package com.queworx.trader.strategies


import groovy.util.logging.Log4j
import com.queworx.Clock
import com.queworx.Calendar
import com.queworx.TradeDirection
import com.queworx.brokers.Order
import com.queworx.brokers.OrderStatus
import com.queworx.brokers.Position
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.trader.Config

@Log4j
abstract class TradeEvaluator
{
	protected static final int LogValidAmount = 1
	protected static final int LogValidTick = 2
	protected static final int LogValidUptick = 3
	protected static final int LogValidShortable = 4

	protected com.queworx.trader.accounting.OrderTracker __orderTracker
	protected com.queworx.trader.accounting.Portfolio __portfolio
	protected Clock __clock
	protected com.queworx.trader.trading.BrokerSet __brokerSet
	protected Config __config
	protected Calendar __calendar
	protected com.queworx.trader.tracking.TickTracker __tickTracker

	private HashMap<Integer, Long> __logInterval = [:]
	private HashMap<String, Long> __logIntervalMessages = [:]

	public TradeEvaluator(com.queworx.trader.tracking.TickTracker tickTracker, com.queworx.trader.accounting.OrderTracker orderTracker, com.queworx.trader.accounting.Portfolio portfolio, com.queworx.trader.trading.BrokerSet brokerSet, Calendar calendar, Clock clock, Config config)
	{
		__orderTracker = orderTracker
		__portfolio = portfolio
		__clock = clock
		__tickTracker = tickTracker
		__brokerSet = brokerSet
		__calendar = calendar
		__config = config
	}

	/**
	 * Checks weather we should consider trading this position based on ratings, ticks, etc.
	 */
	public boolean shouldTrade(Position position, DataSet dataSet, com.queworx.trader.trading.TradePurpose purpose)
	{
		// Already have a live order
		if (__orderTracker.getLiveOrder(position.instrument, position.direction) != null)
		{
			__logDebug("Already have a live order: " + position.instrument.symbol, position.instrument.id, 555, 2000)
			return false
		}

		// If held order, make sure it's tradeable - enough time has passed
		Order heldOrder = __orderTracker.getHeldOrder(position.instrument, position.direction)
		if (heldOrder != null && !__orderTracker.isHeldOrderTradeable(heldOrder, __config.holdDuration))
		{
			__logDebug("Held order: " + position.instrument.symbol, position.instrument.id, 555, 2000)
			return false
		}

		// Space out filled orders
		OrderStatus lastOrderStatus = __orderTracker.getOrderStatuses(position.instrument).max { it.lastUpdate }
		if (lastOrderStatus != null && lastOrderStatus.filled && lastOrderStatus.lastUpdate + __config.getPositionInterval(purpose) > __clock.currentTimeMillis())
		{
			__logDebug("Space out: " + position.instrument.symbol, position.instrument.id, 555, 2000)
			return false
		}

		// Already have all the positions for this instrument - check filled and partially filled
		if(purpose == com.queworx.trader.trading.TradePurpose.Open && __portfolio.getPositions(position.instrument).count { it.filledPercent >= 0.5 && it.direction == position.direction } >= __config.maxPositions)
		{
			__logDebug("All positions: " + position.instrument.symbol, position.instrument.id, 555, 2000)
			return false
		}

		// We already opened all the positions we can open for the day for this instrument
		if(__portfolio.getPositions(position.instrument).count { it.opened.toLocalDate() == __calendar.today() && it.filledPercent >= 0.5 } >= __config.maxDayPositions)
			return false

		// Check uptick
		if(!__validUptick(position))
			return false

		// Check tick properties
		if(!__validTick(position, dataSet, purpose))
			return false

		// Check that we can actually short this instrument
		if(purpose == com.queworx.trader.trading.TradePurpose.Open && position.direction == TradeDirection.Short && !__brokerSet.isShortable(position.instrument))
		{
			__logDebug("Instrument is not shortable: " + position.instrument.symbol, position.instrument.id, LogValidShortable, 2000)
			return false
		}

		return true
	}

	/**
	 * Checks weather we should consider trading this position based on the amount - filled amounts, position amounts, etc
	 */
	public boolean shouldTradeAmount(Position position, BigDecimal filledAmount, BigDecimal liveAmount, com.queworx.trader.trading.TradePurpose purpose, Order heldOrder)
	{
		// The amount remaining on this thread - full amount for new position, and reduced amount for already partially filled position
		BigDecimal remainingAmount = position.amount
		OrderStatus orderStatus = heldOrder == null ? null : __orderTracker.getOrderStatus(heldOrder)
		if(orderStatus != null)
			remainingAmount -= orderStatus.filledQuantity * orderStatus.filledAvgPrice

		boolean partiallyFilled = orderStatus != null && orderStatus.filledQuantity > 0

		// New/full position, filled budget check
		if(purpose == com.queworx.trader.trading.TradePurpose.Open && !partiallyFilled && __config.filledBudget - filledAmount < __config.tradeAmount * 0.5)
		{
			__logDebug("Can't open new position - filled budget: " + position.instrument.symbol + " " + filledAmount, position.instrument.id, 555, 2000)
			return false
		}

		// Partially filled position, filled budget check
		if(purpose == com.queworx.trader.trading.TradePurpose.Open && partiallyFilled && filledAmount  > __config.filledBudget * 1.1)
		{
			__logDebug("Can't resubmit position - filled budget: " + position.instrument.symbol + " " + filledAmount, position.instrument.id, 555, 2000)
			return false
		}

		// Checks that we don't violate the live order amount - live + held + filled
		if(purpose == com.queworx.trader.trading.TradePurpose.Open && liveAmount > __config.liveBudget)
		{
			__logDebug("Live budget: " + position.instrument.symbol + " " + liveAmount + " " + __config.liveBudget, position.instrument.id, 555, 2000)
			return false
		}

		// Make sure we don't violate any global account limits
		if(!__validAccountAmounts(position, remainingAmount))
			return false

		return true
	}

	protected boolean __validAccountAmounts(Position position, BigDecimal orderAmount)
	{
		BigDecimal portfolioPositionAmount = __brokerSet.getPositions(position.instrument).sum { it.amount * (it.direction.isLong() ? 1 : -1) } ?: 0
		if(position.direction == TradeDirection.Long && orderAmount + portfolioPositionAmount > __config.maxLongPosition * 1.05)
		{
			__logDebug("Can't trade because long max position reached: " + position.instrument.symbol, position.instrument.id, LogValidAmount, 3000)
			return false
		}
		else if(position.direction == TradeDirection.Short && -orderAmount + portfolioPositionAmount < -__config.maxShortPosition * 1.05)
		{
			__logDebug("Can't trade because short max position reached: " + position.instrument.symbol, position.instrument.id, LogValidAmount, 3000)
			return false
		}
		else if(position.direction == TradeDirection.Short && __isRiskyShort(position.instrument) && -orderAmount + portfolioPositionAmount < -__config.maxRiskyShortPosition * 1.05)
		{
			__logDebug("Can't trade because max short risky position reached: " + position.instrument.symbol, position.instrument.id, LogValidAmount, 3000)
			return false
		}

		return true
	}

	protected boolean __validUptick(Position position)
	{
		if(!__config.uptick || position.direction != TradeDirection.Short)
			return true

		Tick tick = __tickTracker.getLatestTick(position.instrument)
		Tick prevTick = __tickTracker.getPrevTick(position.instrument)
		if(tick == null || prevTick == null)
			return false

		if(prevTick.lastTradePrice >= tick.lastTradePrice)
		{
			__logDebug("Need uptick: " + position.instrument.symbol, position.instrument.id, LogValidUptick, 2000)
			return false
		}

		return true
	}

	protected boolean __isRiskyShort(Instrument instrument)
	{
		return instrument.isBio()
	}

	protected void __logDebug(String message, int instrumentId, int type, long interval)
	{
		int key = instrumentId * 100 + type
		Long last = __logInterval[key]
		if (last == null || __clock.currentTimeMillis() - last >= interval)
		{
			log.debug(message)
			__logInterval[key] = __clock.currentTimeMillis()
		}
	}

	protected void __logDebug(String message, int type, long interval)
	{
		Long last = __logInterval[type]
		if (last == null || __clock.currentTimeMillis() - last >= interval)
		{
			log.debug(message)
			__logInterval[type] = __clock.currentTimeMillis()
		}
	}

	protected abstract boolean __validTick(Position position, DataSet dataSet, com.queworx.trader.trading.TradePurpose purpose)
}

package com.queworx.trader.strategies.five_percent_drop

import com.queworx.Clock
import com.queworx.Tick
import com.queworx.brokers.Position
import com.queworx.trader.Config
import com.queworx.trader.accounting.OrderTracker
import com.queworx.trader.accounting.Portfolio
import com.queworx.trader.strategies.DataSet
import com.queworx.trader.strategies.Rating
import com.queworx.trader.strategies.StrategyClock
import com.queworx.trader.strategies.TradeEvaluator
import com.queworx.trader.tracking.TickTracker
import com.queworx.trader.trading.BrokerSet
import com.queworx.trader.trading.TradePurpose
import org.joda.time.DateTime

/**
 * Evaluates if we should make the trade or not
 */
class FivePercentDropTradeEvaluator extends TradeEvaluator
{
	private static final int LogValidSpread = 51

	private FivePercentDropRater __rater
	private StrategyClock __strategyClock

	public FivePercentDropTradeEvaluator(FivePercentDropRater rater, TickTracker tickTracker, OrderTracker orderTracker, Portfolio portfolio, BrokerSet brokerSet, StrategyClock strategyClock, Calendar calendar, Clock clock, Config config)
	{
		super(tickTracker, orderTracker, portfolio, brokerSet, calendar, clock, config)
		__rater = rater
		__strategyClock = strategyClock
	}

	/**
	 * Evaluate whether the latest tick is something we can trade on, spread is not too big, we have all the info, price
	 * is not too small, etc.
	 */
	protected boolean __validTick(Position position, DataSet dataSet, TradePurpose purpose)
	{
		Tick tick = __tickTracker.getLatestTick(position.instrument)
		if (tick == null || tick.bid == null || tick.ask == null || tick.bidSize == null || tick.askSize == null)
		{
			__logDebug("Don't have a valid tick yet: " + position.instrument.symbol, position.instrument.id, LogValidTick, 2000)
			return false
		}

		if(tick.avgPrice < __config.minPrice)
		{
			__logDebug("Can't trade because price is too low: " + position.instrument.symbol, position.instrument.id, LogValidTick, 2000)
			return false
		}

		Rating rating = purpose == TradePurpose.Open ? __rater.rate(position.instrument, dataSet) : null
		if(rating != null && !rating.shouldTrade)
		{
			__logDebug("Rating is saying not to trade: " + position.instrument.symbol + " " + rating.ex, position.instrument.id, LogValidTick, 2000)
			return false
		}

		// We are evaluating if the tick is valid for closing
		if(purpose == TradePurpose.Close)
		{
			BigDecimal minSpread = new BigDecimal("0.03") // We are ok with a $0.03 spread

			// We are coming up on closing, so we are ok with a bigger spread to close
			if (__strategyClock.getMinutesFromStart() >= 380)
				minSpread = new BigDecimal("0.06")

			BigDecimal spread = tick.spread/tick.bid
			if(spread < minSpread)
			{
//				__logDebug("Spread is too low: " + position.instrument.symbol + " " + spread, position.instrument.id, LogValidSpread, 2000)
				return false
			}
		}

		return true
	}
}

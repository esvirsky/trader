package com.queworx.trader.strategies


import groovy.util.logging.Log4j
import com.queworx.brokers.Order
import com.queworx.brokers.Position
import com.queworx.trader.trading.TradePurpose

@Log4j
class Controller
{
	private Strategy __strategy
	private ControllerArg __arg
	private DataSet __dataSet

	public Controller(Strategy strategy, ControllerArg arg)
	{
		__strategy = strategy
		__arg = arg
	}

	public void run()
	{
		log.info("Starting Controller")

		__strategy.onPreDataLoad()
		__dataSet = __arg.database.getInitialDataSet()
		__strategy.setDataSet(__dataSet)
		if(__arg.dbPortfolio != null)
			__arg.portfolio.loadDb()

		if(__dataSet.instruments.size() == 0 && __strategy.getPositionsToClose().size() == 0)
		{
			log.warn("There are no instruments and positions to trade, closing")
			return
		}

		log.debug("Initial DataSet Info: " + __dataSet.getInstruments().size() + " instruments")
		log.debug("Instruments: " + __dataSet.instruments*.symbol.sort().join(","))

		__strategy.onPreOpen()
		__arg.strategyClock.waitForOpen()
		__strategy.onPostOpen()

		while(!__arg.strategyClock.timeForTradingEnd())
		{
			long loopStartTime = __arg.clock.currentTimeMillis()

			__strategy.onLoopStart()
			__arg.trackerAdjuster.adjustTrackers(__dataSet)

			if(!__arg.strategyClock.timeForTradingStart())
			{
				__arg.sleeper.sleep(1000, __arg.clock.currentTimeMillis() - loopStartTime)
				continue
			}

			// Cancel unfilled to open positions (if over budget)
			if((__arg.portfolio.getPositions().sum { it.amount } ?: 0) + __arg.orderTracker.getActiveFilledAmount() > __arg.config.filledBudget)
				__arg.orderAdjuster.cancelUnfilledOpenOrders()

			// Cancel expired orders and scale back live to held orders
			__arg.orderAdjuster.cancelExpiredOrders(__arg.config.maxHoldDuration)
			__arg.orderAdjuster.cancelExpiredEmptyOrders(__arg.config.maxHoldEmptyDuration)
			__arg.orderAdjuster.scaleBackOrders(__arg.config.orderSleepDuration)

			// Run trading loop
			__runTradingLoop(loopStartTime)

			// Check to end trading
			if(__strategy.shouldEndTrading())
				break
		}

		log.debug("Ending trading")
		log.debug("Closing open orders")
		__arg.orderTracker.allOrderStatuses.each{ if(it.value.active) __arg.trader.cancelOrder(it.key); __arg.sleeper.sleep(__arg.config.orderSleepDuration) }

		__strategy.onEndTrading()
		__arg.sleeper.sleep(15000) // Let all the order cancellations register
	}

	/**
	 * Trading loop - runs for the rest of the 1 second continuously looking at anything we can trade
	 */
	protected void __runTradingLoop(long loopStartTime)
	{
		__strategy.onTradingLoopStart()
//		log.debug("Time passed pre trading loop start: " + (__arg.clock.currentTimeMillis() - loopStartTime))
		int preLoopPassed = __arg.clock.currentTimeMillis() - loopStartTime
		if(__arg.clock.currentTimeMillis() - loopStartTime > 500)
			log.warn("Less than 500ms left in trading loop. passed: " + (__arg.clock.currentTimeMillis() - loopStartTime))

		HashSet<Position> submitted = new HashSet<Position>()
		List<Position> openPositions = __strategy.getPositionsToOpen()
		List<Position> closePositions = __strategy.getPositionsToClose().collect { __flipPosition(it) }

//		log.debug("Trading positions - open: " + openPositions.size() + "  close: " + closePositions.size())
		int counter = 0
		long tradingLoopStartTime = __arg.clock.currentTimeMillis()
		while(__arg.clock.currentTimeMillis() - loopStartTime < 1000 || counter == 0)
		{
			com.queworx.trader.accounting.Accountant filledAccountant = new com.queworx.trader.accounting.Accountant((__arg.portfolio.getPositions().sum { it.amount } ?: 0) + __arg.orderTracker.getActiveFilledAmount())
			com.queworx.trader.accounting.Accountant liveAccountant = new com.queworx.trader.accounting.Accountant(filledAccountant.balance + __arg.orderTracker.getLiveAmount() + __arg.orderTracker.getHeldAmount())

			__tradePositions(closePositions, submitted, filledAccountant, liveAccountant, tradingLoopStartTime, TradePurpose.Close)
			__tradePositions(openPositions, submitted, filledAccountant, liveAccountant, tradingLoopStartTime, TradePurpose.Open)

			__arg.sleeper.sleep(10)
//			__arg.sleeper.sleep(1000, __arg.clock.currentTimeMillis() - loopStartTime)
			counter++
		}

		log.debug("Loop Info - passed: " + preLoopPassed + "  counter: " + counter + "  open: " + openPositions.size() + "  close: " + closePositions.size())
	}

	private void __tradePositions(List<Position> positions, HashSet<Position> submitted, com.queworx.trader.accounting.Accountant filledAccountant, com.queworx.trader.accounting.Accountant liveAccountant, long tradingLoopStartTime, TradePurpose purpose)
	{
		for(Position position in positions)
		{
			// Shouldn't spend more than one second in the trading loop
			if(System.currentTimeMillis() - tradingLoopStartTime >= 1000)
				break

			// already traded
			if(submitted.contains(position))
				continue

			// should trade
			if(!__arg.tradeEvaluator.shouldTrade(position, __dataSet, purpose))
				continue

			// See if there is already a held order for this instrument
			Order heldOrder = __arg.orderTracker.getHeldOrder(position.instrument, position.direction)

			// can we trade the order amount
			if(!__arg.tradeEvaluator.shouldTradeAmount(position, filledAccountant.balance, liveAccountant.balance, purpose, heldOrder))
				continue

			// Submit the order
			if(heldOrder != null)
			{
				log.debug("Re-submitting order: " + position.instrument.symbol + " " + position.direction.toString())
				__arg.trader.submitOrder(__strategy.adjustOrderForResubmit(heldOrder, purpose), __arg.config.orderSleepDuration)
			}
			else
			{
				log.debug("Submitting new order: " + position.instrument.symbol + " " + position.direction.toString())
				__arg.trader.submitOrder(__strategy.createOrderForSubmit(position, purpose), __arg.config.orderSleepDuration, position.broker)
			}

			// add to submitted and adjust live accountant
			submitted.add(position)
			liveAccountant.credit(purpose == TradePurpose.Open ? position.amount : -position.amount)

			// Get fresh ratings when order sleep duration is long
			if (purpose == TradePurpose.Open && __arg.config.orderSleepDuration > 0.3)
				break
		}
	}

	private Position __flipPosition(Position position)
	{
		Position ret = position.clone()
		ret.direction = position.reverseDirection
		return ret
	}
}

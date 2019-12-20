package com.queworx.trader.trading


import groovy.util.logging.Log4j
import com.queworx.Clock
import com.queworx.Sleeper
import com.queworx.brokers.Order
import com.queworx.brokers.OrderStatus
import com.queworx.brokers.Position

@Log4j
class OrderAdjuster
{
	private Trader __trader
	private com.queworx.trader.accounting.OrderTracker __orderTracker
	private com.queworx.trader.accounting.Portfolio __portfolio
	private Clock __clock
	private Sleeper __sleeper

	public OrderAdjuster(Trader trader, com.queworx.trader.accounting.OrderTracker orderTracker, com.queworx.trader.accounting.Portfolio portfolio, Clock clock, Sleeper sleeper)
	{
		__trader = trader
		__orderTracker = orderTracker
		__portfolio = portfolio
		__clock = clock
		__sleeper = sleeper
	}

	/**
	 * Scales back orders that couldn't fill
	 *
	 * @param scaleBackSleepDuration - amount of time to sleep after a scale back order - need to sleep for safety
	 */
	public void scaleBackOrders(int scaleBackSleepDuration)
	{
		// Live orders that have been around for more than x millis have to be moved to filled or held
		for (Order order in __orderTracker.getExpiredLiveOrders())
		{
			log.debug("Moving order to hold: " + order.instrument.symbol)
			__trader.scaleBack(order)
			__orderTracker.moveToHeldOrder(order)
			__sleeper.sleep(scaleBackSleepDuration)
		}
	}

	/**
	 * Cancel orders that have been held for way too long
	 */
	public void cancelExpiredOrders(int maxHoldDuration)
	{
		for (Order order in __orderTracker.getExpiredHeldOrders(maxHoldDuration))
			__trader.cancelOrder(order)
	}

	/**
	 * Cancel orders empty orders that have been held for way too long
	 */
	public void cancelExpiredEmptyOrders(int maxEmptyHoldDuration)
	{
		for(Order order in __orderTracker.getExpiredHeldOrders(maxEmptyHoldDuration))
		{
			OrderStatus status = __orderTracker.getOrderStatus(order)
			if(status != null && status.filledQuantity == 0)
				__trader.cancelOrder(order)
		}
	}

	public void cancelUnfilledOpenOrders()
	{
		for(Order order in __orderTracker.getActiveUnfilledOrders())
		{
			// Check to make sure it's not a close order
			Position openPosition = __portfolio.getPositions(order.instrument).find{ it.direction != order.direction && it.quantity == order.quantity}
			if(openPosition == null)
				__trader.cancelOrder(order)
		}
	}
}

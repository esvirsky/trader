package com.queworx.trader.trading


import groovy.util.logging.Log4j
import com.queworx.Sleeper
import com.queworx.TradeDirection
import com.queworx.brokers.Broker
import com.queworx.brokers.Order
import com.queworx.brokers.OrderStatus

/**
 * Instead of going to the broker directly, strategies have to use the Trader
 * to deal with orders. The reason is that the Trader offers added protection against faulty logic,
 * like the safety guard.
 */
@Log4j
class Trader
{
	private BrokerSet __brokerSet
	private com.queworx.trader.accounting.OrderTracker __orderTracker
	private OrderSafetyGuard __safetyGuard
	private Sleeper __sleeper

	public Trader(BrokerSet brokerSet, com.queworx.trader.accounting.OrderTracker orderTracker, OrderSafetyGuard safetyGuard, Sleeper sleeper)
	{
		__brokerSet = brokerSet
		__orderTracker = orderTracker
		__safetyGuard = safetyGuard
		__sleeper = sleeper
	}

	public void submitOrder(Order order, Integer sleepAfterTrade = null, Broker broker = null)
	{
		try
        {
			__safetyGuard.verifyOrder(order)
		}
		catch(UnsafeOrderException ex)
        {
            log.warn("Unsafe Order Submit: " + order + " msg: " + ex.message)
            return
        }

		log.debug("Submitting order: " + order)
		OrderStatus status = __orderTracker.getOrderStatus(order)
		if (status == null || status.active == false)
		{
			if(broker == null)
				broker = __brokerSet.getBestBroker(order)

			if(broker == null || !broker.canTrade(order))
			{
				log.debug("Couldn't find a matching broker for order (not shortable, commission too high?): " + order + ", " + broker)
				return
			}

			broker.createOrder(order)
		}
		else
		{
			broker = __orderTracker.getOrderBroker(order)
			broker.resubmitOrder(order)
		}

		__orderTracker.addLiveOrder(order, broker)
		__safetyGuard.notifyOrder(order)

		if(sleepAfterTrade != null)
			__sleeper.sleep(sleepAfterTrade)
	}

	/**
	 * Scales back the order by 5%
	 */
	public void scaleBack(Order order)
	{
		try
        {
			__safetyGuard.verifyOrder(order)
		}
		catch(UnsafeOrderException ex)
        {
            log.warn("Unsafe Order Scaleback: " + order + " msg: " + ex.message)
            return
        }

		order.price = (order.price*(order.direction == TradeDirection.Long ? 0.95 : 1.05)).setScale(2, BigDecimal.ROUND_HALF_DOWN)
		log.debug("Scaling back order: " + order)
		__orderTracker.getOrderBroker(order).resubmitOrder(order)

		__safetyGuard.notifyOrder(order)
	}

	/**
	 * Cancels an order
	 */
	public void cancelOrder(Order order)
	{
		log.debug("Cancelling order: " + order.instrument.symbol)
		__orderTracker.getOrderBroker(order).cancelOrder(order)
		__orderTracker.addCancelledOrder(order)
	}
}

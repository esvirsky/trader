package com.queworx.trader.trading


import groovy.util.logging.Log4j
import com.queworx.Clock
import com.queworx.TradeDirection
import com.queworx.brokers.Order
import com.queworx.brokers.OrderStatus
import com.queworx.Tick
import com.queworx.trader.Config

/**
 * This class is a safety guard against my code, so that I don't have a Capital Knights day
 *
 * Called before orders to make 100% sure that we are safe in making the trade. This
 * is an added layer of protection on top of the regular logic, so that I don't make
 * 1000 orders in a minute or make a ridiculously priced order, both of which could
 * bankrupt me.
 *
 * This class should be kept simple, not precise. That means that it shouldn't just duplicate logic, but
 * instead should guarantee that I don't make disastrous trades. For example, I can check that an order
 * amount is within 10% of the max trade amount. Even though there is a mistake elsewhere, an extra 10% is
 * not disastrous. But buying 100 times as much could be.
 */
@Log4j
class OrderSafetyGuard
{
	private com.queworx.trader.tracking.TickTracker __tickTracker
	private com.queworx.trader.accounting.OrderTracker __orderTracker
	private Clock __clock
	private Config __config

	private int __orderCounter = 0
	private Long __lastOrderTime = null

	public OrderSafetyGuard(com.queworx.trader.tracking.TickTracker tickTracker, com.queworx.trader.accounting.OrderTracker orderTracker, Clock clock, Config config)
	{
		__tickTracker = tickTracker
		__orderTracker = orderTracker
		__clock = clock
		__config = config
	}

	/**
	 * Has to be called before an order, to make sure that the order is safe to make
	 *
	 * If the order is not safe throws an exception
	 */
	public void verifyOrder(Order order)
	{
		Tick tick = __tickTracker.getLatestTick(order.instrument)

		// Basic tick checks
		if (tick.bidSize == 0 || tick.askSize == 0 || tick.ask == null || tick.bid == null)	throw new UnsafeOrderException("Tick bid or ask are empty")
		if (tick.ask < 0.5 || tick.bid < 0.5) throw new UnsafeOrderException("Tick bid or ask have an invalid price")

		// Order Check
		// 1. Minimum price check
		// 2. Min quantity less than or equal to total quantity
		// 3. Order price to bid/ask spread check (can't be too different)
		// 4. Order Amount check
		if (order.price < __config.safety_min_price) throw new UnsafeOrderException("Price is too low")
		if (order.price > __config.safety_max_price) throw new UnsafeOrderException("Price is too high")
		if (order.minQuantity > order.quantity) throw new UnsafeOrderException("Minimum quantity is too high")

		if (order.direction == TradeDirection.Long && (order.price - tick.bid)/tick.bid > __config.safety_max_spread) throw new UnsafeOrderException("Trying to buy above safety spread: " + tick)
		if (order.direction == TradeDirection.Short && -(order.price - tick.ask)/tick.ask > __config.safety_max_spread) throw new UnsafeOrderException("Trying to sell above safety spread: " + tick)

		if (order.price * order.quantity < __config.safety_min_trade_amount) throw new UnsafeOrderException("Order trade amount is too low")
		if (order.price * order.quantity > __config.safety_max_trade_amount) throw new UnsafeOrderException("Order trade amount is too high")

		// Budget check
		// 1. Total of all orders has to be within some amount
		Map<Order, OrderStatus> statuses = __orderTracker.getAllOrderStatuses()
		List<OrderStatus> otherStatuses = statuses.findAll{it.key != order}.values().toList()
		if (otherStatuses.sum(0) {it.filledQuantity*it.filledAvgPrice} + order.price*order.quantity > __config.safety_max_filled_budget) throw new UnsafeOrderException("We are over the maximum filled budget: filled=" + otherStatuses.sum{it.filledQuantity*it.filledAvgPrice} + " order=" + order.price*order.quantity)
		if (otherStatuses.sum(0) {(it.filledQuantity + it.remainingQuantity)*it.filledAvgPrice} + order.price*order.quantity > __config.safety_max_total_budget) throw new UnsafeOrderException("We are over the maximum total budget: filled+remaining=" + otherStatuses.sum{(it.filledQuantity + it.remainingQuantity)*it.filledAvgPrice} + " order=" + order.price*order.quantity)

		// Order entry limits
		// 1. Order attempt count check
		// 2. Total order count check (filled + active)
		// 3. Time interval check
		if (__orderCounter >= __config.safety_max_order_attempts) throw new UnsafeOrderException("Reached the order attempt counter limit: " + __orderCounter)
		if (statuses.size() >= __config.safety_max_order_count) throw new UnsafeOrderException("Reached the total order count limit: " + statuses.size())
		if (__lastOrderTime != null && __clock.currentTimeMillis() - __lastOrderTime < __config.safety_min_time_interval*1000 && !__config.testing) throw new UnsafeOrderException("Attempting a new order too soon: " + __lastOrderTime - __clock.currentTimeMillis() + " " + __lastOrderTime)
	}

	/**
	 * Has to be called right after an order, so that the safety guard gets the new info (order count, time of lastTradePrice order, etc.)
	 */
	public void notifyOrder(Order order)
	{
		__orderCounter += 1
		__lastOrderTime = __clock.currentTimeMillis()
	}
}

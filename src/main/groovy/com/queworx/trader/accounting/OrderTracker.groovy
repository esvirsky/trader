package com.queworx.trader.accounting

import groovy.util.logging.Log4j
import com.queworx.Clock
import com.queworx.TradeDirection
import com.queworx.brokers.Broker
import com.queworx.brokers.Order
import com.queworx.brokers.OrderListener
import com.queworx.brokers.OrderStatus
import com.queworx.Instrument

import java.util.concurrent.ConcurrentHashMap

@Log4j
class OrderTracker implements OrderListener
{
	private Clock __clock

	private ConcurrentHashMap<Order, OrderStatus> __orderStatuses = [:]
	private ConcurrentHashMap<Order, Long> __live = [:]
	private ConcurrentHashMap<Order, Long> __held = [:]
	private Map<Order, Broker> __orderBrokers = [:]
	private Object __lock = new Object()

	public OrderTracker(Clock clock)
	{
		__clock = clock
	}

	public OrderStatus getOrderStatus(Order order)
	{
		return __orderStatuses.get(order)
	}

	public Map<Order, OrderStatus> getAllOrderStatuses()
	{
		return __orderStatuses
	}

	public List<OrderStatus> getOrderStatuses(Instrument instrument)
	{
		return __orderStatuses.findAll{key, value -> key.instrument == instrument}.values().toList()
	}

	public Order getLiveOrder(Instrument instrument, TradeDirection direction)
	{
		return __live.keySet().find { it.instrument == instrument && it.direction == direction }
	}

	public Order getHeldOrder(Instrument instrument, TradeDirection direction)
	{
		return __held.keySet().find { it.instrument == instrument && it.direction == direction }
	}

	public List<Order> getHeldOrders()
	{
		return __held.keySet().toList()
	}

	public List<Order> getActiveUnfilledOrders()
	{
		return (__live.keySet() + __held.keySet()).findAll { __orderStatuses[it] != null && __orderStatuses[it].filledQuantity == 0}.toList()
	}

	public boolean isHeldOrderTradeable(Order order, int holdDuration)
	{
		return __held[order] + holdDuration < __clock.currentTimeMillis()
	}

	/**
	 * @param liveDuration - amount of time in millis order can stay live
	 */
	public List<Order> getExpiredLiveOrders()
	{
		return __live.findAll {it.value + it.key.liveDuration < __clock.currentTimeMillis()}.keySet().toList()
	}

	public List<Order> getExpiredHeldOrders(int maxHoldDuration)
	{
		return __held.findAll { it.value + maxHoldDuration < __clock.currentTimeMillis()}.keySet().toList()
	}

	public BigDecimal getLiveAmount()
	{
		return __live.keySet().sum{it.price*it.quantity} ?: new BigDecimal(0)
	}

	public BigDecimal getHeldAmount()
	{
		return __held.keySet().sum{it.price*it.quantity} ?: new BigDecimal(0)
	}

	public BigDecimal getActiveFilledAmount()
	{
		BigDecimal amount =  __live.keySet().collect { __orderStatuses.get(it) }.findAll { it != null }.sum { it.filledQuantity * it.filledAvgPrice } ?: 0
		amount += __held.keySet().collect { __orderStatuses.get(it) }.findAll { it != null }.sum { it.filledQuantity * it.filledAvgPrice } ?: 0
		return amount
	}

	public Broker getOrderBroker(Order order)
	{
		return __orderBrokers[order]
	}

	/**
	 * Has to be done manually - because there is a delay between when the order goes out and when order tracker sees it,
	 * and in that delay we can attempt to put in another live order
	 */
	public void addLiveOrder(Order order, Broker broker)
	{
		__held.remove(order)
		__live[order] = __clock.currentTimeMillis()
		__orderBrokers[order] = broker
	}

	/**
	 * Has to be done manually - because there is a delay between when the order goes out and when order tracker sees it,
	 * and in that delay we can attempt to cancel again
	 */
	public void addCancelledOrder(Order order)
	{
		__held.remove(order)
		__live.remove(order)
	}

	public void moveToHeldOrder(Order order)
	{
		synchronized (__lock)
		{
			if(!__live.containsKey(order))
				return

			__live.remove(order)
			__held.put(order, __clock.currentTimeMillis())
		}

	}

	@Override
	void orderUpdateNotification(Order order, OrderStatus status, Broker broker)
	{
		synchronized (__lock)
		{
			__orderStatuses.put(order, status)
			if(status.filled || !status.active)
			{
				__live.remove(order)
				__held.remove(order)
			}
		}
	}
}

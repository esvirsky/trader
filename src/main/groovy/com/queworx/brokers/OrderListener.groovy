package com.queworx.brokers

public interface OrderListener
{
	/**
	 * Called when new information about an order is available
	 *
	 * @param order
	 * @param status
	 */
	public void orderUpdateNotification(Order order, OrderStatus status, Broker broker)
}
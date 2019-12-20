package com.queworx.brokers

import groovy.util.logging.Log4j
import com.queworx.TradeDirection
import com.queworx.Instrument

@Log4j
abstract class Broker
{
	public boolean canTrade(Order order)
	{
		Position position = getPosition(order.instrument)
		if (order.direction == TradeDirection.Short && !isShortable(order.instrument) && (position == null || position.absoluteQuantity < order.quantity))
		{
			log.debug("Instrument is not shortable on broker: " + this.class.toString() + " " + order.instrument.symbol)
			return false
		}

		if (order.maxCommission != null && getCommission(order) > order.maxCommission)
		{
			log.debug("Commissions are too high for broker: " + this.class.toString() + " " + getCommission(order))
			return false
		}

		if(order.price * order.quantity > availableFunds)
		{
			log.debug("There are not enough available funds on broker: " + this.class.toString() + " " + availableFunds)
			return false
		}

		return true
	}

	public abstract void connect()
	public abstract void disconnect()

	public abstract void createOrder(Order order)
	public abstract void resubmitOrder(Order order)
	public abstract void cancelOrder(Order order)

	public abstract BigDecimal getCommission(Order order)
	public abstract BigDecimal getAvailableFunds()

	public abstract void startPullingShortable(List<Instrument> instruments)
	public abstract void stopPullingShortable(List<Instrument> instruments)
	public abstract void stopPullingShortable()
	public abstract Boolean isShortable(Instrument instrument)

	public abstract Position getPosition(Instrument instrument)
}
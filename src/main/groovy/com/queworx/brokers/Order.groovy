package com.queworx.brokers

import groovy.transform.ToString
import com.queworx.TradeDirection
import com.queworx.Instrument

@ToString(includeFields=true, includeNames=true, includePackage=false)
class Order
{
	Instrument instrument
	TradeDirection direction
	int quantity
	BigDecimal price

	Integer minQuantity

	// Max commission that I'm willing to pay for this order
	BigDecimal maxCommission

	boolean hidden
	OrderStatus lastStatus
	int liveDuration

	public Order(Instrument instrument, int quantity, BigDecimal price, TradeDirection direction, int liveDuration, BigDecimal maxCommissions = null, hidden = false)
	{
		this.instrument = instrument
		this.quantity = quantity
		this.price = price
		this.direction = direction
		this.liveDuration = liveDuration
		this.maxCommission = maxCommission
		this.hidden = hidden
	}
}

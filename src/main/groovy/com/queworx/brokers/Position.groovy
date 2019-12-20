package com.queworx.brokers

import org.joda.time.DateTime
import org.joda.time.LocalDate
import com.queworx.TradeDirection
import com.queworx.Instrument

class Position
{
	Instrument instrument
	BigDecimal avgPrice
	Integer quantity
	TradeDirection direction

	Float filledPercent
	DateTime opened
	Broker broker
	LocalDate extended

	public Position(Instrument instrument, int quantity, BigDecimal avgPrice, TradeDirection direction)
	{
		this.instrument = instrument
		this.quantity = quantity
		this.avgPrice = avgPrice
		this.direction = direction
	}

	public BigDecimal getAmount()
	{
		return avgPrice * quantity
	}

	public int getAbsoluteQuantity()
	{
		return direction == TradeDirection.Short ? -quantity : quantity
	}

	public TradeDirection getReverseDirection()
	{
		return direction == TradeDirection.Long ? TradeDirection.Short : TradeDirection.Long
	}

	public Position clone()
	{
		Position position = new Position(instrument, quantity, avgPrice, direction)
		position.filledPercent = filledPercent
		position.opened = opened
		position.broker = broker
		position.extended = extended
		return position
	}
}

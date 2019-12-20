package com.queworx.brokers

import groovy.transform.ToString
import com.queworx.brokers.Order

@ToString(includeFields=true, includeNames=true)
class OrderStatus
{
	BigDecimal filledAvgPrice
	Integer filledQuantity, remainingQuantity
	boolean active
	Long lastUpdate

	public boolean isFilled()
	{
		return filledQuantity > 0 && remainingQuantity == 0
	}
}

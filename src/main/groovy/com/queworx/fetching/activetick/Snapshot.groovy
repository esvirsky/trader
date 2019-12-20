package com.queworx.fetching.activetick

import groovy.transform.ToString

@ToString(includeFields = true, includeNames = true, includePackage = false)
class Snapshot
{
	public String name
	public BigDecimal bid, ask, last, open, prevClose
	public int bidSize, askSize, ivolume
	public BigDecimal low, high

	// In epoch time from ActiveTick (NY time)
	public Long lastTradeTime

	public BigDecimal getAvgPrice()
	{
		return bid == null || ask == null ? null : (bid + ask)/2
	}
}

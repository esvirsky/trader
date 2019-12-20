package com.queworx

import groovy.transform.ToString

@ToString(includeFields=true, includeNames=true, includePackage=false)
class Tick
{
	int iTime, volume
	BigDecimal bid, ask
	float fbid, fask, flast
	Integer bidSize, askSize

	// When this tick came in, millis since 1970
	Long time

	BigDecimal lastTradePrice
	Integer lastTradeVolume

	// Last trade in millis since 1970
	Long lastTradeTime

	public BigDecimal getAvgPrice()
	{
		return bid == null || ask == null ? null : (bid + ask)/2
	}

	public BigDecimal getSpread()
	{
		return bid == null || ask == null ? null : ask - bid
	}

	public float getSpreadPLong()
	{
		return (ask - bid)/ask
	}

	public float getSpreadPShort()
	{
		return (ask - bid)/bid
	}

	public float getSpreadPAvg()
	{
		return (ask - bid)/avgPrice
	}


	public float getFSpread()
	{
		return fask - fbid
	}

	public Tick copy()
	{
		return new Tick(bid:bid, ask:ask, lastTradePrice:lastTradePrice, bidSize:bidSize, askSize:askSize, lastTradeVolume: lastTradeVolume, lastTradeTime:lastTradeTime, time:time)
	}
}

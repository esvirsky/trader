package com.queworx

import groovy.transform.ToString

@ToString(includeFields=true, includeNames=true)
class Instrument
{
	public enum Type { stock, index, delisted }

	int id // This data came from a database
	String symbol, name
	Integer sic, naics

	Exchange exchange
	Etf etf
	Type type

	public boolean isBio()
	{
		if(sic != null && (((int)sic/10) == 283 || sic == 3826 || sic == 3841))
			return true

		String lower = name?.toLowerCase()
		if(lower != null && (lower.contains("genetic") || lower.contains("therapeutic") || lower.contains("pharmaceutical")))
			return true

		return false
	}
}

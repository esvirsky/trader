package com.queworx

import com.queworx.Instrument

/**
 * Thread safe
 */
class InstrumentDatastore
{
	private HashMap<String, Instrument> __instruments = [:]
	private int __newId = -1;

	public synchronized Instrument getOrCreateInstrument(String symbol)
	{
		// If one doesn't exist make one up for this session
		if(!__instruments.containsKey(symbol))
		{
			__instruments[symbol] = new Instrument(id: __newId, symbol: symbol)
			__newId--
		}

		return __instruments[symbol]
	}

	public synchronized Instrument getInstrument(int id)
	{
		return __instruments.values().find { it.id == id }
	}

	public synchronized List<Instrument> getInstruments()
	{
		return __instruments.values().toList()
	}

	public synchronized void registerInstruments(List<Instrument> instruments)
	{
		instruments.each { __instruments[it.symbol] = it }
	}
}

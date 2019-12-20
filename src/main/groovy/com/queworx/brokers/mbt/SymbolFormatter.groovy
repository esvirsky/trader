package com.queworx.brokers.mbt

import com.queworx.Instrument

/**
 * Converts between standard format and MBT format
 */
class SymbolFormatter
{
	/**
	 * Creates a standard instrument from an MBT symbol and name
	 */
	public Instrument toStandard(String symbol, String name = null)
	{
		return new Instrument(symbol: symbol, name: name, type: Instrument.Type.stock)
	}

	/**
	 * Creates an MBT symbol from a standard instrument
	 */
	public String fromStandard(Instrument instrument)
	{
		return instrument.symbol
	}
}

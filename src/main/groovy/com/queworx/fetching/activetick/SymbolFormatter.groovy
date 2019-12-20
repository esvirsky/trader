package com.queworx.fetching.activetick

import com.queworx.Exchange
import com.queworx.Instrument

/**
 * Converts between standard format and ActiveTick format
 */
class SymbolFormatter
{
	/**
	 * Creates a standard instrument from an ActiveTick symbol and name
	 */
	public Instrument toStandard(String symbol, String name)
	{
		// Shouldn't come in with these characters
		if (symbol.contains(".") || symbol.contains("+") || symbol.contains("*"))
			throw new IllegalArgumentException("ActiveTick shouldn't return these characters in the symbol: " + symbol)

		// Index conversion
		if (symbol.startsWith("INDEX:"))
			return new Instrument(symbol: symbol.replace("INDEX:", "") + ".XO", name: name, type: Instrument.Type.index)

		// Doesn't know specific exchange
		return new Instrument(symbol: symbol.replace("-", "."), name: name, type: Instrument.Type.stock)
	}

	/**
	 * Creates an ActiveTick symbol from a standard instrument
	 */
	public String fromStandard(Instrument instrument)
	{
		// Only US exchanges
		if (!(instrument.exchange in [null, Exchange.nyse, Exchange.amex, Exchange.nasdaq, Exchange.nyse_arca, Exchange.otc]))
			throw new IllegalArgumentException("Couldn't convert Exchange to ActiveTick symbol: " + instrument)

		// No special symbols
		if (instrument.symbol.contains("-") || instrument.symbol.contains("+") || instrument.symbol.contains("*"))
			throw new IllegalArgumentException("Couldn't convert instrument to ActiveTick symbol: " + instrument)

		// Index conversion
		if (instrument.type == Instrument.Type.index)
			return "INDEX:" + instrument.symbol.replace(".XO", "")

		// Format conversion
		return instrument.symbol.replace(".", "-")
	}
}

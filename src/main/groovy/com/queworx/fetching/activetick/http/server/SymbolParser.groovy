package com.queworx.fetching.activetick.http.server

import at.shared.ATServerAPIDefines

class SymbolParser
{
	public String parseSymbol(ATServerAPIDefines.ATSYMBOL atSymbol)
	{
		String symbol = new String(atSymbol.symbol);
		int plainItemSymbolIndex = symbol.indexOf((byte)0);
		return __toClient(symbol.substring(0, plainItemSymbolIndex), atSymbol.symbolType);
	}

	/**
	 * toServer and toClient should eventually be moved to the client
	 */
	public String formatSymbolForServer(String symbol)
	{
		return symbol.replaceFirst(/^INDEX:/, "\\\$").replace('-', '/').replace('.','/')
	}

	/**
	 * toServer and toClient should eventually be moved to the client
	 */
	private String __toClient(String symbol, byte symbolType)
	{
		if(symbolType == ATServerAPIDefines.ATSymbolType.Index)
			symbol = "INDEX:" + symbol

		return symbol.replace('/', '-')
	}

}

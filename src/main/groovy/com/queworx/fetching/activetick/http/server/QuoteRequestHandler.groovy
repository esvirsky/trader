package com.queworx.fetching.activetick.http.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.queworx.ErrorHandler

class QuoteRequestHandler implements HttpHandler
{
	private ActiveTickComm __atComm
	private SymbolParser __symbolParser
	private ErrorHandler __errorHandler

	public QuoteRequestHandler(ActiveTickComm atComm, SymbolParser symbolParser, ErrorHandler errorHandler)
	{
		__atComm = atComm
		__symbolParser = symbolParser
		__errorHandler = errorHandler
	}

	@Override
	public void handle(HttpExchange httpExchange) throws IOException
	{
		try
		{
			List<String> symbols = __getSymbols(httpExchange.requestURI.rawQuery)
			List<Integer> fields = __getFields(httpExchange.requestURI.rawQuery)
			if(symbols == null || fields == null)
				throw new IllegalArgumentException("Error: Need to pass both symbols and fields to request")

			__atComm.requestQuotes(symbols, fields, new QuoteResponseListener(httpExchange))

		}
		catch(Exception ex)
		{
			__errorHandler.handleError("Error in QuoteRequestHandler", ex)
		}
	}

	private List<String> __getSymbols(String query)
	{
		for (String pair in query.split("&"))
		{
			List<String> args = pair.split("=")
			if(args[0] != "symbol")
				continue

			// Eventually should be $symbol both incoming and outgoing for index
			return args[1].split("\\+").collect { __symbolParser.formatSymbolForServer(it) }
		}
	}

	private List<Integer> __getFields(String query)
	{
		for (String pair in query.split("&"))
		{
			List<String> args = pair.split("=")
			if(args[0] != "field")
				continue

			return args[1].split("\\+").collect { Integer.valueOf(it) }
		}
	}
}

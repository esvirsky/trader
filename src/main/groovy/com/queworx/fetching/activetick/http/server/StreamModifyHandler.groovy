package com.queworx.fetching.activetick.http.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.queworx.ErrorHandler

class StreamModifyHandler implements HttpHandler
{
	private ActiveTickComm __atComm
	private SymbolParser __symbolParser
	private ErrorHandler __errorHandler

	public StreamModifyHandler(ActiveTickComm atComm, SymbolParser symbolParser, ErrorHandler errorHandler)
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
			String action = __getAction(httpExchange.requestURI.rawQuery)
			String sessionId = __getSessionId(httpExchange.requestURI.rawQuery)

			if(action == null)
			{
				println "Error: Need to pass an action"
				__writeResponse(httpExchange, "failed");
				return
			}
			else if((action == "subscribe" || action == "unsubscribe") && symbols == null)
			{
				println "Error: Need to pass both symbols and action to request"
				__writeResponse(httpExchange, "failed");
				return
			}

			if(action == "subscribe")
				__atComm.subscribeStream(symbols, sessionId)
			else if(action == "unsubscribe")
				__atComm.unsubscribeStream(symbols, sessionId)
			else if(action == "unsubscribeAll")
				__atComm.unsubscribeAllStream(sessionId)

			__writeResponse(httpExchange, "success");
		}
		catch(Exception ex)
		{
			__errorHandler.handleError("Error in StreamRequestHandler", ex)
		}
	}

	private List<String> __getSymbols(String query)
	{
		for (String pair in query.split("&"))
		{
			List<String> args = pair.split("=")
			if(args[0] != "symbol")
				continue

			return args[1].split("\\+").collect{ __symbolParser.formatSymbolForServer(it) }
		}
	}

	private String __getAction(String query)
	{
		for (String pair in query.split("&"))
		{
			List<String> args = pair.split("=")
			if(args[0] != "action")
				continue

			return args[1]
		}
	}

	private String __getSessionId(String query)
	{
		for (String pair in query.split("&"))
		{
			List<String> args = pair.split("=")
			if(args[0] != "sessionId")
				continue

			return args[1]
		}
	}

	private void __writeResponse(HttpExchange httpExchange, String value)
	{
		httpExchange.getResponseHeaders().set("Content-Type","text/plain")
		httpExchange.sendResponseHeaders(200, 0)
		httpExchange.getResponseBody().write(value.bytes)
		httpExchange.getResponseBody().close()
	}
}

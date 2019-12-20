package com.queworx.fetching.activetick.http.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.queworx.ErrorHandler

class StreamHandler implements HttpHandler
{
	private ActiveTickComm __atComm
	private ActiveTickStreamer __atStreamer
	private ErrorHandler __errorHandler

	public StreamHandler(ActiveTickComm atComm, ActiveTickStreamer atStreamer, ErrorHandler errorHandler)
	{
		__atComm = atComm
		__atStreamer = atStreamer
		__errorHandler = errorHandler
	}

	@Override
	public void handle(HttpExchange httpExchange) throws IOException
	{
		try
		{
			httpExchange.getResponseHeaders().set("Content-Type","text/plain")
			httpExchange.sendResponseHeaders(200, 0)
			httpExchange.getResponseBody().write("connection_start\r\n".bytes)
			httpExchange.getResponseBody().flush()
			__atStreamer.addStreamListener(new StreamListener(httpExchange, __atStreamer))
		}
		catch(Exception ex)
		{
			__errorHandler.handleError("StreamHandler error", ex)
		}
	}
}

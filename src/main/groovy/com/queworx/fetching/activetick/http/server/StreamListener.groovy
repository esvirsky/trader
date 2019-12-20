package com.queworx.fetching.activetick.http.server

import com.sun.net.httpserver.HttpExchange

class StreamListener
{
	private HttpExchange __httpExchange
	private ActiveTickStreamer __atStreamer

	public StreamListener(HttpExchange httpExchange, ActiveTickStreamer atStreamer)
	{
		__httpExchange = httpExchange
		__atStreamer = atStreamer
	}

	public void streamData(String data)
	{
		try
		{
			__httpExchange.getResponseBody().write(data.bytes)
			__httpExchange.getResponseBody().flush()
		}
		catch(IOException ex)
		{
			println "Stream connection closed"
			__atStreamer.removeStreamListener(this)
			try { __httpExchange.getResponseBody().close() } catch(Exception){}
		}
	}

	public void error(String message)
	{
		__httpExchange.sendResponseHeaders(500, 0);
		__httpExchange.getResponseBody().write(message.bytes)
		__httpExchange.getResponseBody().close()
	}
}

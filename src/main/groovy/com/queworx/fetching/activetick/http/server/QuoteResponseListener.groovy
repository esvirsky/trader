package com.queworx.fetching.activetick.http.server

import com.sun.net.httpserver.HttpExchange

class QuoteResponseListener
{
	private HttpExchange __httpExchange

	public QuoteResponseListener(HttpExchange httpExchange)
	{
		__httpExchange = httpExchange
	}

	public void quoteData(String data)
	{
		__httpExchange.getResponseHeaders().set("Content-Type","text/plain")
		__httpExchange.sendResponseHeaders(200, 0)
		__httpExchange.getResponseBody().write(data.bytes)
		__httpExchange.getResponseBody().close()
	}

	public void error(String message)
	{
		__httpExchange.sendResponseHeaders(500, 0);
		__httpExchange.getResponseBody().write(message.bytes)
		__httpExchange.getResponseBody().close()
	}
}
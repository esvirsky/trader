package com.queworx

import groovyx.net.http.AsyncHTTPBuilder
import groovyx.net.http.HTTPBuilder

/**
 * This is needed because HttpBuilder has such an implementation to where once it's shutdown it can't be restarted, it
 * has to be re instantiated. This presents a problem for classes that connect and disconnect to service streams, as
 * they can't just take an HttpBuilder as a constructor argument
 */
class HttpBuilderFactory
{
	public HTTPBuilder createHTTPBuilder()
	{
		return new HTTPBuilder()
	}

	public AsyncHTTPBuilder createAsyncHTTPBuilder()
	{
		return new AsyncHTTPBuilder()
	}
}

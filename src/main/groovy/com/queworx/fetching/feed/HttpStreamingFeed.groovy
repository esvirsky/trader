package com.queworx.fetching.feed

import groovy.util.logging.Log4j
import groovyx.net.http.AsyncHTTPBuilder
import groovyx.net.http.ContentType
import com.queworx.HttpBuilderFactory

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Connects to a streaming http source and keeps feeding listeners that are connected to it
 */
@Log4j
class HttpStreamingFeed
{
	@groovy.beans.ListenerList
	private java.util.List<StreamingFeedListener> __streamingFeedListeners = new CopyOnWriteArrayList<StreamingFeedListener>()

	private HttpBuilderFactory __httpBuilderFactory
	private AsyncHTTPBuilder __streamHttpBuilder

	private boolean __connected
	private boolean __stop
	private Object __lock = new Object()

	public HttpStreamingFeed(HttpBuilderFactory httpBuilderFactory)
	{
		__httpBuilderFactory = httpBuilderFactory
	}

	void connect(String url)
	{
		__stop = false
		__streamHttpBuilder = __httpBuilderFactory.createAsyncHTTPBuilder()
		__streamHttpBuilder.get(uri: url, contentType: ContentType.TEXT, this.&__responseHandler)

		synchronized (__lock) { while(!__connected) { try{ __lock.wait() } catch(InterruptedException ex){} } }
	}

	void disconnect()
	{
		__stop = true
		__streamHttpBuilder.shutdown()
		__streamHttpBuilder.threadExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
		__connected = false
	}

	protected void __responseHandler(def resp, def reader)
	{
		try
		{
			BufferedReader bfReader = new BufferedReader(reader)
			while (!__stop)
			{
				String line = bfReader.readLine()

				if(!__connected)
				{
					__connected = true
					synchronized (__lock) { __lock.notifyAll() }
				}

				if(line != "connection_start")
					__streamingFeedListeners.each { it.streamingFeedData(line) }
			}
		}
		catch(SocketException ex)
		{
			if(!__stop)
				log.error("Exception in HttpStreamingFeed", ex)
		}
		catch(Exception ex)
		{
			log.error("Exception in HttpStreamingFeed", ex)
		}
	}
}

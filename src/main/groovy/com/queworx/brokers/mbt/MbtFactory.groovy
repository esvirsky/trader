package com.queworx.brokers.mbt

import com.queworx.Clock
import com.queworx.ExecutorFactory
import com.queworx.HttpBuilderFactory
import com.queworx.InstrumentDatastore
import com.queworx.fetching.feed.HttpStreamingFeed

class MbtFactory
{
	public MbtBroker createMbtBroker(InstrumentDatastore instrumentDatastore, String serverUrl)
	{
		HttpBuilderFactory httpBuilderFactory = new HttpBuilderFactory()
		ExecutorFactory executorFactory = new ExecutorFactory()
		Clock clock = new Clock()

		HttpStreamingFeed httpStreamingFeed = new HttpStreamingFeed(httpBuilderFactory)

		SymbolFormatter symbolFormatter = new SymbolFormatter()
		MbtDatastore datastore = new MbtDatastore()

		MbtShortableTask shortableTask = new MbtShortableTask(datastore, httpBuilderFactory, symbolFormatter, serverUrl)
		MbtBroker broker = new MbtBroker(datastore, shortableTask, httpBuilderFactory, executorFactory, httpStreamingFeed, symbolFormatter, serverUrl)

		MbtReceiver receiver = new MbtReceiver(broker, datastore, instrumentDatastore, symbolFormatter, clock)

		// Wire up
		httpStreamingFeed.addStreamingFeedListener(receiver)

		return broker
	}
}

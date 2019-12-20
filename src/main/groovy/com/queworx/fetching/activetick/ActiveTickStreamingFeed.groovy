package com.queworx.fetching.activetick

import groovy.util.logging.Log4j
import groovyx.net.http.HTTPBuilder
import org.apache.commons.lang3.RandomStringUtils
import com.queworx.HttpBuilderFactory
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.fetching.feed.Feed
import com.queworx.fetching.feed.FeedQueue
import com.queworx.fetching.feed.HttpStreamingFeed
import com.queworx.fetching.feed.StreamingFeedListener
import com.queworx.fetching.feed.TickListener

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Log4j
class ActiveTickStreamingFeed implements Feed, StreamingFeedListener
{
	@groovy.beans.ListenerList
	private java.util.List<TickListener> __tickListeners = new CopyOnWriteArrayList<TickListener>()

	private HttpBuilderFactory __httpBuilderFactory
	private HttpStreamingFeed __httpStreamingFeed
	private String __url
	private SymbolFormatter __symbolFormatter
	int __maxLatency

	private HTTPBuilder __requestHttpBuilder
	private FeedQueue __feedQueue
	private ConcurrentHashMap<String, Instrument> __instrumentSymbols = [:]
	private ConcurrentHashMap<Integer, Tick> __ticks = [:]

	private String __sessionId = RandomStringUtils.random(5, true, true)

	/**
	 * Constructor
	 *
	 * @param capacity  If set, then this is the max number of instruments that this feed can get,
	 * if we add more instruments, than older instruments get removed in fifo order
	 */
	public ActiveTickStreamingFeed(HttpBuilderFactory httpBuilderFactory, HttpStreamingFeed httpStreamingFeed, String url, SymbolFormatter symbolFormatter, int maxLatency = 200)
	{
		__httpBuilderFactory = httpBuilderFactory
		__httpStreamingFeed = httpStreamingFeed
		__url = url
		__symbolFormatter = symbolFormatter
		__maxLatency = maxLatency
	}

	@Override
	void connect()
	{
		__httpStreamingFeed.connect(__url + "/stream")
		__feedQueue = new FeedQueue(1000)
	}

	@Override
	void disconnect()
	{
		__httpStreamingFeed.disconnect()
	}

	@Override
	public void startPulling(List<Instrument> instruments)
	{
		List<Instrument> remove = __feedQueue.add(instruments)
		if(remove.size() > 0)
			stopPulling(remove)

		instruments.each {	__instrumentSymbols[__symbolFormatter.fromStandard(it)] = it }
		String url = sprintf("%s/modifyStream?symbol=%s&action=subscribe&sessionId=%s", __url, instruments.collect { __symbolFormatter.fromStandard(it) }.join("+"), __sessionId)
		__getRequestHttpBuilder().get(uri: url)
	}

	@Override
	public void stopPulling(List<Instrument> instruments)
	{
		__feedQueue.remove(instruments)
		instruments.each {	__ticks.remove(it.id) }
		String url = sprintf("%s/modifyStream?symbol=%s&action=unsubscribe&sessionId=%s", __url, instruments.collect { it.symbol }.join("+"), __sessionId)
		__getRequestHttpBuilder().get(uri: url)
	}

	public void clearTickCache()
	{
		__ticks.clear()
	}

	@Override
	void stopPulling()
	{
		if(__feedQueue.size() > 0)
			stopPulling(__feedQueue.collect { it })
	}

	@Override
	List<Instrument> getPulledInstruments()
	{
		return __feedQueue
	}

	@Override
	void streamingFeedData(String data)
	{
		if(data == null)
			return

		String[] parts = data.split(",")
		if(parts[0] != "q" && parts[0] != "t")
			return

		Instrument instrument = __instrumentSymbols[parts[1]]
		if (instrument == null)
			return

		Tick tick = __ticks[instrument.id]
		if(tick == null)
			tick = __ticks[instrument.id] = new Tick()

		if (parts[0] == "q")
		{
			tick.bidSize = Integer.valueOf(parts[2]) * 100
			tick.bid = new BigDecimal(parts[3])
			tick.ask = new BigDecimal(parts[4])
			tick.askSize = Integer.valueOf(parts[5]) * 100
			tick.time = Long.valueOf(parts[6]) - 7200000 // adj to local time

			if(System.currentTimeMillis() - tick.time > __maxLatency)
				log.warn("!!!WARNING!!! Max latency violation (ActiveTickStreaming - q): drift: " + (System.currentTimeMillis() - tick.time) + " tick: " + tick)
		}
		else if (parts[0] == "t")
		{
			tick.lastTradePrice = new BigDecimal(parts[2])
			tick.lastTradeVolume = Integer.valueOf(parts[3])
			tick.lastTradeTime = Long.valueOf(parts[4]) - 7200000 // adj to local time

			if(System.currentTimeMillis() - tick.lastTradeTime > __maxLatency)
				log.warn("!!!WARNING!!! Max latency violation (ActiveTickStreaming - q): drift: " + (System.currentTimeMillis() - tick.lastTradeTime) + " tick: " + tick)
		}

		__tickListeners.each { it.tickUpdateNotification(instrument, tick.copy()) }
	}

	private HTTPBuilder __getRequestHttpBuilder()
	{
		if(__requestHttpBuilder == null)
			__requestHttpBuilder = __httpBuilderFactory.createHTTPBuilder()

		return __requestHttpBuilder
	}
}

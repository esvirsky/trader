package com.queworx.brokers.mbt

import groovy.util.logging.Log4j
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import org.apache.commons.lang3.RandomStringUtils
import com.queworx.ExecutorFactory
import com.queworx.HttpBuilderFactory
import com.queworx.TradeDirection
import com.queworx.brokers.Broker
import com.queworx.brokers.Order
import com.queworx.brokers.OrderListener
import com.queworx.brokers.Position
import com.queworx.Instrument
import com.queworx.fetching.feed.Feed
import com.queworx.fetching.feed.FeedQueue
import com.queworx.fetching.feed.HttpStreamingFeed
import com.queworx.fetching.feed.TickListener

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@Log4j
class MbtBroker extends Broker implements Feed
{
	@groovy.beans.ListenerList
	private java.util.List<TickListener> __tickListeners = new CopyOnWriteArrayList<TickListener>()

	@groovy.beans.ListenerList
	private java.util.List<OrderListener> __orderListeners = new CopyOnWriteArrayList<OrderListener>()

	private MbtDatastore __datastore
	private MbtShortableTask __shortableTask
	private HttpBuilderFactory __httpBuilderFactory
	private ExecutorFactory __executorFactory
	private HttpStreamingFeed __httpStreamingFeed
	private SymbolFormatter __symbolFormatter
	private String __url

	private FeedQueue __feedQueue
	private HTTPBuilder __requestHttpBuilder
	private ExecutorService __shortableExecutorService

	private String __sessionId = RandomStringUtils.random(5, true, true)

	public MbtBroker(MbtDatastore datastore, MbtShortableTask shortableTask, HttpBuilderFactory httpBuilderFactory, ExecutorFactory executorFactory, HttpStreamingFeed httpStreamingFeed, SymbolFormatter symbolFormatter, String url)
	{
		__datastore = datastore
		__shortableTask = shortableTask
		__httpBuilderFactory = httpBuilderFactory
		__executorFactory = executorFactory
		__httpStreamingFeed = httpStreamingFeed
		__symbolFormatter = symbolFormatter
		__url = url
	}

	@Override
	void connect()
	{
		__httpStreamingFeed.connect(__url + "/stream")
		__feedQueue = new FeedQueue(500)

		__shortableTask.stop.set(false)
		__shortableExecutorService = __executorFactory.createSingleThreadExecutor()
		__shortableExecutorService.execute(__shortableTask)
	}

	@Override
	void disconnect()
	{
		__httpStreamingFeed.disconnect()

		__shortableTask.stop.set(true)
		__shortableExecutorService.shutdownNow()
		__shortableExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)

		__shortableTask.clear()
		__datastore.clear()
	}

	@Override
	void startPulling(List<Instrument> instruments)
	{
		List<Instrument> remove = __feedQueue.add(instruments)
		if(remove.size() > 0)
			stopPulling(remove)

		instruments.each {	__datastore.symbols[__symbolFormatter.fromStandard(it)] = it }
		String url = sprintf("%s/request?symbols=%s&action=subscribe&sessionId=%s", __url, instruments.collect { __symbolFormatter.fromStandard(it) }.join(","), __sessionId)
		__getRequestHttpBuilder().get(uri: url)
	}

	@Override
	void stopPulling(List<Instrument> instruments)
	{
		__feedQueue.remove(instruments)
		instruments.each {	__datastore.ticks.remove(it.id) }
		String url = sprintf("%s/request?symbols=%s&action=unsubscribe&sessionId=%s", __url, instruments.collect { __symbolFormatter.fromStandard(it) }.join(","), __sessionId)
		__getRequestHttpBuilder().get(uri: url)
	}

	@Override
	void stopPulling()
	{
		if(__feedQueue.size() > 0)
			stopPulling(__feedQueue.collect { it })
	}

	@Override
	public List<Instrument> getPulledInstruments()
	{
		return __feedQueue
	}

	@Override
	void createOrder(Order order)
	{
		log.info("MBT Submit Order: " + order)
		__datastore.unclaimedOrders.add(order)
		String url = sprintf("%s/request?action=submit&symbol=%s&direction=%s&quantity=%s&price=%s",
				__url, __symbolFormatter.fromStandard(order.instrument), order.direction == TradeDirection.Long ? "buy" : "sell", order.quantity, order.price.toString())
		__getRequestHttpBuilder().get(uri: url, contentType: ContentType.TEXT) { resp, reader -> return new BufferedReader(reader).readLine()}
	}

	@Override
	void resubmitOrder(Order order)
	{
		log.info("MBT Re-submit Order: " + order)
		String url = sprintf("%s/request?action=resubmit&orderId=%s&quantity=%s&price=%s", __url, __datastore.orderIds.get(order), order.quantity, order.price.toString())
		__getRequestHttpBuilder().get(uri: url, contentType: ContentType.TEXT)
	}

	@Override
	void cancelOrder(Order order)
	{
		log.info("MBT Cancel Order: " + order)
		String url = sprintf("%s/request?action=cancel&orderId=%s",	__url, __datastore.orderIds.get(order))
		__getRequestHttpBuilder().get(uri: url, contentType: ContentType.TEXT)
	}

	@Override
	public void startPullingShortable(List<Instrument> instruments)
	{
		__shortableTask.shortableList.addAll(instruments)
	}

	@Override
	public void stopPullingShortable(List<Instrument> instruments)
	{
		__shortableTask.shortableList.removeAll(instruments)
	}

	@Override
	void stopPullingShortable()
	{
		__shortableTask.shortableList.clear()
	}

	public Boolean isShortable(Instrument instrument)
	{
		if(__datastore.finalShortable.containsKey(instrument.id))
			return __datastore.finalShortable[instrument.id]

		return __datastore.shortable[instrument.id]
	}

	@Override
	Position getPosition(Instrument instrument)
	{
		return __datastore.positions[instrument]
	}

	@Override
	BigDecimal getCommission(Order order)
	{
		return new BigDecimal("4.95")
	}

	@Override
	BigDecimal getAvailableFunds()
	{
		return __datastore.availableFunds.get()
	}

	private HTTPBuilder __getRequestHttpBuilder()
	{
		if(__requestHttpBuilder == null)
			__requestHttpBuilder = __httpBuilderFactory.createHTTPBuilder()

		return __requestHttpBuilder
	}
}

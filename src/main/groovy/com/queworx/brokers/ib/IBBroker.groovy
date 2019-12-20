package com.queworx.brokers.ib

import com.ib.client.EClientSocket
import groovy.util.logging.Log4j
import com.queworx.CommandRunningQueue
import com.queworx.ExecutorFactory
import com.queworx.TradeDirection
import com.queworx.brokers.Broker
import com.queworx.brokers.Order
import com.queworx.brokers.OrderListener
import com.queworx.brokers.Position
import com.queworx.Instrument
import com.queworx.fetching.feed.Feed
import com.queworx.fetching.feed.FeedQueue
import com.queworx.fetching.feed.TickListener

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@Log4j
class IBBroker extends Broker implements Feed
{
	@groovy.beans.ListenerList
	private java.util.List<TickListener> __tickListeners = new CopyOnWriteArrayList<TickListener>()

	@groovy.beans.ListenerList
	private java.util.List<OrderListener> __orderListeners = new CopyOnWriteArrayList<OrderListener>()

	private EClientSocket __clientSocket
	private IBDatastore __datastore
	private IBConfig __config
	private CommandRunningQueue __commandRunningQueue
	private ExecutorFactory __executorFactory

	private FeedQueue __feedQueue
	private IBFeedHandler __feedHandler
	private IBShortableQueryTask __shortableQueryTask
	private ExecutorService __shortableExecutorService

	public IBBroker(EClientSocket clientSocket, IBDatastore datastore, IBShortableQueryTask shortableQueryTask, IBFeedHandler feedHandler, IBConfig config, CommandRunningQueue commandRunningQueue, ExecutorFactory executorFactory)
	{
		__clientSocket = clientSocket
		__datastore = datastore
		__shortableQueryTask = shortableQueryTask
		__feedHandler = feedHandler
		__config = config
		__commandRunningQueue = commandRunningQueue
		__executorFactory = executorFactory
	}

	@Override
	public void connect()
	{
		__feedQueue = new FeedQueue(__config.symbolLimit)

		__clientSocket.eConnect(__config.host, __config.port, __config.clientId)
		__clientSocket.reqAccountUpdates(true, __config.account)
		__commandRunningQueue.start()

		__shortableQueryTask.stop.set(false)
		__shortableExecutorService = __executorFactory.createSingleThreadExecutor()
		__shortableExecutorService.execute(__shortableQueryTask)
	}

	@Override
	public void disconnect()
	{
		__commandRunningQueue.stop()
		__clientSocket.eDisconnect()

		__shortableQueryTask.stop.set(true)
		__shortableExecutorService.shutdownNow()
		__shortableExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)

		__feedHandler.clear()
		__shortableQueryTask.clear()
	}

	@Override
	public void createOrder(Order order)
	{
		__submitOrder(order, __datastore.createOrderId(order))
	}

	@Override
	public void resubmitOrder(Order order)
	{
		__submitOrder(order, __datastore.getOrderId(order))
	}

	@Override
	public void cancelOrder(Order order)
	{
		__commandRunningQueue.runCommand(__clientSocket.&cancelOrder, [__datastore.getOrderId(order)])
	}

	@Override
	public void startPulling(List<Instrument> instruments)
	{
		List<Instrument> remove = __feedQueue.add(instruments)
		if (remove.size() > 0)
			stopPulling(remove)

		for (Instrument instrument in instruments)
		{
			log.debug "IB start pulling: " + instrument.symbol
			__feedHandler.startPulling(instrument, IBFeedHandler.TypeTickFeed)
		}
	}

	@Override
	public void stopPulling(List<Instrument> instruments)
	{
		__feedQueue.remove(instruments)
		for (Instrument instrument in instruments)
		{
			log.debug "IB stop pulling: " + instrument.symbol
			__feedHandler.stopPulling(instrument, IBFeedHandler.TypeTickFeed)
		}
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
	public void startPullingShortable(List<Instrument> instruments)
	{
		__shortableQueryTask.shortableList.addAll(instruments)
	}

	@Override
	public void stopPullingShortable(List<Instrument> instruments)
	{
		__shortableQueryTask.shortableList.removeAll(instruments)
	}

	@Override
	void stopPullingShortable()
	{
		__shortableQueryTask.shortableList.clear()
	}

	@Override
	public Boolean isShortable(Instrument instrument)
	{
		return __datastore.getShortable(instrument)
	}

	@Override
	Position getPosition(Instrument instrument)
	{
		return __datastore.getPosition(instrument)
	}

	@Override
	BigDecimal getCommission(Order order)
	{
		return new BigDecimal("1.0").max(order.quantity * (new BigDecimal("0.005")))
	}

	@Override
	BigDecimal getAvailableFunds()
	{
		return __datastore.availableFunds.get()
	}

	private void __submitOrder(Order order, int orderId)
	{
		com.ib.client.Order ibOrder = new com.ib.client.Order()
		ibOrder.m_clientId = __config.clientId
		ibOrder.m_transmit = true
		ibOrder.m_orderType = "LMT"
		ibOrder.m_orderId = orderId
		ibOrder.m_action = order.direction == TradeDirection.Short ? "SELL" : "BUY"
		ibOrder.m_totalQuantity = order.quantity
		ibOrder.m_lmtPrice = order.price.toDouble()
        ibOrder.m_account = __config.account
		ibOrder.m_hidden = order.hidden
		if(order.minQuantity != null) ibOrder.m_minQty = order.minQuantity

		log.info("IB Submit Order: " + order + " " + orderId)
		__commandRunningQueue.runCommand(__clientSocket.&placeOrder, [ibOrder.m_orderId, __datastore.createContract(order.instrument), ibOrder])
	}
}
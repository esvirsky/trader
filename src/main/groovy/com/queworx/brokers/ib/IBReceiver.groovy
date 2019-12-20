package com.queworx.brokers.ib

import com.ib.client.*
import groovy.util.logging.Log4j
import com.queworx.ErrorHandler
import com.queworx.InstrumentDatastore
import com.queworx.TradeDirection
import com.queworx.brokers.OrderStatus
import com.queworx.brokers.Position
import com.queworx.Instrument
import com.queworx.Tick

@Log4j
class IBReceiver implements EWrapper
{
	private IBDatastore __datastore
	private InstrumentDatastore __instrumentDatastore
	private ErrorHandler __errorHandler
	private IBBroker __ibBroker

	private HashMap<Integer, Tick> __tickMap = [:]

	public IBReceiver(IBDatastore datastore, InstrumentDatastore instrumentDatastore, ErrorHandler errorHandler)
	{
		__datastore = datastore
		__instrumentDatastore = instrumentDatastore
		__errorHandler = errorHandler
	}

	public void setBroker(IBBroker broker)
	{
		__ibBroker = broker
	}

	@Override
	void tickPrice(int tickerId, int field, double price, int canAutoExecute)
	{
		synchronized (this)
		{
			try {
				if (!(field in [1, 2, 4]))
					return

				Tick tick = __tickMap.get(tickerId)?: new Tick()
				Instrument instrument = __datastore.getTickerInstrument(tickerId)

				if (field == 1)	tick.bid = price.toBigDecimal().setScale(3, BigDecimal.ROUND_HALF_DOWN)
				if (field == 2)	tick.ask = price.toBigDecimal().setScale(3, BigDecimal.ROUND_HALF_DOWN)
				if (field == 4)	tick.lastTradePrice = price.toBigDecimal().setScale(3, BigDecimal.ROUND_HALF_DOWN)
				tick.time = System.currentTimeMillis()
				__tickMap.put(tickerId, tick)

				__ibBroker.tickListeners.each { it.tickUpdateNotification(instrument, tick.copy()) }
			}
			catch(Exception ex) { __errorHandler.handleError("error getting tick price", ex)}
		}
	}

	@Override
	void tickSize(int tickerId, int field, int size)
	{
		synchronized (this)
		{
			try
			{
				if (!(field in [0, 3, 8]))
					return

				Tick tick = __tickMap.get(tickerId)?: new Tick()
				Instrument instrument = __datastore.getTickerInstrument(tickerId)

				if (field == 0)	tick.bidSize = size * 100
				if (field == 3)	tick.askSize = size * 100
				if (field == 8)	tick.lastTradeVolume = size
				tick.time = System.currentTimeMillis()
				__tickMap.put(tickerId, tick)

				__ibBroker.tickListeners.each { it.tickUpdateNotification(instrument, tick.copy()) }
			}
			catch(Exception ex) { __errorHandler.handleError("error getting tick price", ex)}
		}
	}

	@Override
	void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void tickGeneric(int tickerId, int tickType, double value)
	{
		synchronized (this)
		{
			if(tickType == 46)
			{
				Instrument instrument = __datastore.getTickerInstrument(tickerId)
//				log.debug("IB generic tick received: " + tickerId + " " + tickType + " " + value + " " + instrument.symbol)
				if(value > 2.5) //3.0 at least 1000 shares shortable
					__datastore.setShortable(instrument, true)
				else if(value > 1.5) // 2.0 IB is trying to locate shares
					__datastore.setShortable(instrument, false)
				else // >0, not shortable
					__datastore.setShortable(instrument, false)
			}
		}
	}

	@Override
	void tickString(int tickerId, int tickType, String value)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld)
	{
		synchronized (this)
		{
			try {
				com.queworx.brokers.Order order = __datastore.getOrder(orderId)

				// Order from a different session
				if(order == null)
					return

				// avoids duplicate filled, cancelled, rejected messages
				if(order.lastStatus != null && !order.lastStatus.active)
					return

				OrderStatus orderStatus = new OrderStatus()
				orderStatus.filledAvgPrice = avgFillPrice.toBigDecimal().setScale(3, BigDecimal.ROUND_HALF_DOWN)
				orderStatus.filledQuantity = filled
				orderStatus.remainingQuantity = remaining
				orderStatus.active = !status.equals("Cancelled") && !status.equals("Filled") && !status.equals("Inactive")
				orderStatus.lastUpdate = System.currentTimeMillis()
				order.lastStatus = orderStatus

				log.debug("IB Status: " + order.instrument.symbol + " " + orderStatus)
				__ibBroker.orderListeners.each { it.orderUpdateNotification(order, orderStatus, __ibBroker) }
			}
			catch(Exception ex) { __errorHandler.handleError("Error in IB Order Status", ex)}
		}
	}

	@Override
	void openOrder(int orderId, Contract contract, Order order, OrderState orderState)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void openOrderEnd()
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void updateAccountValue(String key, String value, String currency, String accountName)
	{
		synchronized (this)
		{
			if (key == "AvailableFunds")
				__datastore.availableFunds.set(new BigDecimal(value))
		}
	}

	@Override
	void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName)
	{
		synchronized (this)
		{
			try {
				Instrument instrument = __instrumentDatastore.getOrCreateInstrument(contract.m_symbol)
//				log.debug("IB Portfolio update: " + contract.m_symbol + " " + marketValue)
				__datastore.setPosition(instrument, new Position(instrument, Math.abs(position), marketPrice, position >= 0 ? TradeDirection.Long : TradeDirection.Short))
			}
			catch(Exception ex) { __errorHandler.handleError("Error in IB Update Portfolio", ex)}
		}
	}

	@Override
	void updateAccountTime(String timeStamp)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void accountDownloadEnd(String accountName)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void nextValidId(int orderId)
	{
		synchronized (this)
		{
			__datastore.setInitialOrderId(orderId)
		}
	}

	@Override
	void contractDetails(int reqId, ContractDetails contractDetails)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void bondContractDetails(int reqId, ContractDetails contractDetails)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void contractDetailsEnd(int reqId)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void execDetails(int reqId, Contract contract, Execution execution)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void execDetailsEnd(int reqId)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void updateNewsBulletin(int msgId, int msgType, String message, String origExchange)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void managedAccounts(String accountsList)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void receiveFA(int faDataType, String xml)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void scannerParameters(String xml)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void scannerDataEnd(int reqId)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void currentTime(long time)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void fundamentalData(int reqId, String data)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void deltaNeutralValidation(int reqId, UnderComp underComp)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void tickSnapshotEnd(int reqId)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void marketDataType(int reqId, int marketDataType)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void commissionReport(CommissionReport commissionReport)
	{
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	void error(Exception e)
	{
		log.error("IBBroker error", e)
	}

	@Override
	void error(String str)
	{
		log.error("IBBroker: " + str)
	}

	@Override
	void error(int id, int errorCode, String errorMsg)
	{
		synchronized (this)
		{
			log.debug("IB Error: " + id + " " + errorCode + " " + errorMsg)
			if (errorCode in [2103,2104, 2105, 2106, 2108]) return // some data farm is connected/disconnected, I just need to ignore it
			if (errorCode == 200) { log.error("IBBroker error: " + id + "," + errorCode + "," + errorMsg); return; }
			if (errorCode in [201, 202, 104, 404, 161, 2102]) return //Order cancelled, can't modify a filled order, order held while securities are located, cancel attempted when order is not in a cancellable state, Unable to modify this order as its still being processed
			if (errorMsg == "" || errorMsg == null) return
			if (errorCode == 103) return // Happens when IB kills the order for whatever reason, and then I try to modify it
			if (errorCode == 322 && errorMsg.contains("Duplicate ticker id")) return
			if (errorCode == 354 || errorCode == 300) return // Requested market data is not subscribed, not sure why I get this - maybe 2 seconds isn't enough to subscribe and unsubscribe
			if (errorCode == 1100 || errorCode == 1102) return // ib disconnected, ib connection restored

			if (errorCode == 0 && errorMsg.contains("Warning: Approaching max rate of 50 messages per second"))
			{
				log.warn("IB approaching max rate of messages per second")
				return
			}

			__errorHandler.handleError("IBBroker error: " + errorCode + "," + errorMsg, null)
		}
	}

	@Override
	void connectionClosed()
	{
		synchronized (this)
		{
			log.info("IBBroker Connection Closed")
		}
	}
}

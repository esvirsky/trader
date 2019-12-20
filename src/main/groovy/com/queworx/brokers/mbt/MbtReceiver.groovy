package com.queworx.brokers.mbt

import groovy.util.logging.Log4j
import com.queworx.Clock
import com.queworx.InstrumentDatastore
import com.queworx.TradeDirection
import com.queworx.brokers.Order
import com.queworx.brokers.OrderStatus
import com.queworx.brokers.Position
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.fetching.feed.StreamingFeedListener

@Log4j
class MbtReceiver implements StreamingFeedListener
{
	private MbtBroker __mbtBroker
	private MbtDatastore __datastore
	private InstrumentDatastore __instrumentDatastore
	private SymbolFormatter __symbolFormatter
	private Clock __clock

	public MbtReceiver(MbtBroker mbtBroker, MbtDatastore datastore, InstrumentDatastore instrumentDatastore, SymbolFormatter symbolFormatter, Clock clock)
	{
		__mbtBroker = mbtBroker
		__datastore = datastore
		__instrumentDatastore = instrumentDatastore
		__symbolFormatter = symbolFormatter
		__clock = clock
	}

	@Override
	void streamingFeedData(String data)
	{
		log.debug("MBT: " + data)
		String[] parts = data.split(",")
		if (parts[0] == "error")
			log.error("Error from MBT server: " + data)
		else if(parts[0] == "q" || parts[0] == "t")
			__processQuote(parts)
		else if(parts[0] == "o")
			__processOrderEvent(parts)
		else if(parts[0] == "pn")
			__processPosition(parts)
		else if(parts[0] == "account")
		{
			log.debug("Account 4Available Funds: " + parts[1])
			__datastore.availableFunds.set(new BigDecimal(parts[1]))
		}
	}

	private void __processQuote(String[] parts)
	{
		Instrument instrument = __datastore.symbols[parts[1]]
		if (instrument == null)
			return

		Tick tick = __datastore.ticks[instrument.id]
		if(tick == null)
			tick = __datastore.ticks[instrument.id] = new Tick()

		if (parts[0] == "q")
		{
			tick.bidSize = Integer.valueOf(parts[2]) * 100
			tick.bid = new BigDecimal(parts[3])
			tick.ask = new BigDecimal(parts[4])
			tick.askSize = Integer.valueOf(parts[5]) * 100
			tick.lastTradePrice = new BigDecimal(parts[6])
			tick.time = Long.valueOf(parts[9]) // already in local time

			__datastore.shortable[instrument.id] = parts[7] == "1"
		}
		else if (parts[0] == "t")
		{
			tick.lastTradePrice = new BigDecimal(parts[2])
			tick.lastTradeVolume = Integer.valueOf(parts[3])
			tick.lastTradeTime = Long.valueOf(parts[4]) // already in local time
		}

		__mbtBroker.tickListeners.each { it.tickUpdateNotification(instrument, tick.copy()) }
	}

	private void __processOrderEvent(String[] parts)
	{
		String orderId = parts[2]
		Order order = __datastore.orderIds.inverse().get(orderId)
		OrderStatus orderStatus
		if(parts[1] == "Enter")
		{
			__claimOrder(parts, orderId)
			return
		}

		// Getting info for an order that we are not tracking
		if(order == null)
		{
//			log.warn("Getting info for an order that we don't have: " + orderId + " " + __datastore.orderIds.keySet().join(",") + " " + __datastore.orderIds.inverse().get(orderId))
			return
		}

		orderStatus = new OrderStatus()
		orderStatus.filledQuantity = Integer.valueOf(parts[7])
		orderStatus.filledAvgPrice = new BigDecimal(parts[6])
		orderStatus.remainingQuantity = order.quantity - orderStatus.filledQuantity
		orderStatus.active = orderStatus.remainingQuantity != 0
		orderStatus.lastUpdate = __clock.currentTimeMillis()

		if(parts[1] in ["Cancel", "Order Reject", "Replace Reject", "Suspended", "Order Cancelled"])
			orderStatus.active = false

		if(parts[1] == "Order Reject" && order.direction == TradeDirection.Short)
		{
			__datastore.finalShortable[order.instrument.id] = false
			log.warn("MBT shortable order reject: " + order.instrument.symbol)
		}

		log.debug("MBT Order Status: " + order.instrument.id + " " + orderStatus)
		__mbtBroker.orderListeners.each {it.orderUpdateNotification(order, orderStatus, __mbtBroker)}
	}

	private void __processPosition(String[] parts)
	{
		Instrument instrument = __instrumentDatastore.getOrCreateInstrument(__symbolFormatter.toStandard(parts[1]).symbol)
		int quantity = Integer.valueOf(parts[2])
		BigDecimal avgPrice = new BigDecimal(parts[3])
		__datastore.positions[instrument] = new Position(instrument, Math.abs(quantity), avgPrice, quantity >= 0 ? TradeDirection.Long : TradeDirection.Short)
	}

	private void __claimOrder(String[] parts, String orderId)
	{
		String symbol = parts[3]
		int quantity = Integer.valueOf(parts[5])
		TradeDirection direction = parts[4] == "sell" ? TradeDirection.Short : TradeDirection.Long

		Order order = __datastore.unclaimedOrders.find { it.direction == direction && __symbolFormatter.fromStandard(it.instrument) == symbol && quantity == it.quantity }
		if(order == null)
		{
			log.debug("Can't claim an order: " + parts.join(",") + " " + __datastore.unclaimedOrders.size() + " " + __datastore.unclaimedOrders)
			return
		}

		__datastore.unclaimedOrders.remove(order)
		__datastore.orderIds.put(order, orderId)
	}
}

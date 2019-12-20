package com.queworx.brokers.ib

import com.ib.client.Contract
import org.apache.commons.collections.bidimap.DualHashBidiMap
import com.queworx.brokers.Order
import com.queworx.brokers.Position
import com.queworx.Exchange
import com.queworx.Instrument

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread safe
 */
class IBDatastore
{
	public AtomicReference<BigDecimal> availableFunds = new AtomicReference<BigDecimal>(0)

	private int __tickerId = -1
	private DualHashBidiMap __tickerMap = Collections.synchronizedMap(new DualHashBidiMap())

	private Integer __orderId = null
	private ConcurrentHashMap<Integer, Order> __idOrderMap = [:]
	private ConcurrentHashMap<Order, Integer> __orderIdMap = [:]

	private ConcurrentHashMap<Instrument, Boolean> __shortable = [:]
	private ConcurrentHashMap<Instrument, Position> __positions = [:]

	public synchronized int createTickerId(Instrument instrument)
	{
		__tickerId++
		__tickerMap.put(__tickerId, instrument)
		return __tickerId
	}

	public synchronized int createOrderId(Order order)
	{
		if (__orderId == null)
			throw new Exception("IB: Order id hasn't been set yet")

		__orderId++
		__orderIdMap.put(order, __orderId)
		__idOrderMap.put(__orderId, order)
		return __orderId
	}

	public synchronized void setInitialOrderId(int orderId)
	{
		__orderId = orderId
	}

	public Integer getTickerId(Instrument instrument)
	{
		return __tickerMap.getKey(instrument)
	}

	public Instrument getTickerInstrument(int tickerId)
	{
		return __tickerMap.get(tickerId)
	}

	public Integer getOrderId(Order order)
	{
		return __orderIdMap.get(order)
	}

	public Order getOrder(int orderId)
	{
		return __idOrderMap.get(orderId)
	}

	public Boolean getShortable(Instrument instrument)
	{
		return __shortable[instrument]
	}

	public void setShortable(Instrument instrument, boolean shortable)
	{
		__shortable[instrument] = shortable
	}

	public Position getPosition(Instrument instrument)
	{
		return __positions[instrument]
	}

	public void setPosition(Instrument instrument, Position position)
	{
		__positions[instrument] = position
	}

	public Contract createContract(Instrument instrument)
	{
		return new Contract(m_symbol: instrument.symbol.replace(".A", " A").replace(".B", " B"), m_primaryExch: __getExchange(instrument), m_secType: "STK", m_exchange: "SMART", m_currency: "USD")
	}

	private String __getExchange(Instrument instrument)
	{
		if (instrument.exchange == Exchange.amex) return "AMEX"
		if (instrument.exchange == Exchange.nyse) return "NYSE"
		if (instrument.exchange == Exchange.nyse_arca) return "ARCA"
		if (instrument.exchange == Exchange.nasdaq) return "NASDAQ"
		if (instrument.exchange == Exchange.tsx) return "TSX"
	}
}

package com.queworx.trader.trading

import groovy.util.logging.Log4j
import com.queworx.TradeDirection
import com.queworx.brokers.Broker
import com.queworx.brokers.Order
import com.queworx.brokers.Position
import com.queworx.Instrument

@Log4j
class BrokerSet
{
	public List<Broker> brokers

	public BrokerSet(List<Broker> brokers)
	{
		this.brokers = brokers
	}

	public void startPullingShortable(List<Instrument> instruments)
	{
		brokers.each{ it.startPullingShortable(instruments) }
	}

	public void stopPullingShortable(List<Instrument> instruments)
	{
		brokers.each{ it.stopPullingShortable(instruments) }
	}

	public void stopPullingShortable()
	{
		brokers.each{ it.stopPullingShortable() }
	}

	public boolean isShortable(Instrument instrument)
	{
		return brokers.count { it.isShortable(instrument) } > 0
	}

	public List<Position> getPositions(Instrument instrument)
	{
		return brokers.collect { it.getPosition(instrument) }.findAll { it != null }
	}

	public Broker getBestBroker(Order order)
	{
		return brokers.sort(false) { 0.3*(it.availableFunds/10000) - 0.7*it.getCommission(order) }.reverse().find { it.canTrade(order) }
	}
}

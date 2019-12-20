package com.queworx.brokers.mbt

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.Maps
import com.queworx.brokers.Order
import com.queworx.brokers.Position
import com.queworx.Instrument
import com.queworx.Tick

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread safe
 */
class MbtDatastore
{
	public AtomicReference<BigDecimal> availableFunds = new AtomicReference<BigDecimal>(0)

	public ConcurrentHashMap<String, Instrument> symbols = [:]
	public ConcurrentHashMap<Integer, Tick> ticks = [:]

	public ConcurrentHashMap<Integer, Boolean> shortable = [:]
	public ConcurrentHashMap<Integer, Boolean> finalShortable = [:]

	public ConcurrentHashMap<Instrument, Position> positions = [:]

	public BiMap<Order, String> orderIds = Maps.synchronizedBiMap(HashBiMap.create());
	List<Order> unclaimedOrders = Collections.synchronizedList([])

	public void clear()
	{
		availableFunds.set(0)
		symbols.clear()
		ticks.clear()
		shortable.clear()
		finalShortable.clear()
		positions.clear()
		orderIds.clear()
		unclaimedOrders.clear()
	}
}

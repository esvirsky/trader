package com.queworx.trader.tracking

import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.fetching.feed.TickListener

import java.util.concurrent.ConcurrentHashMap

class TickTracker implements TickListener
{
	private ConcurrentHashMap<Integer, Tick> __tickMap = [:]
	private ConcurrentHashMap<Integer, Tick> __lastTickMap = [:]

	public Tick getLatestTick(Instrument instrument)
	{
		return __tickMap[instrument.id]
	}

	public Tick getPrevTick(Instrument instrument)
	{
		return __lastTickMap[instrument.id]
	}

	public void clear()
	{
		__tickMap.clear()
		__lastTickMap.clear()
	}

	@Override
	void tickUpdateNotification(Instrument instrument, Tick tick)
	{
		Tick lastTick = __tickMap[instrument.id]
		__tickMap.put(instrument.id, tick)
		if(lastTick != null)
			__lastTickMap.put(instrument.id, lastTick)
	}
}

package com.queworx.trader.tracking

import com.queworx.BdRange
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.fetching.feed.TickListener

import java.util.concurrent.ConcurrentHashMap

class RangeTracker implements TickListener
{
	private ConcurrentHashMap<Integer, BdRange> __ranges = [:]

	public BdRange getRange(Instrument instrument)
	{
		return __ranges[instrument.id]
	}

	@Override
	void tickUpdateNotification(Instrument instrument, Tick tick)
	{
		if(tick.lastTradePrice == null)
			return

		BdRange range = __ranges[instrument.id]
		if(range == null)
		{
			__ranges[instrument.id] = new BdRange(tick.lastTradePrice, tick.lastTradePrice)
			return
		}

		range.low = range.low.min(tick.lastTradePrice)
		range.high = range.high.max(tick.lastTradePrice)
	}
}

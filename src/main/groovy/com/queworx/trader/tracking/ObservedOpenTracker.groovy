package com.queworx.trader.tracking

import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.fetching.feed.TickListener

import java.util.concurrent.ConcurrentHashMap

class ObservedOpenTracker implements TickListener
{
	private long __marketOpen
	private ConcurrentHashMap<Integer, BigDecimal> __observedOpeningPrints = [:]

	/**
	 * @param marketOpen - local time in millis since epoch
	 */
	public ObservedOpenTracker(long marketOpen)
	{
		__marketOpen = marketOpen
	}

	public BigDecimal getObservedOpen(Instrument instrument)
	{
		return __observedOpeningPrints[instrument.id]
	}

	@Override
	void tickUpdateNotification(Instrument instrument, Tick tick)
	{
		if (__observedOpeningPrints[instrument.id] == null && tick.lastTradeTime >= __marketOpen)
			__observedOpeningPrints[instrument.id] = tick.getLastTradePrice()
	}
}

package com.queworx.trader.tracking

import com.queworx.BdRange
import com.queworx.Instrument
import com.queworx.fetching.feed.SnapshotListener

import java.util.concurrent.ConcurrentHashMap

class SnapshotTracker implements SnapshotListener
{
	private ConcurrentHashMap<Integer, BigDecimal> __openingPrints = [:]
	private ConcurrentHashMap<Integer, Integer> __ivolumes = [:]
	private ConcurrentHashMap<Integer, BdRange> __ranges = [:]

	public BigDecimal getOpen(Instrument instrument)
	{
		return __openingPrints[instrument.id]
	}

	public Integer getIVolume(Instrument instrument)
	{
		return __ivolumes[instrument.id]
	}

	public BdRange getRange(Instrument instrument)
	{
		return __ranges[instrument.id]
	}

	@Override
	public void openUpdateNotification(Instrument instrument, BigDecimal open)
	{
		__openingPrints[instrument.id] = open
	}

	@Override
	public void ivolumeUpdateNotification(Instrument instrument, Integer ivolume)
	{
		__ivolumes[instrument.id] = ivolume
	}

	@Override
	public void rangeUpdateNotification(Instrument instrument, BdRange range)
	{
		__ranges[instrument.id] = range
	}
}

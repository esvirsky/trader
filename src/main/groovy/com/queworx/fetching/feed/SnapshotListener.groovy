package com.queworx.fetching.feed

import com.queworx.BdRange
import com.queworx.Instrument

interface SnapshotListener
{
	public void ivolumeUpdateNotification(Instrument instrument, Integer ivolume)
	public void openUpdateNotification(Instrument instrument, BigDecimal open)
	public void rangeUpdateNotification(Instrument instrument, BdRange range)
}

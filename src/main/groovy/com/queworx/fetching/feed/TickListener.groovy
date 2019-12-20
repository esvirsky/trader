package com.queworx.fetching.feed

import com.queworx.Instrument
import com.queworx.Tick

interface TickListener
{
	/**
	 * Called when new information about instrument tick data is available
	 *
	 * @param instrument
	 * @param tick
	 */
	public void tickUpdateNotification(Instrument instrument, Tick tick)
}

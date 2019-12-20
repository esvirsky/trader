package com.queworx.trader.strategies.five_percent_drop


import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.trader.strategies.DataSet
import com.queworx.trader.strategies.Rater
import com.queworx.trader.strategies.Rating
import com.queworx.trader.strategies.StrategyClock
import com.queworx.trader.tracking.TickTracker

class FivePercentDropRater implements Rater
{
	private TickTracker __tickTracker
	private StrategyClock __strategyClock

	public FivePercentDropRater(com.queworx.trader.tracking.TickTracker tickTracker, StrategyClock strategyClock)
	{
		__tickTracker = tickTracker
		__strategyClock = strategyClock
	}

	@Override
	Rating rate(Instrument instrument, DataSet dataSet)
	{
		Tick tick = __tickTracker.getLatestTick(instrument)
		if(tick == null || tick.bid == null || tick.ask == null || tick.bid == 0 || tick.ask == 0)
			return new Rating(0f, "no tick data yet", false, true)

		if(tick.lastTradePrice == null)
			return new Rating(0f, "hasn't traded yet", false, true)

		Double lastClose = ((FivePercentDropDataSet)dataSet).getPrevClose(instrument)
		float delta = (tick.lastTradePrice - lastClose)/lastClose

		// whether we should trade this instrument
		boolean shouldTrade = delta <= -0.05

		// whether we should watch this instrument at the tick level
		boolean shouldWatch = delta <= -0.04

		return new Rating(Math.abs(delta), sprintf("%.4f, %.4f", lastClose, delta), shouldTrade, shouldWatch)
	}
}

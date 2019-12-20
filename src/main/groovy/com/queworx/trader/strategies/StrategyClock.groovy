package com.queworx.trader.strategies

import com.queworx.Clock
import com.queworx.Sleeper
import com.queworx.trader.Config

class StrategyClock
{
	public Clock clock
	private Sleeper __sleeper
	private Config __config

	public StrategyClock(Sleeper sleeper, Clock clock, Config config)
	{
		this.clock = clock
		__sleeper = sleeper
		__config = config
	}

	public void waitForOpen()
	{
		int diff = __config.marketOpen.getMillisOfDay() - clock.now().getMillisOfDay()
		if (diff > 0) __sleeper.sleep(diff)
	}

	public boolean timeForTradingStart()
	{
		return clock.now() >= __config.tradingStart
	}

	public boolean timeForTradingEnd()
	{
		return clock.now() >= __config.tradingEnd
	}

	public int getSecondsFromStart()
	{
		return (clock.now().millisOfDay - __config.marketOpen.millisOfDay)/1000
	}

	public int getMinutesFromStart()
	{
		return getSecondsFromStart()/60
	}
}

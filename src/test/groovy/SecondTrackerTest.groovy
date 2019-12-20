package com.queworx.trader

import org.gmock.GMockTestCase
import org.junit.Before
import com.queworx.Clock
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.trader.tracking.SecondTracker

class SecondTrackerTest extends GMockTestCase
{
	private SecondTracker __tracker
	private Clock __clock
	private Calendar __calendar
	private Closure __construct

	@Before void setUp()
	{
		__clock = mock(Clock)
		__calendar = mock(rise.Calendar)

		__construct = {__tracker = new SecondTracker(__calendar, __clock, 30000)}
	}

	void testGetLatestTick()
	{
		def instruments = (0..3).collect{new Instrument(id: it)}
		__clock.currentTimeMillis().returns(7261000).stub()
		__construct()
		play {
			assertEquals null, __tracker.getLatestTick(instruments[0], 0)
			assertEquals null, __tracker.getLatestTick(instruments[0], 100)
			__tracker.tickUpdateNotification(instruments[0], new Tick(time: 7250000))
			__tracker.tickUpdateNotification(instruments[0], new Tick(time: 7260000))
			__tracker.tickUpdateNotification(instruments[0], new Tick(time: 7260500))
			__tracker.tickUpdateNotification(instruments[1], new Tick(time: 30000))
			__tracker.tickUpdateNotification(instruments[1], new Tick(time: 40000))
			assertEquals null, __tracker.getLatestTick(instruments[0], 7249000)
			assertEquals 7250000, __tracker.getLatestTick(instruments[0], 7250000).time
			assertEquals 7250000, __tracker.getLatestTick(instruments[0], 7255000).time
			assertEquals 7260000, __tracker.getLatestTick(instruments[0], 7260000).time
			assertEquals 7260500, __tracker.getLatestTick(instruments[0], 7270000).time
		}
	}

	void testGetLatestTickCleanUp()
	{
		def instruments = (0..3).collect{new Instrument(id: it)}
		__clock.currentTimeMillis().returns(7290000).stub()
		__construct()
		play {
			__tracker.tickUpdateNotification(instruments[0], new Tick(time: 7250000))
			__tracker.tickUpdateNotification(instruments[0], new Tick(time: 7260000))
			__tracker.tickUpdateNotification(instruments[0], new Tick(time: 7285000))
			assertEquals null, __tracker.getLatestTick(instruments[0], 7255000)
			assertEquals 7260000, __tracker.getLatestTick(instruments[0], 7260000).time
		}
	}
}

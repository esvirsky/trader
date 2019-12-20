package com.queworx.trader

import org.gmock.GMockTestCase
import org.junit.Before
import com.queworx.Clock
import com.queworx.Instrument
import com.queworx.fetching.feed.Feed

class FeedTest extends GMockTestCase
{
	private FeedSubclass __feed
	private Clock __clock
	private Closure __construct

	class FeedSubclass implements Feed
	{
		public List<Instrument> instruments = []

		FeedSubclass(Clock clock, Integer capacity = null)
		{
			super(clock, capacity)
		}

		@Override
		void connect(){}

		@Override
		void disconnect(){}

		@Override
		public void startPulling(List<Instrument> instruments)
		{
			this.instruments.addAll(instruments)
		}

		@Override
		public void stopPulling(List<Instrument> instruments)
		{
			this.instruments.removeAll(instruments)
		}

		@Override
		void stopPulling()
		{

		}

		@Override
		List<Instrument> getPulledInstruments()
		{
			return this.instruments
		}
	}

	@Before void setUp()
	{
		__clock = mock(Clock)
		__clock.currentTimeMillis().returns(100).stub()

		__construct = {__feed = new FeedSubclass(__clock, it)}
	}

	void testStartTrackingCapacityError()
	{
		__construct(3)
		play {
			shouldFail(IllegalArgumentException) { __feed.startPulling([1, 2, 3, 4]) }
		}
	}

	void testStartPullingCapacityNoRemoval()
	{
		def instruments = (0..3).collect{new Instrument()}
		__construct(10)
		play {
			__feed.startPulling(instruments)
			assertEquals instruments, __feed.instruments
		}
	}

	void testStartPullingCapacityWithRemoval()
	{
		def instruments = (0..7).collect{new Instrument(id: it)}
		__construct(5)
		play {
			__feed.startPulling(instruments[0..2])
			__feed.startPulling(instruments[2..5])
			__feed.startPulling(instruments[4..7])
			assertEquals instruments[3..7], __feed.instruments
		}
	}

//	void testGetTrackingTime()
//	{
//		def instruments = [new Instrument(), new Instrument()]
//		__construct()
//		play {
//			__feed.startPulling(instruments)
////			assertEquals 100, __tracker.getTrackingStartTime(instruments[0])
//		}
//	}
}

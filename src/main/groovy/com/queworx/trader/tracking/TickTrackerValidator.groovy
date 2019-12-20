package com.queworx.trader.tracking

import groovy.util.logging.Log4j
import com.queworx.ExecutorFactory
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.fetching.feed.Feed

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Takes multiple trackers and makes sure that they have matching data. This is
 * needed so that we aren't working off of delayed data from one of the trackers
 */
@Log4j
class TickTrackerValidator
{
	private ExecutorFactory __executorFactory
	private ScheduledExecutorService __scheduledExecutorService

	private List<Feed> __feeds
	private List<TickTracker> __trackers
	private Instrument __instrument

	private Long __startTime
	private boolean __running = false

	public TickTrackerValidator(ExecutorFactory executorFactory, List<Feed> feeds, List<TickTracker> trackers)
	{
		__executorFactory = executorFactory
		__feeds = feeds
		__trackers = trackers
	}

	public void start(Instrument instrument)
	{
		__running = true
		__instrument = instrument
		__startTime = System.currentTimeMillis()

		__feeds.each { it.startPulling([instrument]) }
		__scheduledExecutorService = __executorFactory.createSingleThreadScheduledExecutor()
		__scheduledExecutorService.scheduleWithFixedDelay(new Task(), 0, 5000, TimeUnit.MILLISECONDS)
	}

	public void stop()
	{
		__feeds.each { it.stopPulling([__instrument]) }
		__scheduledExecutorService.shutdownNow()
		__scheduledExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
		__running = false
	}

	public boolean isRunning()
	{
		return __running
	}

	private class Task implements Runnable
	{
		private int __counter = 0

		public void run()
		{
			try
			{
				List<Tick> ticks = __trackers.collect { it.getLatestTick(__instrument) }.findAll { it != null && it.bid != null }
				if(ticks == null)
					return

				// give them a min to aquire current data
				if(ticks.size() < 2)
				{
					if(System.currentTimeMillis() - __startTime >= 60000)
						log.warn("Still haven't acquired validation data ticks from streams");
					return
				}

				__counter++
				def maxDelta = ticks.count {it == null} > 0 ? 1 : ticks.collect { (ticks[0].bid - it.bid)/it.bid }.max()
				if(maxDelta < 0.005)
					__counter = 0

				if(__counter >= 2)
				{
					String tickStr = ticks.collect{ it.toString() }.join("  ::  ")
					log.error("Warning!!! Tick trackers out of sync: " + __instrument.symbol + " " + tickStr)
				}
			}
			catch (Exception ex)
			{
				log.error("Exception in TickTrackerValidator", ex)
			}
		}
	}
}

package com.queworx.fetching.activetick

import groovy.util.logging.Log4j
import com.queworx.Clock
import com.queworx.ExecutorFactory
import com.queworx.BdRange
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.fetching.activetick.http.ActiveTickSnapshotFetcher
import com.queworx.fetching.feed.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Log4j
class ActiveTickPollingFeed implements PollingFeed, Feed
{
    @groovy.beans.ListenerList
    private java.util.List<TickListener> __tickListeners = new CopyOnWriteArrayList<TickListener>()

	@groovy.beans.ListenerList
	private java.util.List<SnapshotListener> __snapshotListeners = new CopyOnWriteArrayList<SnapshotListener>()

    private ActiveTickSnapshotFetcher __activeTick
	private ExecutorFactory __executorFactory
	private Clock __clock
	private int __pollingInterval
	private List<SnapAttribute> __attributes

	private FeedQueue __feedQueue
	private ScheduledExecutorService __scheduledExecutorService
    private boolean __connected
	private ScheduledFuture __future

	/**
	 * @param activeTick
	 * @param pollingInterval - in milliseconds
	 */
    public ActiveTickPollingFeed(ActiveTickSnapshotFetcher activeTick, ExecutorFactory executorFactory, Clock clock, List<SnapAttribute> attributes, int pollingInterval)
    {
        __activeTick = activeTick
	    __clock = clock
	    __pollingInterval = pollingInterval
	    __executorFactory = executorFactory
	    __attributes = attributes
    }

    @Override
    void connect()
    {
	    __connected = true
	    __feedQueue = new FeedQueue(2000)
	    __scheduledExecutorService = __executorFactory.createSingleThreadScheduledExecutor()
	    __future = __scheduledExecutorService.scheduleWithFixedDelay(new Task(), 0, __pollingInterval, TimeUnit.MILLISECONDS)
    }

    @Override
    void disconnect()
    {
	    __scheduledExecutorService.shutdownNow()
	    __scheduledExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
        __connected = false
    }

	/**
	 * @param pollingInterval In milliseconds
	 */
	public void setPollingInterval(int pollingInterval)
	{
		__pollingInterval = pollingInterval
		__future.cancel(false)
		__future = __scheduledExecutorService.scheduleWithFixedDelay(new Task(), __pollingInterval, __pollingInterval, TimeUnit.MILLISECONDS)
	}

	@Override
	public void startPulling(List<Instrument> instruments)
	{
		synchronized (__feedQueue) { __feedQueue.add(instruments) }
	}

	@Override
	public void stopPulling(List<Instrument> instruments)
	{
		synchronized (__feedQueue) { __feedQueue.remove(instruments)	}
	}

	@Override
	void stopPulling()
	{
		if(__feedQueue.size() > 0)
			stopPulling(__feedQueue.collect { it })
	}

	@Override
	public List<Instrument> getPulledInstruments()
	{
		return __feedQueue
	}

	private class Task implements Runnable
    {
        public void run()
        {
	        try
	        {
                List<Instrument> instruments
                synchronized (__feedQueue) { instruments = __feedQueue.collect{ it } }

				if(instruments.size() > 0)
				{
	                log.debug("Fetching snapshot for " + instruments.size() + " instruments")
	                Map<Instrument, Snapshot> ret = __activeTick.fetch(instruments, __attributes)

	                for(Instrument instrument in ret.keySet())
	                {
		                Snapshot snapshot = ret[instrument]
	                    Tick tick = new Tick()
	                    tick.lastTradePrice = snapshot.last
	                    tick.bidSize = snapshot.bidSize
	                    tick.bid = snapshot.bid
	                    tick.askSize = snapshot.askSize
	                    tick.ask = snapshot.ask
	                    tick.time = __clock.currentTimeMillis()
	                    tick.lastTradeTime = snapshot.lastTradeTime

		                // Adjust to local time
		                if(tick.lastTradeTime != null)
			                tick.lastTradeTime -= 7200000

	                    __tickListeners.each {it.tickUpdateNotification(instrument, tick)}
		                __snapshotListeners.each {
			                it.openUpdateNotification(instrument, snapshot.open)
			                it.ivolumeUpdateNotification(instrument, snapshot.ivolume)
			                it.rangeUpdateNotification(instrument, new BdRange(snapshot.low, snapshot.high))
		                }
	                }
				}
	        }
			catch(InterruptedException) {}
	        catch(Exception ex)
	        {
		        log.error("Error in ActiveTickPollingFeed", ex)
	        }
        }
    }
}

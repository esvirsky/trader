package com.queworx.brokers.ib

import groovy.util.logging.Log4j
import com.queworx.Sleeper
import com.queworx.Instrument

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

@Log4j
class IBShortableQueryTask implements Runnable
{
	public AtomicBoolean stop = new AtomicBoolean(false)
	public CopyOnWriteArrayList<Instrument> shortableList = []

	private IBFeedHandler __feedHandler
	private IBConfig __config
	private Sleeper __sleeper

	private Random __rand = new Random()
	private HashMap<Integer, Long> __tracking = [:]
	private HashMap<Integer, Long> __nextUpdate = [:]

	public IBShortableQueryTask(IBFeedHandler feedHandler, IBConfig config, Sleeper sleeper)
	{
		__feedHandler = feedHandler
		__config = config
		__sleeper = sleeper
	}

	public void run()
	{
		try
		{
			while(!stop.get())
			{
				for(Instrument instrument in shortableList)
				{
					if(__isTracking(instrument))
					{
						__stopTracking(instrument)
						continue
					}

					if(__shouldUpdate(instrument))
						__update(instrument)
				}

				__sleeper.sleep(1000)
			}
		}
		catch(Exception ex)
		{
			if(!(ex instanceof InterruptedException) && stop.get())
				log.error("IB Shortable task error", ex)
		}
	}

	public void clear()
	{
		shortableList.clear()
		__tracking.clear()
		__nextUpdate.clear()
	}

	private boolean __isTracking(Instrument instrument)
	{
		return __tracking.containsKey(instrument.id)
	}

	private void __stopTracking(Instrument instrument)
	{
		if(System.currentTimeMillis() - __tracking.get(instrument.id) >= 2000)
		{
//				log.debug("IB Stop pulling short info for: " + instrument.symbol)
			__feedHandler.stopPulling(instrument, IBFeedHandler.TypeShortableFeed)
			__nextUpdate[instrument.id] = System.currentTimeMillis() + (__rand.nextInt(300000) + 600000) // Update in 5-10 minutes
			__tracking.remove(instrument.id)
		}
	}

	private boolean __shouldUpdate(Instrument instrument)
	{
		if(__feedHandler.feedSize > __config.symbolLimit)
			return false

		return __nextUpdate[instrument.id] == null || System.currentTimeMillis() >= __nextUpdate[instrument.id]
	}

	private void __update(Instrument instrument)
	{
//			log.debug("IB Starting pulling short info for: " + instrument.symbol)
		__feedHandler.startPulling(instrument, IBFeedHandler.TypeShortableFeed)
		__tracking[instrument.id] = System.currentTimeMillis()
	}
}

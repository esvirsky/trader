package com.queworx.trader.tracking

import groovy.util.logging.Log4j
import org.joda.time.DateTime
import com.queworx.Clock
import com.queworx.Instrument
import com.queworx.Calendar
import com.queworx.Tick
import com.queworx.fetching.feed.TickListener

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps a record of seconds for a stock
 */
@Log4j
class SecondTracker implements TickListener
{
	private Calendar __calendar
	private Clock __clock
	private long __lookback
	private ConcurrentHashMap<Integer, TreeMap<Long, Tick>> __data = [:]

	private AtomicLong __startOfDayMillis = new AtomicLong(0)

	/**
	 * @param lookback keeps data up to lookback seconds
	 */
	public SecondTracker(Calendar calendar, Clock clock, int lookback)
	{
		__calendar = calendar
		__clock = clock
		__lookback = lookback
	}

	/**
	 * Gets the latest tick before or equal to time
	 */
	public Tick getLatestTick(Instrument instrument, Long time)
	{
		TreeMap<Long, Tick> map = __data[instrument.id]
		if(map == null)
			return null

		synchronized (map)
		{
			def entry = map.floorEntry(time)
			return entry?.value
		}
	}

	/**
	 * Get ticks from start (before or equal) to end
	 *
	 * @param instrument
	 * @param start - gets ticks right before or equal to this time
	 * @param end - gets ticks right before or equal to this time
	 * @return
	 */
	public List<Tick> getSecondTicks(Instrument instrument, Long start, Long end)
	{
		TreeMap<Long, Tick> map = __data[instrument.id]
		if(map == null)
			return []

		synchronized (map)
		{
			// include tick before start - if no tick on start
			start = map.floorKey(start) ?: start
			return map.subMap(start, true, end, true).values().toList()
		}
	}

	@Override
	void tickUpdateNotification(Instrument instrument, Tick tick)
	{
		try
		{
			if(tick.time == null)
				return

			if(!__data.containsKey(instrument.id))
				__data[instrument.id] = new TreeMap<Long, Tick>()

			TreeMap<Long, Tick> map = __data[instrument.id]
			synchronized (map)
			{
				__cleanup(map)

				Long lastKey =  map.size() > 0 ? map.lastKey() : null
				if(lastKey != null && lastKey != tick.time && __secondOfDay(lastKey) == __secondOfDay(tick.time))
					map.remove(lastKey)

				map.put(tick.time, tick)
			}
		}
		catch(Exception ex)
		{
			log.error("SecondTracker error", ex)
		}
	}

	private void __cleanup(TreeMap<Long, Tick> map)
	{
		if(map.size() == 0)
			return

		Long cutOff = __clock.currentTimeMillis() - __lookback * 1000l
		Long cutOffKey = map.floorKey(cutOff)
		if(cutOffKey == null)
			return

		def submap = map.headMap(cutOffKey)
		submap.keySet().toList().each { map.remove(it) }
	}

	private long __secondOfDay(Long time)
	{
		if(__startOfDayMillis.get() == 0)
			__startOfDayMillis.set(new DateTime().withMillisOfDay(0).getMillis())

		long dayMillis = time - __startOfDayMillis.get()
		return dayMillis/1000
	}
}

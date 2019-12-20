package com.queworx.trader.strategies


import com.queworx.Calendar
import com.queworx.Clock
import com.queworx.BdRange
import com.queworx.Instrument

abstract class Logger
{
	private Calendar __calendar
	private Clock __clock

	protected String __path
	protected FileWriter __ratingWriter

	public Logger(Calendar calendar, Clock clock)
	{
		__calendar = calendar
		__clock = clock
	}

	public void logRatings(HashMap<Instrument, Rating> ratings)
	{
		if (ratings.size() == 0)
			return

		__init()
		if (__ratingWriter == null)
		{
			__ratingWriter = new FileWriter(new File(__path + "/ratings.csv"), true)
			__ratingWriter.write("time,symbol," + __getRatingDebugHeader() + "\r\n")
		}

		String time = __clock.now().toString("kk:mm:ss")
		for (def entry in ratings)
			__ratingWriter.write(sprintf("%s,%s,%s\r\n", time, entry.key.symbol, entry.value.debugData))
		__ratingWriter.flush()
	}

	public void logSnapshotTracker(com.queworx.trader.tracking.SnapshotTracker tracker, List<Instrument> instruments, List<Rating> ratings)
	{
		__init()
		new File(__path + "/snapshotfeed_" + __clock.now().toString("kk_mm_ss") + ".csv").withWriter { writer ->
			writer.write("symbol,ex,open,ivolume,range\r\n")
			for(int i=0; i<instruments.size(); i++)
			{
				Instrument instrument = instruments[i]
				BdRange range = tracker.getRange(instrument)
				String rangeStr = range == null ? "" : range.low + "-" + range.high
				writer.write(sprintf("%s,%.4f,%.4f,%d,%s\r\n", instrument.symbol, ratings[i]?.ex, tracker.getOpen(instrument), tracker.getIVolume(instrument), rangeStr))
			}
		}
	}

	protected abstract String __getRatingDebugHeader()

	protected void __init()
	{
		if(__path == null)
		{
			__path = "logs/" + __calendar.today().toString()
			if (!new File(__path).exists())	new File(__path).mkdirs()
		}
	}
}

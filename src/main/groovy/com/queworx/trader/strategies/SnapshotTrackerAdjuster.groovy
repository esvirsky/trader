package com.queworx.trader.strategies


import groovy.util.logging.Log4j
import com.queworx.Clock
import com.queworx.Instrument
import com.queworx.fetching.feed.Feed
import com.queworx.trader.Config

/**
 * A tracker adjuster that first looks for the snapshot and then subscribes to the tick feed
 * Should be a common type of TrackerAdjuster
 */
@Log4j
abstract class SnapshotTrackerAdjuster implements TrackerAdjuster
{
	protected com.queworx.trader.tracking.SnapshotTracker __snapshotTracker
	protected Rater __rater
	protected Feed __tickFeed

	private Feed __snapshotFeed
	private com.queworx.trader.trading.BrokerSet __brokerSet
	private Logger __logger
	private Clock __clock
	private Config __config

	private long __lastRun = -1
	private long __runInterval = 1000
	private boolean __intervalAdjusted = false

	public SnapshotTrackerAdjuster(Feed snapshotFeed, Feed tickFeed, com.queworx.trader.tracking.SnapshotTracker snapshotTracker, com.queworx.trader.trading.BrokerSet brokerSet, Rater rater, Logger logger, Clock clock, Config config)
	{
		__snapshotFeed = snapshotFeed
		__tickFeed = tickFeed
		__snapshotTracker = snapshotTracker
		__brokerSet = brokerSet
		__rater = rater
		__logger = logger
		__clock = clock
		__config = config
	}

	@Override
	void adjustTrackers(DataSet dataSet)
	{
		if(__clock.currentTimeMillis() - __lastRun < __runInterval)
			return

		if(__snapshotFeed.pulledInstruments.size() > 0)
			__logger.logSnapshotTracker(__snapshotTracker, __snapshotFeed.pulledInstruments, __snapshotFeed.pulledInstruments.collect { __rater.rate(it, dataSet)} )

		List<Instrument> instruments = __snapshotFeed.pulledInstruments.findAll { __snapshotTracker.getOpen(it) > 0.1 }
		if(instruments.size() > 0)
		{
			__snapshotFeed.stopPulling(instruments.findAll { __shouldStopSnapshotTrack(it, dataSet) })
			log.debug("Got snapshot info for " + instruments.size() + " instruments. Still tracking " + __snapshotFeed.pulledInstruments.size() + " instruments")
		}

		// Get instruments to start tick tracking
		List<Instrument> track = instruments.findAll { __shouldTickTrack(it, dataSet) }
		if(__tickFeed.pulledInstruments.size() + track.size() > __config.maxTickTrack)
		{
			log.warn("Too many instruments to tick track")
			track = track[0..<Math.min(__config.maxTickTrack - __tickFeed.pulledInstruments.size(), 0)]
		}

		track.each { log.debug("Tracking tick feed for: " + it.symbol) }
		if(track.size() > 0)
		{
			__tickFeed.startPulling(track)
			__brokerSet.startPullingShortable(track)
		}

		// Get instruments to stop tick tracking. Don't unsubscribe the first one because it might be used in validation
		List<Instrument> stopTrack = __tickFeed.pulledInstruments.findAll {	__shouldStopTickTrack(it, dataSet) && it != __tickFeed.pulledInstruments[0] }
		stopTrack.each { log.debug("Un-tracking tick feed for: " + it.symbol) }
		if(stopTrack.size() > 0)
			__tickFeed.stopPulling(stopTrack)

		// Adjust polling interval and how frequently I adjust trackers
//		if(dataSet.getInstruments().size()*0.1 > __snapshotFeed.pulledInstruments.size() && !__intervalAdjusted)
//		{
//			__intervalAdjusted = true
//			__runInterval = 30000
//			((PollingFeed)__snapshotFeed).setPollingInterval(30000)
//		}
		__lastRun = __clock.currentTimeMillis()
	}

	protected abstract boolean __shouldStopSnapshotTrack(Instrument instrument, DataSet dataSet)
	protected abstract boolean __shouldTickTrack(Instrument instrument, DataSet dataSet)
	protected abstract boolean __shouldStopTickTrack(Instrument instrument, DataSet dataSet)
}

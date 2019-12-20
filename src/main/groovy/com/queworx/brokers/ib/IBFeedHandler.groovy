package com.queworx.brokers.ib

import com.ib.client.EClientSocket
import groovy.util.logging.Log4j
import com.queworx.CommandRunningQueue
import com.queworx.Instrument

/**
 * Thread Safe
 */
@Log4j
class IBFeedHandler
{
	public static final int TypeTickFeed = 0
	public static final int TypeShortableFeed = 1

	private CommandRunningQueue __commandRunningQueue
	private EClientSocket __clientSocket
	private IBDatastore __datastore

	private HashSet<Instrument> __tickFeed = new HashSet<Instrument>()
	private HashSet<Instrument> __shortableFeed = new HashSet<Instrument>()

	public IBFeedHandler(EClientSocket clientSocket, IBDatastore datastore, CommandRunningQueue commandRunningQueue)
	{
		__clientSocket = clientSocket
		__datastore = datastore
		__commandRunningQueue = commandRunningQueue
	}

	public synchronized void startPulling(Instrument instrument, int feedType)
	{
		if(!__tickFeed.contains(instrument) && !__shortableFeed.contains(instrument))
		{
//				log.debug("IB PULL START " + instrument.symbol)
			__commandRunningQueue.addCommand(__clientSocket.&reqMktData, [__datastore.createTickerId(instrument), __datastore.createContract(instrument), "236", false])
		}

		(feedType == TypeTickFeed ? __tickFeed : __shortableFeed).add(instrument)
	}

	public synchronized void stopPulling(Instrument instrument, int feedType)
	{
		(feedType == TypeTickFeed ? __tickFeed : __shortableFeed).remove(instrument)
		if(!__tickFeed.contains(instrument) && !__shortableFeed.contains(instrument))
		{
			__commandRunningQueue.addCommand(__clientSocket.&cancelMktData, [__datastore.getTickerId(instrument)])
//				log.debug("IB PULL STOP " + instrument.symbol)
		}
	}

	public synchronized int getFeedSize()
	{
		return __tickFeed.size() + __shortableFeed.size()
	}

	public synchronized void clear()
	{
		__tickFeed.clear()
		__shortableFeed.clear()
	}
}

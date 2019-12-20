package com.queworx.brokers.mbt

import groovy.util.logging.Log4j
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import com.queworx.HttpBuilderFactory
import com.queworx.Instrument

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

@Log4j
class MbtShortableTask implements Runnable
{
	public AtomicBoolean stop = new AtomicBoolean(false)
	public CopyOnWriteArrayList<Instrument> shortableList = []

	private MbtDatastore __datastore
	private HttpBuilderFactory __httpBuilderFactory
	private SymbolFormatter __symbolFormatter
	private String __url

	private HTTPBuilder __requestHttpBuilder
	private Random __rand = new Random()
	private HashMap<Integer, Long> __tracking = [:]
	private HashMap<Integer, Long> __nextUpdate = [:]

	public MbtShortableTask(MbtDatastore datastore, HttpBuilderFactory httpBuilderFactory, SymbolFormatter symbolFormatter, String url)
	{
		__datastore = datastore
		__httpBuilderFactory = httpBuilderFactory
		__symbolFormatter = symbolFormatter
		__url = url
	}

	@Override
	void run()
	{
		try
		{
			while(!stop.get())
			{
				List<Instrument> fetch = []
				for(Instrument instrument in shortableList)
				{
					if(__isTracking(instrument))
					{
						__stopTracking(instrument)
						continue
					}

					if(__shouldUpdate(instrument))
						fetch.add(instrument)
				}

				if(fetch.size() > 20)
					fetch = fetch[0..<20]

				if(fetch.size() > 0)
					__requestShortableInfo(fetch)

				fetch.each { __update(it) }
				Thread.sleep(Math.max(fetch.size().intdiv(2), 1) * 1000)
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
			__nextUpdate[instrument.id] = System.currentTimeMillis() + (__rand.nextInt(600000) + 900000) // Update in 10-15 minutes
			__tracking.remove(instrument.id)
		}
	}

	private boolean __shouldUpdate(Instrument instrument)
	{
		return __nextUpdate[instrument.id] == null || System.currentTimeMillis() >= __nextUpdate[instrument.id]
	}

	private void __update(Instrument instrument)
	{
		__tracking[instrument.id] = System.currentTimeMillis()
	}

	private void __requestShortableInfo(List<Instrument> instruments)
	{
		instruments.each {	__datastore.symbols[__symbolFormatter.fromStandard(it)] = it }
		String url = sprintf("%s/request?action=quote&symbols=%s", __url, instruments.collect { __symbolFormatter.fromStandard(it) }.join(","))
		__getRequestHttpBuilder().get(uri: url, contentType: ContentType.TEXT)
	}

	private HTTPBuilder __getRequestHttpBuilder()
	{
		if(__requestHttpBuilder == null)
			__requestHttpBuilder = __httpBuilderFactory.createHTTPBuilder()

		return __requestHttpBuilder
	}
}

package com.queworx.fetching.activetick.http

import groovy.util.logging.Log4j
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import org.apache.http.conn.ConnectTimeoutException
import org.joda.time.DateTime
import com.queworx.Clock
import com.queworx.Sleeper
import com.queworx.Instrument
import com.queworx.fetching.activetick.SnapAttribute
import com.queworx.fetching.activetick.Snapshot
import com.queworx.fetching.activetick.SymbolFormatter

class ActiveTickSnapshotFetcher
{
	private SymbolFormatter __atFormatter
	private HTTPBuilder __httpBuilder
	private String __url
	private Clock __clock
	private Sleeper __sleeper

	private int __fetchCount
	private int __fetchTime

	public ActiveTickSnapshotFetcher(HTTPBuilder httpBuilder, String url, SymbolFormatter atFormatter, Clock clock, Sleeper sleeper)
	{
		__atFormatter = atFormatter
		__httpBuilder = httpBuilder
		__url = url
		__clock = clock
		__sleeper = sleeper
	}

	public HashMap<Instrument, Snapshot> fetch(List<Instrument> instruments, List<SnapAttribute> attributes, List<String> errors = [])
	{
		HashMap<Instrument, Snapshot> ret = [:]

		// Attributes come back ordered, so I need to sort how I pass it in too
		attributes = attributes.sort { it.value() }

		// I need to filter out instruments that will not be on activetick
		instruments = instruments.findAll {
			!it.symbol.contains("-") && !it.symbol.contains("+") && !it.symbol.contains("*")
		}

		// Nothing to get
		if(instruments.size() == 0)
			return ret

		// ActiveTick periodically re-logs in and times out, so I have to do a double get
		__httpBuilder.getClient().getParams().setParameter("http.connection.timeout", new Integer(5000))
		__httpBuilder.getClient().getParams().setParameter("http.socket.timeout", new Integer(5000))

		Map<String, Instrument> instrumentLookup = instruments.collectEntries { [__atFormatter.fromStandard(it), it] }
		(__fetchTime, __fetchCount) = [0, 0]
		for (List<String> subset in instrumentLookup.keySet().asList().collate(2000))
		{
			int sleepTime = __fetchTime == 0 ? 0 : 5500 - (__clock.currentTimeMillis() - __fetchTime)
			if (sleepTime > 0)
			{
				println "Sleeping for " + sleepTime
				__sleeper.sleep(sleepTime)
			}

			println "Fetching at: " + __clock.now()
			List<String> lines = __fetch(subset, attributes)
			__parse(ret, instrumentLookup, lines, attributes, errors)
		}

		return ret
	}

	private List<String> __fetch(List<String> symbols, List<SnapAttribute> attributes)
	{
		String url = sprintf("%s/quoteData?symbol=%s&field=1+%s", __url, symbols.join("+"), attributes.collect {
			it.value()
		}.join("+"))

		List<String> lines = null
		// Retry once in case of a timeout
		try
		{
			lines = __httpBuilder.get(uri: url, contentType: ContentType.TEXT).readLines()
		}
		catch (ConnectTimeoutException | SocketTimeoutException ex)
		{
			println "Timed out connection " + __clock.now() + ", retrying"
			Thread.sleep(5000)
			println "Attempt 2 at: " + __clock.now()
			lines = __httpBuilder.get(uri: url, contentType: ContentType.TEXT).readLines()
		}

		__fetchCount += 1
		__fetchTime = __clock.currentTimeMillis()
		return lines
	}

	private void __parse(LinkedHashMap<Instrument, Snapshot> ret, Map<String, Instrument> instrumentLookup, List<String> lines, List<SnapAttribute> attributes, List<String> errors)
	{
		for (String line in lines)
		{
			// ActiveTick returns a bunch of BS I have to filter
			if (line == "" || line.size() < 5 || line =~ /[^\x20-\x7E]/)
				continue

			try
			{
				Snapshot snapshot = new Snapshot()
				List<String> parts = line.split(",")
				if (parts.size() < 3) //More ActiveTick BS filtering
					continue

				Instrument instrument = instrumentLookup[parts[0]]
				assert parts[1] as int == 1 // Status check

				for (int i in 0..<attributes.size())
				{
					//afield = parts[i*4 + 2 + 0]
					int astatus = parts[i * 4 + 2 + 1] as int
					//atype = parts[i*4 + 2 + 2]
					String adata = parts[i * 4 + 2 + 3]

					if (astatus != 1)
						throw new Exception(sprintf("Bad attribute status %s %s %s", attributes[i], adata, astatus))

					__parseAttribute(snapshot, adata, attributes[i])
				}

				ret[instrument] = snapshot
			}
			catch (Exception ex)
			{
				errors.add(sprintf("Couldn't parse ActiveTick line: %s, error: %s", line, ex.message))
			}
		}
	}

	private void __parseAttribute(Snapshot snapshot, String adata, SnapAttribute attribute)
	{
		if (attribute == SnapAttribute.ASK) snapshot.ask = adata as BigDecimal
		if (attribute == SnapAttribute.ASK_SIZE) (snapshot.askSize = adata as int) * 100
		if (attribute == SnapAttribute.BID) snapshot.bid = adata as BigDecimal
		if (attribute == SnapAttribute.BID_SIZE) (snapshot.bidSize = adata as int) * 100
		if (attribute == SnapAttribute.LAST) snapshot.last = adata as BigDecimal
		if (attribute == SnapAttribute.OPEN) snapshot.open = adata as BigDecimal
		if (attribute == SnapAttribute.PREV_CLOSE) snapshot.prevClose = adata as BigDecimal
		if (attribute == SnapAttribute.IVOLUME) snapshot.ivolume = adata as int
		if (attribute == SnapAttribute.NAME) snapshot.name = adata
		if (attribute == SnapAttribute.LAST_TRADE_DATETIME) snapshot.lastTradeTime = __parseTime(adata)
		if (attribute == SnapAttribute.LOW_PRICE) snapshot.low = adata as BigDecimal
		if (attribute == SnapAttribute.HIGH_PRICE) snapshot.high = adata as BigDecimal
	}

	private Long __parseTime(String value)
	{
		return new DateTime(Integer.parseInt(value[0..3]), Integer.parseInt(value[4..5]), Integer.parseInt(value[6..7]),
				Integer.parseInt(value[8..9]), Integer.parseInt(value[10..11]), Integer.parseInt(value[12..13]),
				Integer.parseInt(value[14..16])).getMillis()
	}
}

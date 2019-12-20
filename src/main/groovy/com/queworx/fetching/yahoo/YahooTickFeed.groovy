package com.queworx.fetching.yahoo

import groovy.util.logging.Log4j
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import com.queworx.Clock
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.fetching.feed.Feed
import com.queworx.fetching.feed.FeedQueue
import com.queworx.fetching.feed.TickListener
import java.util.Base64

@Log4j
class YahooTickFeed implements Feed
{
	@groovy.beans.ListenerList
	private java.util.List<TickListener> __tickUpdateListeners = Collections.synchronizedList([])

	private HTTPBuilder __httpBuilder

	private BackgroundFetcher __thread
	private FeedQueue __feedQueue
	private boolean __running
	private YahooTickFeed __this = this

	YahooTickFeed(Clock clock, Integer capacity = null)
	{
		super(clock, capacity)
	}

	@Override
	void connect()
	{
		__httpBuilder = new HTTPBuilder()

		File cookie = new File("yahoo_cookie")
		if (cookie.exists() && System.currentTimeMillis() - cookie.lastModified() < 3600000)
		{
			log.debug "loading cookie"
			cookie.withObjectInputStream { __httpBuilder.client.cookieStore = it.readObject() }
		}
		else
		{
			log.debug "saving cookie"
			__httpBuilder.post(uri: "https://login.yahoo.com/config/login", body: [login: new String(Base64.getDecoder().decodeBuffer("x")), passwd: new String(Base64.getDecoder().decodeBuffer("x"))])
			cookie.withObjectOutputStream { it << __httpBuilder.client.cookieStore }
		}

		__running = true
		__feedQueue = new FeedQueue()
		__thread = new BackgroundFetcher()
		__thread.start()
	}

	@Override
	void disconnect()
	{
		__thread.interrupt()
		synchronized (__this) { while(__running) { try{ __this.wait() } catch(InterruptedException ex){} } }

		__httpBuilder.shutdown()
	}

	@Override
	public void startPulling(List<Instrument> instruments)
	{
		if (__httpBuilder == null)
			connect()

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
		stopPulling(__feedQueue.collect { it })
	}

	@Override
	List<Instrument> getPulledInstruments()
	{
		return __feedQueue
	}

	private class BackgroundFetcher extends Thread
	{
		private boolean __interrupt = false

		public void run()
		{
			DateTimeFormatter formatter = DateTimeFormat.forPattern("h:ma")

			while(!__interrupt)
			{
				Map<String, Instrument> map = [:]
				synchronized (__feedQueue) { map = __feedQueue.collectEntries {[it.symbol.replace(".A", "-A").replace(".B", "-B"), it]} }

				// Gives the session something to do to keep it alive
				if (map.size() == 0)
					map.put("SPY", new Instrument(symbol: "dummy"))

				for(List sub in map.keySet().asList().collate(200))
				{
					String url = sprintf("http://download.finance.yahoo.com/d/quotes.csv?s=%s&f=sl1b6baa5ot1v", sub.join(","))
					log.debug "Fetching yahoo: " + url
				    __httpBuilder.get(uri: url, contentType : ContentType.TEXT).eachLine{
//						println it
//					new File("E:/dev/rise/test/risetrader/yahoo" + (sub[0] == "FCTY" ? "1.txt" : "2.txt")).eachLine {
//					new File("E:/dev/rise/test/risetrader/filtered.txt").eachLine {
						if(it.contains("N/A,N/A"))
							return

						it = it.replaceAll(/(,\d+(,\d{3})+,)/, {"," + it[0].replace(",", "") + ","}) //Used for the bid/ask size commas
						List<String> parts = it.trim().split(",")

					    Instrument instrument = map.get(parts[0][1..-2])

					    Tick tick = new Tick()
					    tick.lastTradePrice = parts[1].toBigDecimal()
					    tick.bidSize = parts[2].toInteger()
					    tick.bid = parts[3].replace("&nbsp;+&nbsp;", "").replace("&nbsp;-&nbsp;", "").toBigDecimal()
					    tick.ask = parts[4].replace("&nbsp;+&nbsp;", "").replace("&nbsp;-&nbsp;", "").toBigDecimal()
					    tick.askSize = parts[5].toInteger()
					    tick.open = parts[6].isBigDecimal() ? parts[6].toBigDecimal() : null
					    tick.lastTradeTime = parts[7][1..-2] == "N/A" ? null : formatter.parseLocalTime(parts[7][1..-2]).toDateTimeToday().getMillis()
					    tick.lastTradeVolume = parts[8].toInteger()
					    tick.time = System.currentTimeMillis()

					    tick.lastTradePrice = tick.lastTradePrice < 0.01 ? null : tick.lastTradePrice
					    tick.bid = tick.lastTradePrice < 0.01 ? null : tick.lastTradePrice
					    tick.ask = tick.lastTradePrice < 0.01 ? null : tick.lastTradePrice

					    synchronized (__tickUpdateListeners) { __tickUpdateListeners.each {it.tickUpdateNotification(instrument, tick)} }
				    }

					try{ Thread.sleep(1000) } catch(InterruptedException) {}
				}
			}

			Thread.sleep(100)
			__running = false
			synchronized (__this) { __this.notifyAll() }
		}

		public void interrupt()
		{
			__interrupt = true;
			super.interrupt();
		}
	}
}

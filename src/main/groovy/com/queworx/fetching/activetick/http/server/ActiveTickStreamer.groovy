package com.queworx.fetching.activetick.http.server

import at.feedapi.ActiveTickStreamListener
import at.feedapi.Session
import at.shared.ATServerAPIDefines
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import com.queworx.ErrorHandler

import java.util.concurrent.CopyOnWriteArrayList

class ActiveTickStreamer extends ActiveTickStreamListener
{
	private SymbolParser __symbolParser
	private ErrorHandler __errorHandler
	private long __lastPrint = 0
	private int __symbolCount = 0

	private BufferedWriter __logger = new BufferedWriter(new FileWriter(LocalDate.now().toString() + "_stream.log"))
	private String __lastQuote

	@groovy.beans.ListenerList
	private CopyOnWriteArrayList<StreamListener> __streamListener = new CopyOnWriteArrayList<StreamListener>()

	public ActiveTickStreamer(Session session, SymbolParser symbolParser, ErrorHandler errorHandler)
	{
		super(session, false)
		__symbolParser = symbolParser
		__errorHandler = errorHandler
	}

	public void setStreamedSymbolCount(int symbolCount)
	{
		__symbolCount = symbolCount
	}

	@Override
	public void OnATStreamQuoteUpdate(ATServerAPIDefines.ATQUOTESTREAM_QUOTE_UPDATE update)
	{
		try
		{
			if(System.currentTimeMillis() - __lastPrint > 20000)
			{
				println( (new LocalTime()).toString("HH:mm:ss") + " Streaming Quotes (count: " + __symbolCount + ")")
				__lastPrint = System.currentTimeMillis()
			}

			String symbol = __symbolParser.parseSymbol(update.symbol)
			org.joda.time.DateTime dt = new org.joda.time.DateTime(update.quoteDateTime.year, update.quoteDateTime.month, update.quoteDateTime.day, update.quoteDateTime.hour, update.quoteDateTime.minute, update.quoteDateTime.second, update.quoteDateTime.milliseconds)
			String output = sprintf("q,%s,%d,%.4f,%.4f,%d,%d\r\n", symbol, update.bidSize, update.bidPrice.price, update.askPrice.price, update.askSize, dt.getMillis())
			__logger.write(System.currentTimeMillis() + "," + (System.currentTimeMillis() + 7200000 - dt.getMillis()) + "," + output)

			// frequent duplicates
			if(output == __lastQuote) return
			__lastQuote == output

			synchronized (__streamListener) { __streamListener.each {it.streamData(output)} }
		}
		catch(Exception ex)
		{
			__errorHandler.handleError("OnATStreamQuoteUpdate error", ex)
		}
	}

	@Override
	public void OnATStreamTradeUpdate(ATServerAPIDefines.ATQUOTESTREAM_TRADE_UPDATE update)
	{
		try
		{
			String symbol = __symbolParser.parseSymbol(update.symbol)
			org.joda.time.DateTime dt = new org.joda.time.DateTime(update.lastDateTime.year, update.lastDateTime.month, update.lastDateTime.day, update.lastDateTime.hour, update.lastDateTime.minute, update.lastDateTime.second, update.lastDateTime.milliseconds)
			String output = sprintf("t,%s,%.4f,%d,%d\r\n", symbol, update.lastPrice.price, update.lastSize, dt.getMillis())
			__logger.write(System.currentTimeMillis() + "," + (System.currentTimeMillis() + 7200000 - dt.getMillis()) + "," + output)
			synchronized (__streamListener) { __streamListener.each {it.streamData(output)} }
		}
		catch(Exception ex)
		{
			__errorHandler.handleError("OnATStreamTradeUpdate error", ex)
		}
	}
}

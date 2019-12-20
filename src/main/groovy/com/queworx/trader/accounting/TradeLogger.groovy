package com.queworx.trader.accounting

import groovy.util.logging.Log4j
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import com.queworx.TradeDirection
import com.queworx.brokers.Broker
import com.queworx.brokers.Position
import com.queworx.brokers.ib.IBBroker

import java.math.MathContext
import java.math.RoundingMode

@Log4j
class TradeLogger
{
	private static DateTimeFormatter Formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS")

	private File __file

	public TradeLogger(File file)
	{
		__file = file
	}

	public void logOpening(Position position)
	{
		// Todo: remove the try catch as soon as i see it working correctly
		try
		{
			__file.append(__getLine(position.instrument.symbol, position.direction, position.opened, position.avgPrice, position.quantity, position.broker))
		}
		catch(Exception ex)
		{
			log.error("error logging open: " + ex.message)
		}
	}

	/**
	 * Can log a partial closing if quantity is less than initialPosition.quantity
	 */
	public void logClosing(Position initialPosition, Integer quantity, DateTime dt, BigDecimal price)
	{
		// Todo: remove the try catch as soon as i see it working correctly
		try
		{
			String key = initialPosition.instrument.symbol + "," + initialPosition.direction.name() + "," + Formatter.print(initialPosition.opened) + ",,"
			List<String> lines = __file.exists() ? __file.readLines() : []
			lines.removeAll { it.startsWith(key) || it == "" }

			lines.add(__getLine(initialPosition.instrument.symbol, initialPosition.direction, initialPosition.opened, initialPosition.avgPrice, quantity, initialPosition.broker, dt, price))
			if(initialPosition.quantity > quantity)
				lines.add(__getLine(initialPosition.instrument.symbol, initialPosition.direction, initialPosition.opened, initialPosition.avgPrice, initialPosition.quantity - quantity, initialPosition.broker))

			lines.sort(true) { it.split(",")[2] }
			__file.write(lines.join("\r\n") + "\r\n")
		}
		catch(Exception ex)
		{
			log.error("error logging close: " + ex.message)
		}
	}

	private String __getLine(String symbol, TradeDirection direction, DateTime openDt, BigDecimal openPrice, int quantity, Broker broker, DateTime closeDt = null, BigDecimal closePrice = null)
	{
		StringBuilder sb = new StringBuilder()
		sb.append(symbol)
		sb.append("," + direction.name())
		sb.append("," + Formatter.print(openDt))
		sb.append("," + (closeDt == null ? "" : Formatter.print(closeDt)))
		sb.append("," + openPrice)
		sb.append("," + (closePrice == null ? "" : closePrice))
		sb.append("," + quantity)
		sb.append("," + __calculateCommission(broker, quantity)*2)
		return sb.toString() + "\r\n"
	}

	private BigDecimal __calculateCommission(Broker broker, int quantity)
	{
		if(broker instanceof IBBroker)
		{
			return new BigDecimal(Math.max(quantity*0.005d, 1d), new MathContext(2))
		}
		else
		{
			return 5
		}
	}
}

package com.queworx.trader.accounting

import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import com.queworx.InstrumentDatastore
import com.queworx.TradeDirection
import com.queworx.brokers.Position
import com.queworx.Instrument
import com.queworx.trader.trading.BrokerSet

class FilePortfolio
{
	private static DateTimeFormatter Formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS")
	private static DateTimeFormatter LocalDateFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

	private File __file
	private InstrumentDatastore __instrumentFactory
	private BrokerSet __brokerSet

	public FilePortfolio(File file, InstrumentDatastore instrumentFactory, BrokerSet brokerSet)
	{
		__file = file
		__instrumentFactory = instrumentFactory
		__brokerSet = brokerSet
	}

	public List<Position> load()
	{
		if(!__file.exists())
			return []

		return __file.readLines().findAll { it != "" }.collect {
			String[] parts = it.split(",")
			Instrument instrument = __instrumentFactory.getOrCreateInstrument(parts[0])
			int quantity = Integer.valueOf(parts[1])
			TradeDirection direction = TradeDirection.valueOf(parts[2])
			BigDecimal avgPrice = new BigDecimal(parts[3])

			Position position = new Position(instrument, quantity, avgPrice, direction)
			position.opened = Formatter.parseDateTime(parts[4])
			position.filledPercent = Float.valueOf(parts[5])
			position.broker = __brokerSet.brokers.find { it.class.name == parts[6] }
			if(parts.size() > 7)
				position.extended = LocalDateFormatter.parseLocalDate(parts[7])
			return position
		}
	}

	public void add(Position position)
	{
		StringBuilder sb = new StringBuilder()
		sb.append(position.instrument.symbol)
		sb.append("," + position.quantity)
		sb.append("," + position.direction.name())
		sb.append("," + position.avgPrice)
		sb.append("," + Formatter.print(position.opened))
		sb.append("," + position.filledPercent)
		sb.append("," + position.broker.class.name)
		if(position.extended != null)
			sb.append("," + LocalDateFormatter.print(position.extended))

		__file.append(sb.toString() + "\r\n")
	}

	public void remove(Position position)
	{
		String key = position.instrument.symbol + "," + position.quantity + "," + position.direction.name() + "," + position.avgPrice + "," + Formatter.print(position.opened)
		List<String> lines = __file.exists() ? __file.readLines() : []
		lines.removeAll { it.startsWith(key) || it == "" }
		__file.write(lines.join("\r\n") + "\r\n")
	}

}

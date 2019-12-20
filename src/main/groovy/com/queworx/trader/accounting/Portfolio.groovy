package com.queworx.trader.accounting

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import groovy.util.logging.Log4j
import org.joda.time.DateTime
import com.queworx.brokers.*
import com.queworx.Instrument

/**
 * Thread safe
 */
@Log4j
class Portfolio implements OrderListener
{
	private FilePortfolio __dbPortfolio
	private TradeLogger __tradeLogger

	private Multimap<Instrument, Position> __positions = new ArrayListMultimap<Instrument, Position>()
	private Object __lock = new Object()

	public Portfolio(FilePortfolio dbPortfolio, TradeLogger tradeLogger)
	{
		__dbPortfolio = dbPortfolio
		__tradeLogger = tradeLogger
	}

	public void loadDb()
	{
		synchronized (__lock) { __dbPortfolio.load().each { __positions.put(it.instrument, it) } }
	}

	public void addPosition(Position position)
	{
		synchronized (__lock)
		{
			__positions.put(position.instrument, position)
			if (__dbPortfolio != null)
				__dbPortfolio.add(position)
		}
	}

	public void removePosition(Position position)
	{
		synchronized (__lock)
		{
			__positions.get(position.instrument).remove(position)
			if (__dbPortfolio != null)
				__dbPortfolio.remove(position)
		}
	}

	public void replacePosition(Position position1, Position position2)
	{
		synchronized (__lock)
		{
			if (__dbPortfolio != null)
			{
				__dbPortfolio.remove(position1)
				__dbPortfolio.add(position2)
			}

			__positions.get(position1.instrument).remove(position1)
			__positions.put(position1.instrument, position2)
		}
	}

	public List<Position> getPositions(Instrument instrument)
	{
		synchronized (__lock) { return __positions.get(instrument) ?: [] }
	}

	public List<Position> getPositions()
	{
		synchronized (__lock) { return __positions.values().toList() }
	}

	@Override
	void orderUpdateNotification(Order order, OrderStatus status, Broker broker)
	{
		if(!status.active && status.filledQuantity > 0)
		{
			log.debug("Entered into portfolio update notification: " + order.instrument.symbol)
			Position position = new Position(order.instrument, status.filledQuantity, status.filledAvgPrice, order.direction)
			position.filledPercent = position.amount/(order.price * order.quantity)
			position.opened = new DateTime()
			position.broker = broker

			synchronized (__lock)
			{
				Position reverse = __positions.get(order.instrument)?.find{it.direction != position.direction && it.quantity == order.quantity && it.broker == broker}
				log.debug("Reverse Position is: " + order.instrument.symbol + " " + reverse)
				if(reverse == null)
				{
					addPosition(position)
					__tradeLogger.logOpening(position)
					return
				}

				log.debug("Removing reverse position: " + order.instrument.symbol + " " + reverse)
				removePosition(reverse)
				__tradeLogger.logClosing(reverse, position.quantity, DateTime.now(), position.avgPrice)

				// We were able to only partially close the reverse position
				if(reverse.quantity != position.quantity)
				{
					log.debug("Adding an adjusted reverse position: " + order.instrument.symbol + " " + reverse)
					Float fullFillAmount = (reverse.amount/reverse.filledPercent)
					reverse.quantity -= position.quantity
					reverse.filledPercent = reverse.amount/fullFillAmount
					addPosition(reverse)
				}
			}
		}
	}
}

package com.queworx.trader.strategies.five_percent_drop

import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.TradeDirection
import com.queworx.brokers.Order
import com.queworx.brokers.Position
import com.queworx.fetching.feed.Feed
import com.queworx.trader.Config
import com.queworx.trader.accounting.Portfolio
import com.queworx.trader.strategies.DataSet
import com.queworx.trader.strategies.Logger
import com.queworx.trader.strategies.Rating
import com.queworx.trader.strategies.Strategy
import com.queworx.trader.tracking.TickTracker
import com.queworx.trader.trading.BrokerSet
import com.queworx.trader.trading.TradePurpose
import groovy.util.logging.Log4j

@Log4j
class FivePercentDropStrategy implements Strategy
{
    private FivePercentDropRater __rater
    private Feed __tickFeed
    private TickTracker __tickTracker
    private Portfolio __portfolio
    private Logger __logger
    private BrokerSet __brokerSet
    private Config __config

    private DataSet __dataSet
    private HashMap<Instrument, Rating> __ratings

    public FivePercentDropStrategy(Feed tickFeed, TickTracker tickTracker, Portfolio portfolio, Logger logger, FivePercentDropRater rater, BrokerSet brokerSet, Config config)
    {
        __rater = rater
        __tickFeed = tickFeed
        __tickTracker = tickTracker
        __portfolio = portfolio
        __logger = logger
        __brokerSet = brokerSet
        __config = config
    }

    @Override
    public void setDataSet(DataSet dataSet)
    {
        __dataSet = dataSet
    }

    @Override
    void onPreDataLoad()
    {

    }

    @Override
    void onPreOpen()
    {
        /* I want to start getting information on all instruments that I couldn't sell the day before */
        HashSet<Instrument> instruments = new HashSet<Instrument>(__dataSet.instruments)
        instruments.addAll(__portfolio.getPositions()*.instrument)

        __tickFeed.startPulling(instruments.collect { it })
        __brokerSet.startPullingShortable(instruments.collect { it })
    }

    @Override
    void onPostOpen()
    {
    }

    @Override
    void onLoopStart()
    {

    }

    @Override
    void onTradingLoopStart()
    {
        // I want to log all the ratings for today when my strategy starts
        __ratings = __tickFeed.pulledInstruments.collectEntries { [it, __rater.rate(it, __dataSet)] }
        __logger.logRatings(__ratings)
    }

    @Override
    void onEndTrading()
    {
        log.debug("Unsubscribing from all symbols")
        __tickFeed.stopPulling()
        __brokerSet.stopPullingShortable()
    }

    @Override
    boolean shouldEndTrading()
    {
        return false
    }

    @Override
    List<Position> getPositionsToOpen()
    {
        List<Position> positions = []
        for(Instrument instrument in __ratings.keySet().sort(false) { __ratings[it].ex }.reverse())
        {
            if(!__ratings[instrument].shouldTrade)
                continue

            Tick tick = __tickTracker.getLatestTick(instrument)
            positions.add(new Position(instrument, (__config.tradeAmount/tick.avgPrice).intValue(), tick.avgPrice, TradeDirection.Long))
            positions.add(new Position(instrument, (__config.tradeAmount/tick.avgPrice).intValue(), tick.avgPrice, TradeDirection.Short))
        }

        return positions
    }

    @Override
    List<Position> getPositionsToClose()
    {
        return __portfolio.getPositions()
    }

    @Override
    Order adjustOrderForResubmit(Order order, TradePurpose purpose)
    {
        Tick tick = __tickTracker.getLatestTick(order.instrument)
        order.price = order.direction == TradeDirection.Long ? tick.bid + new BigDecimal("0.01") : tick.ask - new BigDecimal("0.01")
        return order
    }

    @Override
    Order createOrderForSubmit(Position position, TradePurpose purpose)
    {
        Tick tick = __tickTracker.getLatestTick(position.instrument)
        log.debug("Create order tick: " + position.instrument.symbol + " " + position.direction.name() + " " + tick)
        BigDecimal price = position.direction == TradeDirection.Long ? tick.bid + new BigDecimal("0.01") : tick.ask - new BigDecimal("0.01")
        return new Order(position.instrument, position.quantity, price, position.direction, __config.getLiveDuration(purpose), __config.maxCommission, true)
    }
}

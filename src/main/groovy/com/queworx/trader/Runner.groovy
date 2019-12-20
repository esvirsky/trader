package com.queworx.trader

import com.queworx.trader.accounting.FilePortfolio
import com.queworx.trader.accounting.OrderTracker
import com.queworx.trader.accounting.Portfolio
import com.queworx.trader.accounting.TradeLogger
import com.queworx.trader.strategies.Controller
import com.queworx.trader.strategies.ControllerArg
import com.queworx.trader.strategies.StrategyClock
import com.queworx.trader.strategies.five_percent_drop.FivePercentDropDatabase
import com.queworx.trader.strategies.five_percent_drop.FivePercentDropRater
import com.queworx.trader.strategies.five_percent_drop.FivePercentDropStrategy
import com.queworx.trader.strategies.five_percent_drop.FivePercentDropTrackerAdjuster
import com.queworx.trader.strategies.five_percent_drop.FivePercentDropTradeEvaluator
import com.queworx.trader.strategies.five_percent_drop.FivePercentDropLogger
import com.queworx.trader.tracking.TickTracker
import com.queworx.trader.trading.BrokerSet
import com.queworx.trader.trading.OrderAdjuster
import com.queworx.trader.trading.OrderSafetyGuard
import com.queworx.trader.trading.Trader
import groovy.util.logging.Log4j
import org.apache.log4j.PropertyConfigurator
import com.queworx.brokers.ib.IBBroker
import com.queworx.brokers.ib.IBFactory
import com.queworx.brokers.mbt.MbtBroker
import com.queworx.brokers.mbt.MbtFactory
import com.queworx.fetching.activetick.ActiveTickStreamingFeed
import com.queworx.fetching.activetick.SymbolFormatter
import com.queworx.fetching.feed.Feed
import com.queworx.fetching.feed.HttpStreamingFeed
import com.queworx.*

@Log4j
class Runner implements ErrorHandler, Thread.UncaughtExceptionHandler
{
    public static void main(String[] args)
    {
        long time = System.currentTimeMillis()

	    Properties logProperties = new Properties();
	    logProperties.load(new FileInputStream("log4j.xml"));
	    PropertyConfigurator.configure(logProperties);

	    Runner runner = new Runner()
	    runner.run(args)

        println "\n\nRun Time: " + (System.currentTimeMillis() - time)/1000.0f + " seconds"
    }

	public void run(String[] args)
	{
		Thread.setDefaultUncaughtExceptionHandler(this)

		Config config = new Config(new Properties())
		if(new File("config.properties").exists())
			config.load(new File("config.properties"))
		else
			config.load(new File("five_percent_drop.properties"))

		// Wrapping these things made it easy for me to unit test
		Calendar calendar = new Calendar()
		Clock clock = new Clock()
		Sleeper sleeper = new Sleeper()

		if(config.trader == "five_percent_drop")
			__runFivePercentDrop(config, calendar, clock, sleeper)
		// I removed my other strategies and traders
	}

	public void uncaughtException(Thread thread, Throwable throwable)
	{
		log.error("Uncaught Exception", throwable)
		System.exit(0)
	}

	public void handleError(String message, Throwable throwable)
	{
		log.error(message, throwable)
		System.exit(0)
	}

	private void __runFivePercentDrop(Config config, Calendar calendar, Clock clock, Sleeper sleeper)
	{
		ControllerArg arg = new ControllerArg()
		arg.config = config
		arg.clock = clock
		arg.sleeper = sleeper
		arg.strategyClock = new StrategyClock(sleeper, clock, config)

		// Insturment data store has the entire universe of stocks that we are looking at
		// can be the entire US market for example
		InstrumentDatastore instrumentDatastore = new InstrumentDatastore()
		HttpBuilderFactory httpBuilderFactory = new HttpBuilderFactory()
		MbtFactory mbtFactory = new MbtFactory()

		// Will connect to active tick streaming feed
		HttpStreamingFeed atStreamingFeed = new HttpStreamingFeed(httpBuilderFactory)

		IBBroker ibBroker = new IBFactory().createIBbroker(instrumentDatastore, this, config.ib_host, config.ib_port, config.ib_client_id, config.ib_account)
		MbtBroker mbtBroker = mbtFactory.createMbtBroker(instrumentDatastore, config.mbtServerUrl)
		BrokerSet brokerSet = new BrokerSet([ibBroker, mbtBroker])

		// I believe that I used ActiveTick to monitor most stocks on the market, which you can't do with an IB feed.
		// But if it's only a few stocks you can use something else
		Feed tickFeed = new ActiveTickStreamingFeed(httpBuilderFactory, atStreamingFeed, config.active_tick_url, new SymbolFormatter(), config.maxLatency)
		TickTracker tickTracker = new TickTracker()
		OrderTracker orderTracker = new OrderTracker(clock)

		// This is safety logic to prevent our auto trader from making really stupid orders if there is a bug
		OrderSafetyGuard orderSafetyGuard = new OrderSafetyGuard(tickTracker, orderTracker, clock, config)

		// Rates all instruments with a score, if the score is above what we want we trade on this instrument
		FivePercentDropRater rater = new FivePercentDropRater(tickTracker, arg.strategyClock)

		// A simple file db (csv) for keeping my portfolio information
		arg.dbPortfolio = new FilePortfolio(new File("logs\\portfolio.csv"), instrumentDatastore, brokerSet)

		// Portfolio information
		arg.portfolio =  new Portfolio(arg.dbPortfolio, new TradeLogger(new File("logs\\trades.csv")))

		// Database of quotes and other things (like earnings, splits, ....) that we use for this strategy
		arg.database = new FivePercentDropDatabase(instrumentDatastore, calendar)

		arg.strategyClock = new com.queworx.trader.strategies.StrategyClock(sleeper, clock, config)
		arg.logger = new FivePercentDropLogger(calendar, clock)
		arg.orderTracker = orderTracker
		arg.trackerAdjuster = new FivePercentDropTrackerAdjuster()
		arg.tradeEvaluator = new FivePercentDropTradeEvaluator(rater, tickTracker, arg.orderTracker, arg.portfolio, brokerSet, arg.strategyClock, calendar, clock, config)
		arg.trader = new Trader(brokerSet, arg.orderTracker, orderSafetyGuard, sleeper)
		arg.orderAdjuster = new OrderAdjuster(arg.trader, arg.orderTracker, arg.portfolio, clock, sleeper)
		arg.tickFeed = tickFeed

		FivePercentDropStrategy strategy = new FivePercentDropStrategy(tickFeed, tickTracker, arg.portfolio, arg.logger, rater, brokerSet, config)
		Controller controller = new Controller(strategy, arg)

		((ActiveTickStreamingFeed)tickFeed).addTickListener(tickTracker)
		ibBroker.addOrderListener(arg.orderTracker)
		ibBroker.addOrderListener(arg.portfolio)
		mbtBroker.addOrderListener(arg.orderTracker)
		mbtBroker.addOrderListener(arg.portfolio)
		atStreamingFeed.addStreamingFeedListener(tickFeed)

		try
		{
			// I had a database of instruments, but that code is too big and not necessary
			Instrument a = new Instrument(id: 1, symbol: "GOOG", name: "Alphabet Inc.")
			Instrument b = new Instrument(id: 2, symbol: "AAPL", name: "Apple")

			instrumentDatastore.registerInstruments([a, b])
			ibBroker.connect()
			mbtBroker.connect()
			tickFeed.connect()
			controller.run()
		}
		catch(Exception ex)
		{
			log.error("Error", ex)
		}
		finally
		{
			tickFeed.disconnect()
			mbtBroker.disconnect()
			ibBroker.disconnect()
			System.exit(0)
		}
	}
}

package com.queworx.trader.strategies


import com.queworx.Clock
import com.queworx.Sleeper
import com.queworx.fetching.feed.Feed
import com.queworx.trader.Config

class ControllerArg
{
	Database database
	StrategyClock strategyClock
	Logger logger
	TradeEvaluator tradeEvaluator
	TrackerAdjuster trackerAdjuster
	com.queworx.trader.accounting.OrderTracker orderTracker
	com.queworx.trader.accounting.Portfolio portfolio

	com.queworx.trader.trading.OrderAdjuster orderAdjuster
	com.queworx.trader.trading.Trader trader

	Sleeper sleeper
	Clock clock
	Config config

	Feed tickFeed
	com.queworx.trader.accounting.FilePortfolio dbPortfolio
}

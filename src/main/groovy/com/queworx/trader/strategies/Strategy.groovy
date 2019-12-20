package com.queworx.trader.strategies


import com.queworx.brokers.Order
import com.queworx.brokers.Position

interface Strategy
{
	public void setDataSet(DataSet dataSet)

	// Before we load the initial dataset, can be good if you need to wait for the right time to load the dataset
	public void onPreDataLoad()

	// Before the market opens
	public void onPreOpen()

	// Right after the market opens
	public void onPostOpen()

	// This is the continuous loop that runs, expiring orders, doing clean up, and running the trading loop
	public void onLoopStart()

	// This loop looks for positions to open/close
	public void onTradingLoopStart()

	// Trading ends
	public void onEndTrading()

	public boolean shouldEndTrading()
	public List<Position> getPositionsToOpen()
	public List<Position> getPositionsToClose()

	public Order adjustOrderForResubmit(Order order, com.queworx.trader.trading.TradePurpose purpose)
	public Order createOrderForSubmit(Position position, com.queworx.trader.trading.TradePurpose purpose)
}
package com.queworx.trader.strategies

interface TrackerAdjuster
{
	/**
	 * Called once at the beginning of the trading loop
	 */
	public void adjustTrackers(DataSet dataSet)
}

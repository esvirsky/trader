package com.queworx.trader.strategies

class Rating
{
	public Double ex
	public String debugData
	public boolean shouldTrade
	public boolean shouldWatch

	public Rating(Double ex, String debugData, boolean shouldTrade, boolean shouldWatch)
	{
		this.ex = ex
		this.debugData = debugData
		this.shouldTrade = shouldTrade
		this.shouldWatch = shouldWatch
	}
}

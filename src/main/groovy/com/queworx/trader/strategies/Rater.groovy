package com.queworx.trader.strategies

import com.queworx.Instrument

interface Rater
{
	public Rating rate(Instrument instrument, DataSet dataSet)
}
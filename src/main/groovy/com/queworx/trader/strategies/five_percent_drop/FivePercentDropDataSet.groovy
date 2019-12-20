package com.queworx.trader.strategies.five_percent_drop

import com.queworx.Instrument
import com.queworx.trader.strategies.DataSet

class FivePercentDropDataSet implements DataSet
{
	private HashMap<Integer, Integer> __positionMap = [:]
	private List<Instrument> __instruments = []
	private List<Double> __prevCloses = []

	public List<Instrument> getInstruments()
	{
		return __instruments
	}

	public Double getPrevClose(Instrument instrument)
	{
		return __prevCloses[__positionMap.get(instrument.id)]
	}

	public void addInstrument(Instrument instrument, double prevClose)
	{
		__instruments.add(instrument)
		__prevCloses.add(prevClose)
		__positionMap[instrument.id] = __instruments.size() - 1
	}


}

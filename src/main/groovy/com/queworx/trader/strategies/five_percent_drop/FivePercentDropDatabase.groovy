package com.queworx.trader.strategies.five_percent_drop

import com.queworx.Instrument
import com.queworx.InstrumentDatastore
import com.queworx.trader.strategies.DataSet
import com.queworx.trader.strategies.Database
import groovy.util.logging.Log4j

@Log4j
class FivePercentDropDatabase implements Database {

    private InstrumentDatastore __instrumentFactory
    private Calendar __calendar

    public FivePercentDropDatabase(InstrumentDatastore instrumentFactory, Calendar calendar)
    {
        __instrumentFactory = instrumentFactory
        __calendar = calendar
    }

    @Override
    public DataSet getInitialDataSet()
    {
        FivePercentDropDataSet dataSet = new FivePercentDropDataSet()
        for(Instrument instrument in __instrumentFactory.getInstruments())
        {
            // ActiveTick can't handle these, so I'm removing them for now
            if(instrument.symbol.contains("-") || instrument.symbol.contains("+") || instrument.symbol.contains("*"))
                continue

            if(instrument.etf != null)
                continue

            // I removed all code that gets last close prices and volumes, as I was using a propriatory database for this stuff
            // and it would just confuse this code even more
            double prevClose = 4.2
            int prevVolume = 300000

            // We want to filter out instruments that just have too low of a price or too low volume
            if(prevClose < 5 || prevVolume < 300000)
                continue

            // Add this instrument to our session for monitoring
            dataSet.addInstrument(instrument)
        }

        // This was the ActiveTick limit I believe
        if(dataSet.getInstruments().size() > 1500)
        {
            log.warn("Initial dataset has too many instruments " + dataSet.getInstruments().size())
            FivePercentDropDataSet newDataSet = new FivePercentDropDataSet()
            dataSet.getInstruments()[0..<1500].each { newDataSet.addInstrument(it)}
            dataSet = newDataSet
        }

        return dataSet
    }
}

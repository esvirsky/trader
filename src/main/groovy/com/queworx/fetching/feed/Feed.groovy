package com.queworx.fetching.feed

import com.queworx.Instrument

interface Feed
{
	public void connect()
	public void disconnect()

	public void startPulling(List<Instrument> instruments)
	public void stopPulling(List<Instrument> instruments)
	public void stopPulling()
	public List<Instrument> getPulledInstruments()
}
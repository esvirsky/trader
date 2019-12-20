package com.queworx.fetching.feed

import com.queworx.Instrument

class FeedQueue extends LinkedList<Instrument>
{
	protected Integer __capacity

	/**
	 * Constructor
	 *
	 * @param capacity  If set, then this is the max number of instruments that this feed can handle,
	 * if we add more instruments, than older instruments get removed in fifo order
	 */
	public FeedQueue(Integer capacity = null)
	{
		__capacity = capacity
	}

	/**
	 * Adds new instruments
	 * @return - if past capacity returns the instruments removed
	 */
	public List<Instrument> add(List<Instrument> instruments)
	{
		instruments.each{ add(it) }
		if(__capacity == null)
			return []

		List<Instrument> ret = []
		while(size() > __capacity)
			ret.add(poll())

		return ret
	}

	public void remove(List<Instrument> instruments)
	{
		instruments.each{ remove(it) }
	}
}
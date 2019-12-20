package com.queworx

class Sleeper
{
	public void sleep(long millis)
	{
		java.lang.Thread.sleep(millis)
	}

	/**
	 * Will sleep total - passed millis. If passed is already more
	 * than total, will return right away
	 * @param totalMillis
	 * @param passedMillis
	 */
	public void sleep(long totalMillis, long passedMillis)
	{
		if(passedMillis >= totalMillis)
			return

		sleep(totalMillis - passedMillis)
	}
}

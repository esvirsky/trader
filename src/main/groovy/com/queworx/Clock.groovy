package com.queworx

import org.joda.time.DateTimeZone
import org.joda.time.LocalTime

/**
 * Encapsulates static time calls so that I can mock them
 */
class Clock
{
	public long currentTimeMillis()
	{
		return System.currentTimeMillis()
	}

	public LocalTime now(DateTimeZone zone = null)
	{
		return zone == null ? LocalTime.now() : LocalTime.now(zone)
	}
}

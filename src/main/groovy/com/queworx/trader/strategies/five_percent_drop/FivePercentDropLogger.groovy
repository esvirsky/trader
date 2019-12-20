package com.queworx.trader.strategies.five_percent_drop

import com.queworx.Calendar
import com.queworx.Clock
import com.queworx.trader.strategies.Logger

class FivePercentDropLogger extends Logger
{
	public FivePercentDropLogger(Calendar calendar, Clock clock)
	{
		super(calendar, clock)
	}

	@Override
	protected String __getRatingDebugHeader()
	{
		return "lastClose,delta"
	}
}

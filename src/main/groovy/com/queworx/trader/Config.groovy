package com.queworx.trader


import org.joda.time.LocalTime

class Config
{
	boolean testing
	String mbtServerUrl
	String active_tick_url
	String trader
	String python_script_path

	LocalTime marketOpen
	int maxLatency

	// Safety
	BigDecimal safety_min_price, safety_max_price, safety_min_trade_amount, safety_max_trade_amount, safety_max_filled_budget, safety_max_total_budget
	float safety_max_spread, safety_min_time_interval
	int safety_max_order_attempts, safety_max_order_count

	// IB TWS
	String ib_host, ib_account
	int ib_port
	int ib_client_id

	// Strategy specific
	LocalTime tradingStart, tradingEnd
	int openLiveDuration, closeLiveDuration, holdDuration, maxHoldDuration, maxHoldEmptyDuration, orderSleepDuration
	boolean uptick

	BigDecimal filledBudget, liveBudget, tradeAmount, maxCommission
	int maxPositions, maxDayPositions, openPositionInterval, closePositionInterval
	BigDecimal minPrice
	int maxTickTrack

	BigDecimal maxLongPosition, maxShortPosition, maxRiskyShortPosition

	private Properties __properties

	public Config(Properties properties)
	{
		__properties = properties
	}

	public void load(File configFile)
	{
		__properties.load(new FileInputStream(configFile))

		testing = __properties.testing.toLowerCase() == "true"
		mbtServerUrl = __properties.mbt_server_url
		active_tick_url = __properties.active_tick_url
		trader = __properties.trader
		python_script_path = __properties.python_script_path

		marketOpen = LocalTime.parse(__properties.market_open)
		maxLatency = __properties.max_latency.toInteger()

		safety_min_price = __properties.safety_min_price.toBigDecimal()
		safety_max_price = __properties.safety_max_price.toBigDecimal()
		safety_max_spread = __properties.safety_max_spread.toFloat()
		safety_max_trade_amount = __properties.safety_max_trade_amount.toBigDecimal()
		safety_min_trade_amount = __properties.safety_min_trade_amount.toBigDecimal()
		safety_max_filled_budget = __properties.safety_max_filled_budget.toBigDecimal()
		safety_max_total_budget = __properties.safety_max_total_budget.toBigDecimal()
		safety_max_order_attempts = __properties.safety_max_order_attempts.toInteger()
		safety_max_order_count = __properties.safety_max_order_count.toInteger()
		safety_min_time_interval = __properties.safety_min_time_interval.toFloat()

		ib_host = __properties.ib_host
		ib_port = __properties.ib_port.toInteger()
		ib_client_id = __properties.ib_client_id.toInteger()
        ib_account = __properties.ib_account

		tradingStart = LocalTime.parse(__properties.trading_start)
		tradingEnd = LocalTime.parse(__properties.trading_end)

		openLiveDuration = __properties.open_live_duration.toInteger()
		closeLiveDuration = __properties.close_live_duration.toInteger()
		holdDuration = __properties.hold_duration.toInteger()
		maxHoldDuration = __properties.max_hold_duration.toInteger()
		maxHoldEmptyDuration = __properties.max_hold_empty_duration.toInteger()
		orderSleepDuration = __properties.order_sleep_duration.toInteger()

		filledBudget =__properties.filled_budget.toBigDecimal()
		liveBudget = __properties.live_budget.toBigDecimal()
		tradeAmount = __properties.trade_amount.toBigDecimal()
		maxPositions = __properties.max_positions.toInteger()
		maxDayPositions = __properties.max_day_positions.toInteger()
		maxCommission = __properties.max_commission.toBigDecimal()
		openPositionInterval = __properties.open_position_interval.toInteger()
		closePositionInterval = __properties.close_position_interval.toInteger()
		minPrice = __properties.min_price.toBigDecimal()
		maxTickTrack = __properties.max_tick_track.toInteger()

		maxLongPosition = __properties.max_long_position.toBigDecimal()
		maxShortPosition = __properties.max_short_position.toBigDecimal()
		maxRiskyShortPosition = __properties.max_risky_short_position.toBigDecimal()
	}

	public int getLiveDuration(com.queworx.trader.trading.TradePurpose purpose)
	{
		return purpose == com.queworx.trader.trading.TradePurpose.Open ? openLiveDuration : closeLiveDuration
	}

	public int getPositionInterval(com.queworx.trader.trading.TradePurpose purpose)
	{
		return purpose == com.queworx.trader.trading.TradePurpose.Open ? openPositionInterval : closePositionInterval
	}
}

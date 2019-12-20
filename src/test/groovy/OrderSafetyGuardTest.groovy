package com.queworx.trader

import org.gmock.GMockTestCase
import org.junit.Before
import com.queworx.Clock
import com.queworx.TradeDirection
import com.queworx.Instrument
import com.queworx.Tick
import com.queworx.trader.tracking.TickTracker
import com.queworx.brokers.Order
import com.queworx.trader.trading.OrderSafetyGuard
import com.queworx.brokers.OrderStatus
import com.queworx.trader.accounting.OrderTracker
import com.queworx.trader.trading.UnsafeOrderException

class OrderSafetyGuardTest extends GMockTestCase
{
	private TickTracker __tickTracker
	private OrderTracker __orderTracker
	private Clock __clock
	private Config __config
	private OrderSafetyGuard __guard

	private Closure __construct

	@Before void setUp()
	{
		__tickTracker = mock(TickTracker)
		__orderTracker = mock(OrderTracker)
		__config = mock(Config)
		__clock = new Clock()

		__tickTracker.getLatestTick(match{true}).returns(new Tick(bidSize:  200, bid: 23.13, ask:  23.15, askSize: 100, lastTradeVolume:  20000)).stub()
		__orderTracker.getAllOrderStatuses().returns([]).stub()

		__clock.metaClass.time = 0
		__clock.metaClass.currentTimeMillis = {__clock.time += 2000; return __clock.time}

		__config.safety_min_price.returns(3).stub()
		__config.safety_max_price.returns(1000).stub()
		__config.safety_max_spread.returns(0.01).stub()
		__config.safety_max_trade_amount.returns(20000).stub()
		__config.safety_min_trade_amount.returns(2000).stub()
		__config.safety_max_filled_budget.returns(120000).stub()
		__config.safety_max_total_budget.returns(200000).stub()
		__config.safety_max_order_attempts.returns(3).stub()
		__config.safety_max_order_count.returns(5).stub()
		__config.safety_min_time_interval.returns(1.0).stub()
		__config.testing.returns(false).stub()

		__construct = {this.__guard = new OrderSafetyGuard(__tickTracker, __orderTracker, __clock, __config)}
		__construct()
	}

	/**
	 * Basic one that should pass
	 */
	void testIsOrderSafe()
	{
		play {
			__guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512))
			__guard.notifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512))
			__guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512))
		}
	}

	void testIsOrderSafeMinPrice()
	{
		__tickTracker.getLatestTick(match{true}).returns(new Tick(bidSize:  200, bid: 2.13, ask:  2.15, askSize: 100, lastTradeVolume:  20000)).stub()
		play {
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 2.15, quantity:  5120)) }
		}
	}

	void testIsOrderSafeMaxPrice()
	{
		__tickTracker.getLatestTick(match{true}).returns(new Tick(bidSize:  200, bid: 2000.13, ask:  2000.15, askSize: 100, lastTradeVolume:  20000)).stub()
		play {
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 2000.15, quantity:  5120)) }
		}
	}

	void testIsOrderSafeMinQuantity()
	{
		play {
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512, minQuantity: 513)) }
		}
	}

	void testIsOrderSafeSpread()
	{
		play {
			__guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.36, quantity:  512))
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.37, quantity:  512)) }
			__guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Short, price: 22.92, quantity:  512))
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Short, price: 22.91, quantity:  512)) }
		}
	}

	void testIsOrderSafeTradeAmount()
	{
		play {
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  864)) }
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  86)) }
		}
	}

	void testIsOrderSafeBudgetFilled()
	{
		// Altogether 110k filled, so another 10k would violate this number
		List<OrderStatus> statuses = []
		statuses.add(new OrderStatus(filledAvgPrice: 20.25, filledQuantity: 1481, remainingQuantity: 145))
		statuses.add(new OrderStatus(filledAvgPrice: 13.38, filledQuantity: 3542, remainingQuantity: 200))
		statuses.add(new OrderStatus(filledAvgPrice: 58.10, filledQuantity: 554, remainingQuantity: 0))

		__orderTracker = mock(OrderTracker)
		__orderTracker.getAllOrderStatuses().returns(statuses).stub()
		__construct()

		play {
			__guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  400))
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512)) }
		}
	}

	/**
	 * Same as filled, but includes remaining quantity as well, basically what would happen if everything would get filled
	 */
	void testIsOrderSafeBudgetAll()
	{
		// Altogether 192k all together, so another 10k would violate this number
		List<OrderStatus> statuses = []
		statuses.add(new OrderStatus(filledAvgPrice: 20.25, filledQuantity: 2352, remainingQuantity: 3545))
		statuses.add(new OrderStatus(filledAvgPrice: 13.38, filledQuantity: 2242, remainingQuantity: 800))
		statuses.add(new OrderStatus(filledAvgPrice: 58.10, filledQuantity: 554, remainingQuantity: 0))

		__orderTracker = mock(OrderTracker)
		__orderTracker.getAllOrderStatuses().returns(statuses).stub()
		__construct()

		play {
			__guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  300))
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512)) }
		}
	}

	void testIsOrderSafeAttemptCount()
	{
		Order order = new Order(price: 20.0, quantity: 300)
		play {
			__guard.notifyOrder(order)
			__guard.notifyOrder(order)
			__guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512))
			__guard.notifyOrder(order)
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512)) }
		}
	}

	void testIsOrderSafeOrderCount()
	{
		List<OrderStatus> statuses = []
		statuses.add(new OrderStatus(filledAvgPrice: 20.25, filledQuantity: 500, remainingQuantity: 200))
		statuses.add(new OrderStatus(filledAvgPrice: 13.38, filledQuantity: 300, remainingQuantity: 300))
		statuses.add(new OrderStatus(filledAvgPrice: 58.10, filledQuantity: 200, remainingQuantity: 0))
		statuses.add(new OrderStatus(filledAvgPrice: 58.10, filledQuantity: 10, remainingQuantity: 0))
		statuses.add(new OrderStatus(filledAvgPrice: 58.10, filledQuantity: 10, remainingQuantity: 0))

		__orderTracker = mock(OrderTracker)
		__orderTracker.getAllOrderStatuses().returns(statuses).stub()
		__construct()

		play {
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512)) }
		}
	}

	void testIsOrderSafeTimeInterval()
	{
		__clock.metaClass.time = 0
		__clock.metaClass.currentTimeMillis = {__clock.time += 500; return __clock.time}

		Order order = new Order(instrument: new Instrument(symbol: "AA"), direction: TradeDirection.Long, price: 23.15, quantity:  512)

		play {
			__guard.verifyOrder(order)
			__guard.notifyOrder(order)
			shouldFail(UnsafeOrderException) { __guard.verifyOrder(order) }
		}
	}
}

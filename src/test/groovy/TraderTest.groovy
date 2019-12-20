package com.queworx.trader

import org.gmock.GMockTestCase
import org.junit.Before
import com.queworx.Sleeper
import com.queworx.TradeDirection
import com.queworx.brokers.Broker
import com.queworx.brokers.Order
import com.queworx.trader.trading.BrokerSet
import com.queworx.trader.trading.OrderSafetyGuard
import com.queworx.brokers.OrderStatus
import com.queworx.trader.accounting.OrderTracker
import com.queworx.trader.trading.Trader
import com.queworx.trader.trading.UnsafeOrderException

class TraderTest extends GMockTestCase
{
	private Broker __broker
	private BrokerSet __brokerSet
	private OrderTracker __orderTracker
	private OrderSafetyGuard __safetyGuard
	private Trader __trader
	private Sleeper __sleeper

	private Closure __construct

	@Before void setUp()
	{
		__broker = mock(Broker)
		__brokerSet = mock(BrokerSet)
		__orderTracker = mock(OrderTracker)
		__safetyGuard = mock(OrderSafetyGuard)
		__sleeper = mock(Sleeper)

		__construct = {__trader = new Trader(__brokerSet, __orderTracker, __safetyGuard, __sleeper)}
	}

	void testSubmitOrder()
	{
		__brokerSet.getBestBroker(match{true}).returns(__broker)
		__broker.createOrder(match{true})
		__safetyGuard.verifyOrder(match{true}).returns([true, null])
		__safetyGuard.notifyOrder(match{true})
		__orderTracker.getOrderStatus(match{true}).returns(null)
		__orderTracker.addLiveOrder(match{true}, match{true})
		__construct()

		play {
			__trader.submitOrder(new Order())
		}
	}

	void testSubmitOrderActive()
	{
		__broker.resubmitOrder(match{true})
		__safetyGuard.verifyOrder(match{true}).returns([true, null])
		__safetyGuard.notifyOrder(match{true})
		__orderTracker.getOrderStatus(match{true}).returns(new OrderStatus(active: true))
		__orderTracker.getOrderBroker(match{true}).returns(__broker)
		__orderTracker.addLiveOrder(match{true}, match{true})
		__construct()

		play {
			__trader.submitOrder(new Order())
		}
	}

	void testSubmitOrderInactive()
	{
		__brokerSet.getBestBroker(match{true}).returns(__broker)
		__broker.createOrder(match{true})
		__safetyGuard.verifyOrder(match{true}).returns([true, null])
		__safetyGuard.notifyOrder(match{true})
		__orderTracker.getOrderStatus(match{true}).returns(new OrderStatus(active: false))
		__orderTracker.addLiveOrder(match{true}, match{true})
		__construct()

		play {
			__trader.submitOrder(new Order())
		}
	}

	void testSubmitOrderUnsafe()
	{
        __broker.createOrder(match{true}).never()
		__safetyGuard.verifyOrder(match{true}).raises(new UnsafeOrderException("dummy"))
		__construct()

		play {
			 __trader.submitOrder(new Order())
		}
	}

	void testScaleBack()
	{
		__orderTracker.getOrderBroker(match{true}).returns(__broker).times(2)
		__broker.resubmitOrder(match{it.price == 21.85})
		__broker.resubmitOrder(match{it.price == 24.15})
		__safetyGuard.verifyOrder(match{true}).returns([true, null]).times(2)
		__safetyGuard.notifyOrder(match{true}).times(2)
		__construct()

		play {
			__trader.scaleBack(new Order(price: 23.00, direction: TradeDirection.Long))
			__trader.scaleBack(new Order(price: 23.00, direction: TradeDirection.Short))
		}
	}

	void testScaleBackUnsafe()
	{
        __broker.resubmitOrder(match{true}).never()
		__safetyGuard.verifyOrder(match{true}).raises(new UnsafeOrderException("dummy"))
		__construct()

		play {
			__trader.scaleBack(new Order(price: 23.00, direction: TradeDirection.Long))
		}
	}

	void testCancelOrder()
	{
		__orderTracker.getOrderBroker(match{true}).returns(__broker)
		__orderTracker.addCancelledOrder(match{true})
		__broker.cancelOrder(match{true})
		__construct()

		play {
			__trader.cancelOrder(new Order())
		}

	}
}

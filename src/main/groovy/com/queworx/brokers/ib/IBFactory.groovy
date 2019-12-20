package com.queworx.brokers.ib

import com.ib.client.EClientSocket
import com.queworx.CommandRunningQueue
import com.queworx.ErrorHandler
import com.queworx.ExecutorFactory
import com.queworx.InstrumentDatastore
import com.queworx.Sleeper

class IBFactory
{
	public IBBroker createIBbroker(InstrumentDatastore instrumentDatastore, ErrorHandler errorHandler, String ibHost, int ibPort, int ibClientId, String ibAccount)
	{
		Sleeper sleeper = new Sleeper()
		ExecutorFactory executorFactory = new ExecutorFactory()

		CommandRunningQueue commandRunningQueue = new CommandRunningQueue(executorFactory, sleeper, 30)
		IBConfig ibConfig = new IBConfig(host: ibHost, port: ibPort, clientId: ibClientId, account: ibAccount, symbolLimit: 150)
		IBDatastore ibDatastore = new IBDatastore()

		IBReceiver ibReceiver = new IBReceiver(ibDatastore, instrumentDatastore, errorHandler)
		EClientSocket clientSocket = new EClientSocket(ibReceiver)

		IBFeedHandler ibFeedHandler = new IBFeedHandler(clientSocket, ibDatastore, commandRunningQueue)
		IBShortableQueryTask ibShortableQueryTask = new IBShortableQueryTask(ibFeedHandler, ibConfig, sleeper)

		IBBroker ibBroker = new IBBroker(clientSocket, ibDatastore, ibShortableQueryTask, ibFeedHandler, ibConfig, commandRunningQueue, executorFactory)
		ibReceiver.setBroker(ibBroker)

		return ibBroker
	}
}

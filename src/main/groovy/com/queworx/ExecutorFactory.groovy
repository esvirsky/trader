package com.queworx

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class ExecutorFactory
{
	public ExecutorService createSingleThreadExecutor()
	{
		return Executors.newSingleThreadExecutor()
	}

	public ScheduledExecutorService createSingleThreadScheduledExecutor()
	{
		return Executors.newSingleThreadScheduledExecutor()
	}
}

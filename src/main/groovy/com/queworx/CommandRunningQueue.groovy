package com.queworx

import groovy.util.logging.Log4j

import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Log4j
class CommandRunningQueue
{
	private ExecutorFactory __executorFactory
	private Sleeper __sleeper
	private int __commandInterval

	private CommandRunner __commandRunner
	private ExecutorService __executorService

	public CommandRunningQueue(ExecutorFactory executorFactory, Sleeper sleeper, int commandInterval)
	{
		__executorFactory = executorFactory
		__sleeper = sleeper
		__commandInterval = commandInterval
	}

	public void start()
	{
		__commandRunner = new CommandRunner(__sleeper, __commandInterval)
		__commandRunner.stop.set(false)
		__executorService = __executorFactory.createSingleThreadExecutor()
		__executorService.execute(__commandRunner)
	}

	public void stop()
	{
		__commandRunner.stop.set(true)
		__executorService.shutdownNow()
		__executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
	}

	public void addCommand(Closure closure, List<Object> args)
	{
		__commandRunner.commandQueue.add(new Command(closure: closure, args: args))
	}

	/**
	 * Runs the command with high priority - blocking
	 * So that commands are still run synchronously and observe the commandInterval
	 */
	public void runCommand(Closure closure, List<Objects> args)
	{
		synchronized (__commandRunner.commandLock)
		{
			__sleeper.sleep(__commandInterval, System.currentTimeMillis() - __commandRunner.lastRun)
			closure(*args)
			__commandRunner.lastRun = System.currentTimeMillis()
		}
	}

	private class Command
	{
		public Closure closure
		public List<Object> args
	}

	private class CommandRunner implements Runnable
	{
		private BlockingQueue<Command> commandQueue = new LinkedBlockingQueue<Command>()
		public Object commandLock = new Object()
		public AtomicBoolean stop = new AtomicBoolean(false)
		public long lastRun = -1

		private Sleeper __sleeper
		private int __commandInterval

		public CommandRunner(Sleeper sleeper, int commandInterval)
		{
			__sleeper = sleeper
			__commandInterval = commandInterval
		}

		@Override
		void run()
		{
			try
			{
				while(!stop.get())
				{
					Command command = commandQueue.poll(500, TimeUnit.MILLISECONDS)
					if(command == null)
						continue

					synchronized (commandLock)
					{
						__sleeper.sleep(__commandInterval, System.currentTimeMillis() - lastRun)
						command.closure(*command.args)
						lastRun = System.currentTimeMillis()
					}
				}
			}
			catch(Exception ex)
			{
				if(!(ex instanceof InterruptedException) && stop.get())
					log.error("Error in CommandRun")
			}
		}
	}
}

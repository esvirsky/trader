package com.queworx.fetching.activetick.http.server

import at.feedapi.ActiveTickServerAPI
import at.feedapi.Session
import com.sun.net.httpserver.HttpServer
import com.queworx.ErrorHandler

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

public class ActiveTickHttpServer implements ErrorHandler, Thread.UncaughtExceptionHandler
{
	private static final int HttpServerPort = 50002
	private static final String ApiKey = "ae7db39dbd794bb5b745451380971851"
	private static final String Username = "pasholy2001"
	private static final String Password = "YWxnbzRNYXRo"

	private ActiveTickComm __atComm
	private HttpServer __httpServer
	private ExecutorService __httpThreadPool

	public static void main(String[] args)
	{
		new ActiveTickHttpServer().run()
	}

	public void run()
	{
		Thread.setDefaultUncaughtExceptionHandler(this)
		Runtime.getRuntime().addShutdownHook(this.&__shutdown)

		SymbolParser symbolParser = new SymbolParser()
		ActiveTickServerAPI api = new ActiveTickServerAPI()
		api.ATInitAPI()

		Session session = api.ATCreateSession()
		ActiveTickStreamer atStreamer = new ActiveTickStreamer(session, symbolParser, this)

		__atComm = new ActiveTickComm(api, session, atStreamer, symbolParser, this)
		__atComm.connect(Username, Password, ApiKey)

		__httpServer = HttpServer.create(new InetSocketAddress(HttpServerPort), 0)
		__httpServer.with {
			createContext('/quoteData', new QuoteRequestHandler(__atComm, symbolParser, this))
			createContext('/modifyStream', new StreamModifyHandler(__atComm, symbolParser, this))
			createContext('/stream', new StreamHandler(__atComm, atStreamer, this))
			__httpThreadPool = Executors.newCachedThreadPool()
			setExecutor(__httpThreadPool)
			start()
		}
	}

	public void uncaughtException(Thread thread, Throwable throwable)
	{
		handleError("Uncaught Exception", throwable)
	}

	@Override
	public void handleError(String message, Throwable throwable)
	{
		println("Error: " + message + " " + throwable.message)
		throwable.printStackTrace()
	}

	private void __shutdown()
	{
		__atComm.disconnect()
		__httpServer.stop(1);
		__httpThreadPool.shutdownNow();
	}
}

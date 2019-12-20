package com.queworx.fetching.activetick.http.server

import at.feedapi.*
import at.shared.ATServerAPIDefines
import at.shared.ATServerAPIDefines.ATGUID
import at.shared.ActiveTick.DateTime
import at.shared.ActiveTick.UInt64
import at.utils.jlib.Errors
import at.utils.jlib.OutputMessage
import org.apache.commons.codec.binary.Base64
import com.queworx.ErrorHandler

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

class ActiveTickComm extends ActiveTickServerRequester implements ATCallback.ATSessionStatusChangeCallback, ATCallback.ATRequestTimeoutCallback, ATCallback.ATOutputMessageCallback, ATCallback.ATServerTimeUpdateCallback, ATCallback.ATLoginResponseCallback
{
	private final static int Timeout = 5000

	private ActiveTickServerAPI __api
	private Session __session
	private ActiveTickStreamer __atStreamer
	private SymbolParser __symbolParser
	private ErrorHandler __errorHandler

	private String __username
	private String __password

	private ATServerAPIDefines __defines
	private ConcurrentHashMap<Integer, QuoteResponseListener> __quoteListener = [:]
	private HashSet<String> __streamSymbols = new HashSet<String>()
	private HashMap<String, HashSet<String>> __symbolSessionIds = [:]

	public ActiveTickComm(ActiveTickServerAPI api, Session session, ActiveTickStreamer activeTickStreamer, SymbolParser symbolParser, ErrorHandler errorHandler)
	{
		super(api, session, activeTickStreamer)
		__api = api
		__session = session
		__atStreamer = activeTickStreamer
		__symbolParser = symbolParser
		__errorHandler = errorHandler
	}

	public void connect(String username, String password, String apiKey)
	{
		__username = username
		__password = password

		__defines = new ATServerAPIDefines()
		def guid = new ATGUID(__defines)
		guid.SetGuid(apiKey)

		long rc = __api.ATSetAPIKey(__session, guid)

		__session.SetServerTimeUpdateCallback(this);
		__session.SetOutputMessageCallback(this);

		if(rc == Errors.ERROR_SUCCESS)
		{
			boolean stats = __api.ATInitSession(__session, "activetick1.activetick.com", "activetick1.activetick.com", 443, this);
			System.out.println("\ninit status: " + (stats ? "ok" : "failed"));
		}

		System.out.println(__api.GetAPIVersionInformation());
		System.out.println("--------------------------------------------------------------------");
	}

	public void disconnect()
	{
		__api.ATShutdownSession(__session)
		__api.ATShutdownAPI()
	}

	public synchronized void requestQuotes(List<String> symbols, List<Integer> fields, QuoteResponseListener quoteListener)
	{
		def atSymbols = symbols.collect { Helpers.StringToSymbol(it) }
		def atFields = fields.collect { new ATServerAPIDefines.ATQuoteFieldType(__defines, (short)it) }

		long requestId = SendATQuoteDbRequest(atSymbols, atFields, Timeout)
		__quoteListener.put(requestId, quoteListener)
		println "Requesting data for " + symbols.size() + " symbols (request " + requestId + ")"
	}

	public synchronized void subscribeStream(List<String> symbols, String sessionId)
	{
		// Add sessionId
		sessionId = sessionId ?: "generic"
		for(String symbol in symbols)
		{
			if(!__symbolSessionIds.containsKey(symbol))
				__symbolSessionIds[symbol] = new HashSet<String>()

			__symbolSessionIds[symbol].add(sessionId)
		}

		// Don't ask to re-stream subscribed symbols
		symbols = symbols.findAll { !__streamSymbols.contains(it) }
		if(symbols.size() == 0)
			return

		__streamSymbols.addAll(symbols)
		def atSymbols = symbols.collect { Helpers.StringToSymbol(it) }

		ATServerAPIDefines.ATStreamRequestType requestType = new ATServerAPIDefines.ATStreamRequestType(__defines)
		requestType.m_streamRequestType = ATServerAPIDefines.ATStreamRequestType.StreamRequestSubscribe

		println "Subscribing to stream for " + symbols.size() + " symbols: " + symbols.join(",")
		println "Subscribed to " + __streamSymbols.size() + " symbols: " + __streamSymbols.join(",")
		SendATQuoteStreamRequest(atSymbols, requestType, Timeout)
		__atStreamer.setStreamedSymbolCount(__streamSymbols.size())
	}

	public synchronized void unsubscribeStream(List<String> symbols, String sessionId)
	{
		// Remove session Id - and filter it down to symbols that don't have other session ids associated with them
		sessionId = sessionId ?: "generic"
		symbols.each { if(__symbolSessionIds[it] != null) __symbolSessionIds[it].remove(sessionId)}
		symbols = symbols.findAll { __streamSymbols.contains(it) && (__symbolSessionIds[it] == null || __symbolSessionIds[it].size() == 0) }

		if(symbols.size() == 0)
			return

		__streamSymbols.removeAll(symbols)
		def atSymbols = symbols.collect { Helpers.StringToSymbol(it) }

		ATServerAPIDefines.ATStreamRequestType requestType = new ATServerAPIDefines.ATStreamRequestType(__defines)
		requestType.m_streamRequestType = ATServerAPIDefines.ATStreamRequestType.StreamRequestUnsubscribe

		println "Unsubscribing from stream for " + symbols.size() + " symbols: " + symbols.join(",")
		println "Still subscribed to " + __streamSymbols.size() + " symbols: " + __streamSymbols.join(",")
		SendATQuoteStreamRequest(atSymbols, requestType, Timeout)
		__atStreamer.setStreamedSymbolCount(__streamSymbols.size())
	}

	/**
	 * @param sessionId If null unsubscribes from everything
	 */
	public synchronized void unsubscribeAllStream(String sessionId)
	{
		println "Stopping stream: " + sessionId
		if(sessionId == null)
			__symbolSessionIds.clear()

		unsubscribeStream(new ArrayList<String>(__streamSymbols), sessionId)
	}

	public void OnQuoteDbResponse(long origRequest, ATServerAPIDefines.ATQuoteDbResponseType responseType, Vector<ATServerAPIDefines.QuoteDbResponseItem> vecData)
	{
		try
		{
			QuoteResponseListener listener = __quoteListener.remove(origRequest)
			println "Retrieving quote data for request " + origRequest + " symbol count: " + vecData.size()

			if(responseType.m_atQuoteDbResponseType != ATServerAPIDefines.ATQuoteDbResponseType.QuoteDbResponseSuccess)
			{
				listener.error(__responseTypeToStr(responseType))
				println ("Got an error from the server for quote db request: " + __responseTypeToStr(responseType))
				return
			}

			List<String> data = []
			for(ATServerAPIDefines.QuoteDbResponseItem item in vecData)
			{
				List<String> symbolData = []

				String symbol = __symbolParser.parseSymbol(item.m_atResponse.symbol)
				if(item.m_atResponse.status.m_atSymbolStatus != ATServerAPIDefines.ATSymbolStatus.SymbolStatusSuccess)
				{
					println ("Symbol: " + symbol + " bad status: " + __symbolStatusToStr(item.m_atResponse.status.m_atSymbolStatus))
					continue
				}

				symbolData.add(symbol + "," + item.m_atResponse.status.m_atSymbolStatus)

				for(def dataItem in item.m_vecDataItems)
				{
					String dataStr = __quoteDataToStr(dataItem.m_dataItem.dataType.m_atDataType, dataItem.GetItemData())
					String line = sprintf("%d,%d,%d,%s", dataItem.m_dataItem.fieldType.m_atQuoteFieldType, dataItem.m_dataItem.fieldStatus.m_atFieldStatus, dataItem.m_dataItem.dataType.m_atDataType, dataStr)
					symbolData.add(line)
				}

				data.add(symbolData.join(","))
			}

			listener.quoteData(data.join("\r\n"))
		}
		catch(Exception ex)
		{
			__errorHandler.handleError("OnQuoteDbResponse error", ex)
		}
	}

	private String __responseTypeToStr(ATServerAPIDefines.ATQuoteDbResponseType atQuoteDbResponseType)
	{
		if(atQuoteDbResponseType.m_atQuoteDbResponseType == ATServerAPIDefines.ATQuoteDbResponseType.QuoteDbResponseInvalidRequest) return "Invalid Request"
		if(atQuoteDbResponseType.m_atQuoteDbResponseType == ATServerAPIDefines.ATQuoteDbResponseType.QuoteDbResponseDenied) return "Request Denied"
		if(atQuoteDbResponseType.m_atQuoteDbResponseType == ATServerAPIDefines.ATQuoteDbResponseType.QuoteDbResponseSuccess) return "Success"
		if(atQuoteDbResponseType.m_atQuoteDbResponseType == ATServerAPIDefines.ATQuoteDbResponseType.QuoteDbResponseUnavailable) return "Unavailable"
		throw InvalidArgumentException(["Invalid response Type"])
	}

	private String __symbolStatusToStr(status)
	{
		switch(status)
		{
			case ATServerAPIDefines.ATSymbolStatus.SymbolStatusSuccess: return "SymbolStatusSuccess";
			case ATServerAPIDefines.ATSymbolStatus.SymbolStatusInvalid: return "SymbolStatusInvalid";
			case ATServerAPIDefines.ATSymbolStatus.SymbolStatusUnavailable: return "SymbolStatusUnavailable";
			case ATServerAPIDefines.ATSymbolStatus.SymbolStatusNoPermission: return "SymbolStatusNoPermission";
		}
		throw IllegalArgumentException("Invalid symbol status")
	}

	private String __quoteDataToStr(dataType, itemData)
	{
		byte[] intBytes = new byte[4];
		byte[] longBytes = new byte[8];
		switch(dataType)
		{
			case ATServerAPIDefines.ATDataType.Byte:
				return new String(itemData);
			case ATServerAPIDefines.ATDataType.ByteArray:
				return new String("byte data");
				break;
			case ATServerAPIDefines.ATDataType.UInteger32:
				System.arraycopy(itemData, 0, intBytes, 0, 4);
				int nData = ByteBuffer.wrap(intBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
				return new String("" + nData);
			case ATServerAPIDefines.ATDataType.UInteger64:
				System.arraycopy(itemData, 0, longBytes, 0, 8);
				long nData = ByteBuffer.wrap(longBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				return new String("" + nData);
			case ATServerAPIDefines.ATDataType.Integer32:
				System.arraycopy(itemData, 0, intBytes, 0, 4);
				int nData = ByteBuffer.wrap(intBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
				return new String("" + nData);
			case ATServerAPIDefines.ATDataType.Integer64:
				System.arraycopy(itemData, 0, longBytes, 0, 8);
				long nData = ByteBuffer.wrap(longBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
				return new String("" + nData);
			case ATServerAPIDefines.ATDataType.Price:
				ATServerAPIDefines.ATPRICE price = Helpers.BytesToPrice(itemData);
				return "" + price.price;
			case ATServerAPIDefines.ATDataType.String:
				return new String(itemData);
			case ATServerAPIDefines.ATDataType.UnicodeString:
				return new String(itemData);
			case ATServerAPIDefines.ATDataType.DateTime:
				UInt64 li = new UInt64(itemData);
				ATServerAPIDefines.SYSTEMTIME dateTime = DateTime.GetDateTime(li);
				StringBuilder sb = new StringBuilder();
				sb.append(dateTime.month);
				sb.append("/");
				sb.append(dateTime.day);
				sb.append("/");
				sb.append(dateTime.year);
				sb.append(" ");
				sb.append(dateTime.hour);
				sb.append(":");
				sb.append(dateTime.minute);
				sb.append(":");
				sb.append(dateTime.second);
				return sb.toString();
			default:
				throw new IllegalArgumentException("Couldn't read data")
		}
	}

	/**
	 * Session status change - we login in here
	 */
	@Override
	void process(Session session, ATServerAPIDefines.ATSessionStatusType type)
	{
		try
		{
			String strStatusType = "";
			switch (type.m_atSessionStatusType)
			{
				case ATServerAPIDefines.ATSessionStatusType.SessionStatusConnected: strStatusType = "SessionStatusConnected"; break;
				case ATServerAPIDefines.ATSessionStatusType.SessionStatusDisconnected: strStatusType = "SessionStatusDisconnected"; break;
				case ATServerAPIDefines.ATSessionStatusType.SessionStatusDisconnectedDuplicateLogin: strStatusType = "SessionStatusDisconnectedDuplicateLogin"; break;
				default: break;
			}

			println("RECV Status change [" + strStatusType + "]");

			//if we are connected to the server, send a login request
			if (type.m_atSessionStatusType == ATServerAPIDefines.ATSessionStatusType.SessionStatusConnected)
			{
				long m_lastRequest = __api.ATCreateLoginRequest(session, __username, new String(Base64.decodeBase64(__password.bytes)), this);
				boolean rc = __api.ATSendRequest(session, m_lastRequest, ActiveTickServerAPI.DEFAULT_REQUEST_TIMEOUT, this);

				System.out.println("SEND (" + m_lastRequest + "): Login request [" + __username + "] (rc = " + (char) Helpers.ConvertBooleanToByte(rc) + ")");
			}
		}
		catch(Exception ex)
		{
			__errorHandler.handleError("Error", ex)
		}
	}

	/**
	 * Login response
	 */
	@Override
	void process(Session session, long originalRequestId, ATServerAPIDefines.ATLOGIN_RESPONSE response)
	{
		String strLoginResponseType;
		switch(response.loginResponse.m_atLoginResponseType)
		{
			case ATServerAPIDefines.ATLoginResponseType.LoginResponseSuccess: strLoginResponseType = "LoginResponseSuccess"; break;
			case ATServerAPIDefines.ATLoginResponseType.LoginResponseInvalidUserid: strLoginResponseType = "LoginResponseInvalidUserid"; break;
			case ATServerAPIDefines.ATLoginResponseType.LoginResponseInvalidPassword: strLoginResponseType = "LoginResponseInvalidPassword"; break;
			case ATServerAPIDefines.ATLoginResponseType.LoginResponseInvalidRequest: strLoginResponseType = "LoginResponseInvalidRequest"; break;
			case ATServerAPIDefines.ATLoginResponseType.LoginResponseLoginDenied: strLoginResponseType = "LoginResponseLoginDenied"; break;
			case ATServerAPIDefines.ATLoginResponseType.LoginResponseServerError: strLoginResponseType = "LoginResponseServerError"; break;
			default: strLoginResponseType = "unknown"; break;
		}

		System.out.println("RECV " + originalRequestId + ": Login Response [" + strLoginResponseType + "]");
	}

	/**
	 * Timeout
	 */
	@Override
	void process(long origRequest)
	{
		println("(" + origRequest + "): Request timed-out\n");
	}

	/**
	 * Server time
	 */
	@Override
	void process(ATServerAPIDefines.SYSTEMTIME serverTime)
	{

	}

	/**
	 * Output messages
	 */
	@Override
	void process(OutputMessage outputMessage)
	{
		println(outputMessage.GetMessage());
	}
}

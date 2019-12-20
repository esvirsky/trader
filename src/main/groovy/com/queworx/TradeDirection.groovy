package com.queworx

enum TradeDirection
{
	Long, Short

	private boolean __isLong;
	private boolean __isShort;
	private TradeDirection __inverse;

	static {
		Long.__isLong = true;
		Long.__isShort = false;
		Short.__isLong = false;
		Short.__isShort = true;
		Long.__inverse = Short;
		Short.__inverse = Long;
	}

	public boolean isLong() { return __isLong }
	public boolean isShort() { return __isShort }
	public TradeDirection getInverse() { return __inverse }
}
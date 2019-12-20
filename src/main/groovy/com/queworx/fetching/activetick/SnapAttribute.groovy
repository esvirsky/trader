package com.queworx.fetching.activetick

public enum SnapAttribute
{
    ASK(7), ASK_SIZE(26), BID(6), BID_SIZE(25), IVOLUME(27), PREV_CLOSE(3), OPEN(2), LAST(5), NAME(33), LAST_TRADE_DATETIME(20),
    HIGH_PRICE(8),LOW_PRICE(9)
    SnapAttribute(int value) { this.value = value }
    private final int value
    public int value() {return value}
}
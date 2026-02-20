package io.pulseengine.pipeline;

import io.pulseengine.core.Side;

final class MdEvent {
    long tsNanos;
    long bidPx;
    long bidQty;
    long askPx;
    long askQty;

    byte l3EventType;
    long l3OrderId;
    long l3TradeId;
    Side l3Side;
    long l3PriceTicks;
    long l3Qty;
    long l3RemainingQty;

    void reset() {
        tsNanos = 0;
        bidPx = 0;
        bidQty = 0;
        askPx = 0;
        askQty = 0;
        l3EventType = 0;
        l3OrderId = 0;
        l3TradeId = 0;
        l3Side = null;
        l3PriceTicks = 0;
        l3Qty = 0;
        l3RemainingQty = 0;
    }
}

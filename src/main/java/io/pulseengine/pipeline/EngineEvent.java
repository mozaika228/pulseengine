package io.pulseengine.pipeline;

import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.OrderType;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;

final class EngineEvent {
    EngineCommandType commandType;

    long orderId;
    long traderId;
    Side side;
    OrderType orderType;
    TimeInForce tif;
    long priceTicks;
    long stopPriceTicks;
    long quantity;
    long peakSize;

    long cancelOrderId;

    long ingressNanos;
    boolean rejected;
    byte rejectCode;
    long postBidPx;
    long postBidQty;
    long postAskPx;
    long postAskQty;
    byte l3EventType;
    long l3OrderId;
    long l3TradeId;
    Side l3Side;
    long l3PriceTicks;
    long l3Qty;
    long l3RemainingQty;

    final OrderRequest reusableOrderRequest = new OrderRequest();

    void reset() {
        commandType = null;
        orderId = 0;
        traderId = 0;
        side = null;
        orderType = null;
        tif = null;
        priceTicks = 0;
        stopPriceTicks = 0;
        quantity = 0;
        peakSize = 0;
        cancelOrderId = 0;
        ingressNanos = 0;
        rejected = false;
        rejectCode = 0;
        postBidPx = 0;
        postBidQty = 0;
        postAskPx = 0;
        postAskQty = 0;
        l3EventType = 0;
        l3OrderId = 0;
        l3TradeId = 0;
        l3Side = null;
        l3PriceTicks = 0;
        l3Qty = 0;
        l3RemainingQty = 0;
    }
}

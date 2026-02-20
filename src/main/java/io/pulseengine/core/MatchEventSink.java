package io.pulseengine.core;

public interface MatchEventSink {
    void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos);

    void onOrderAccepted(long orderId, long openQty, long tsNanos);

    void onOrderRejected(long orderId, byte reasonCode, long tsNanos);

    void onOrderCanceled(long orderId, long remainingQty, long tsNanos);

    void onOrderFilled(long orderId, long tsNanos);

    void onOrderPartiallyFilled(long orderId, long remainingQty, long tsNanos);
}

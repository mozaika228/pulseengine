package io.pulseengine.persistence;

import io.pulseengine.core.OrderType;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;

public interface CommandJournal extends AutoCloseable {
    void appendNew(
        long tsNanos,
        long orderId,
        long traderId,
        Side side,
        OrderType orderType,
        TimeInForce tif,
        long price,
        long stopPrice,
        long quantity,
        long peak
    );

    void appendCancel(long tsNanos, long cancelOrderId);

    @Override
    void close();
}

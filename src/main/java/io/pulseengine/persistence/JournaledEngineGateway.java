package io.pulseengine.persistence;

import io.pulseengine.core.OrderType;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.pipeline.EnginePipeline;

public final class JournaledEngineGateway implements AutoCloseable {
    private final EnginePipeline engine;
    private final CommandJournal journal;

    public JournaledEngineGateway(EnginePipeline engine, CommandJournal journal) {
        this.engine = engine;
        this.journal = journal;
    }

    public void submitLimit(long orderId, long traderId, Side side, long price, long quantity, TimeInForce tif, long peak) {
        long ts = System.nanoTime();
        journal.appendNew(ts, orderId, traderId, side, OrderType.LIMIT, tif, price, 0, quantity, peak);
        engine.submitLimitAt(orderId, traderId, side, price, quantity, tif, peak, ts);
    }

    public void submitMarket(long orderId, long traderId, Side side, long quantity, TimeInForce tif) {
        long ts = System.nanoTime();
        journal.appendNew(ts, orderId, traderId, side, OrderType.MARKET, tif, 0, 0, quantity, 0);
        engine.submitMarketAt(orderId, traderId, side, quantity, tif, ts);
    }

    public void submitStopMarket(long orderId, long traderId, Side side, long stopPrice, long quantity) {
        long ts = System.nanoTime();
        journal.appendNew(ts, orderId, traderId, side, OrderType.STOP_MARKET, TimeInForce.GTC, 0, stopPrice, quantity, 0);
        engine.submitStopMarketAt(orderId, traderId, side, stopPrice, quantity, ts);
    }

    public void submitCancel(long cancelOrderId) {
        long ts = System.nanoTime();
        journal.appendCancel(ts, cancelOrderId);
        engine.submitCancelAt(cancelOrderId, ts);
    }

    @Override
    public void close() {
        journal.close();
    }
}

package io.pulseengine.pipeline;

import io.pulseengine.jni.NativeOrderBook;
import io.pulseengine.md.TopOfBookView;

public final class NativeIngressAdapter implements AutoCloseable {
    private final NativeOrderBook engine;
    private final TopOfBookView topOfBook;

    public NativeIngressAdapter(TopOfBookView topOfBook) {
        this.engine = new NativeOrderBook();
        this.topOfBook = topOfBook;
    }

    public void insertLimitOrder(long orderId, double price, long qty, boolean isBuy) {
        engine.insertLimitOrder(orderId, price, qty, isBuy);
        publishL2();
    }

    public NativeOrderBook.MatchResult matchMarketOrder(long orderId, long qty, boolean isBuy) {
        NativeOrderBook.MatchResult result = engine.matchMarketOrder(orderId, qty, isBuy);
        publishL2();
        return result;
    }

    public NativeOrderBook.L2Update publishL2Update() {
        return engine.publishL2Update();
    }

    private void publishL2() {
        NativeOrderBook.L2Update update = engine.publishL2Update();
        long bid = update.bestBid > 0 ? Math.round(update.bestBid) : 0;
        long ask = update.bestAsk > 0 ? Math.round(update.bestAsk) : 0;
        topOfBook.publish(bid, ask);
    }

    @Override
    public void close() {
        engine.close();
    }
}

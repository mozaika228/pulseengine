package io.pulseengine.jni;

public final class NativeOrderBook implements AutoCloseable {
    static {
        System.loadLibrary("pulseengine_native");
    }

    private long nativeHandle;

    public NativeOrderBook() {
        nativeHandle = nativeCreate();
        if (nativeHandle == 0) {
            throw new IllegalStateException("Failed to create native order book");
        }
    }

    public void insertLimitOrder(long orderId, double price, long qty, boolean isBuy) {
        ensureOpen();
        nativeInsertLimitOrder(nativeHandle, orderId, price, qty, isBuy);
    }

    public MatchResult matchMarketOrder(long orderId, long qty, boolean isBuy) {
        ensureOpen();
        return nativeMatchMarketOrder(nativeHandle, orderId, qty, isBuy);
    }

    public L2Update publishL2Update() {
        ensureOpen();
        return nativePublishL2Update(nativeHandle);
    }

    @Override
    public void close() {
        long handle = nativeHandle;
        nativeHandle = 0;
        if (handle != 0) {
            nativeDestroy(handle);
        }
    }

    private void ensureOpen() {
        if (nativeHandle == 0) {
            throw new IllegalStateException("Native order book is closed");
        }
    }

    private static native long nativeCreate();

    private static native void nativeDestroy(long handle);

    private static native void nativeInsertLimitOrder(long handle, long orderId, double price, long qty, boolean isBuy);

    private static native MatchResult nativeMatchMarketOrder(long handle, long orderId, long qty, boolean isBuy);

    private static native L2Update nativePublishL2Update(long handle);

    public static final class MatchResult {
        public final long filledQty;
        public final long remainingQty;
        public final int trades;
        public final double avgPrice;
        public final double lastTradePrice;

        public MatchResult(long filledQty, long remainingQty, int trades, double avgPrice, double lastTradePrice) {
            this.filledQty = filledQty;
            this.remainingQty = remainingQty;
            this.trades = trades;
            this.avgPrice = avgPrice;
            this.lastTradePrice = lastTradePrice;
        }
    }

    public static final class L2Update {
        public final double bestBid;
        public final long bestBidQty;
        public final double bestAsk;
        public final long bestAskQty;

        public L2Update(double bestBid, long bestBidQty, double bestAsk, long bestAskQty) {
            this.bestBid = bestBid;
            this.bestBidQty = bestBidQty;
            this.bestAsk = bestAsk;
            this.bestAskQty = bestAskQty;
        }
    }
}

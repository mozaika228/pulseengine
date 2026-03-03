package io.pulseengine.jni;

@Deprecated(forRemoval = false)
public final class NativeMatchingEngine implements AutoCloseable {
    private final NativeOrderBook delegate;

    public NativeMatchingEngine() {
        this.delegate = new NativeOrderBook();
    }

    public void insertLimitOrder(long orderId, double price, long qty, boolean isBuy) {
        delegate.insertLimitOrder(orderId, price, qty, isBuy);
    }

    public int tryInsertLimitOrder(long orderId, double price, long qty, boolean isBuy) {
        return delegate.tryInsertLimitOrder(orderId, price, qty, isBuy);
    }

    public void insertLimitIceberg(long orderId, double price, long qty, long peakQty, boolean isBuy) {
        delegate.insertLimitIceberg(orderId, price, qty, peakQty, isBuy);
    }

    public int tryInsertLimitIceberg(long orderId, double price, long qty, long peakQty, boolean isBuy) {
        return delegate.tryInsertLimitIceberg(orderId, price, qty, peakQty, isBuy);
    }

    public MatchResult matchMarketOrder(long orderId, long qty, boolean isBuy) {
        NativeOrderBook.MatchResult result = delegate.matchMarketOrder(orderId, qty, isBuy);
        return new MatchResult(result.filledQty, result.remainingQty, result.trades, result.avgPrice, result.lastTradePrice);
    }

    public L2Update publishL2Update() {
        NativeOrderBook.L2Update update = delegate.publishL2Update();
        return new L2Update(update.bestBid, update.bestBidQty, update.bestAsk, update.bestAskQty);
    }

    @Override
    public void close() {
        delegate.close();
    }

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
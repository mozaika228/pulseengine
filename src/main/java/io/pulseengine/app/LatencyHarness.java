package io.pulseengine.app;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import org.HdrHistogram.Histogram;

public final class LatencyHarness {
    public static void main(String[] args) {
        OrderBook book = new OrderBook();
        Histogram histogram = new Histogram(3);
        MatchEventSink sink = new BlackholeSink();

        for (int i = 1; i <= 100_000; i++) {
            OrderRequest seedBid = OrderRequest.limit(i, 100 + i, Side.BUY, 49_900, 10, TimeInForce.GTC);
            OrderRequest seedAsk = OrderRequest.limit(1_000_000L + i, 200 + i, Side.SELL, 50_100, 10, TimeInForce.GTC);
            long now = System.nanoTime();
            book.process(seedBid, sink, SmpPolicy.NONE, now);
            book.process(seedAsk, sink, SmpPolicy.NONE, now);
        }

        long baseOrderId = 10_000_000L;
        for (int i = 0; i < 1_000_000; i++) {
            Side side = (i & 1) == 0 ? Side.BUY : Side.SELL;
            long px = side == Side.BUY ? 50_200 : 49_800;
            OrderRequest req = OrderRequest.limit(baseOrderId + i, 9_999, side, px, 1, TimeInForce.IOC);

            long t0 = System.nanoTime();
            book.process(req, sink, SmpPolicy.CANCEL_AGGRESSOR, t0);
            long dt = System.nanoTime() - t0;
            histogram.recordValue(dt);
        }

        System.out.println("latency_ns_p50=" + histogram.getValueAtPercentile(50));
        System.out.println("latency_ns_p99=" + histogram.getValueAtPercentile(99));
        System.out.println("latency_ns_p9999=" + histogram.getValueAtPercentile(99.99));
        System.out.println("latency_ns_max=" + histogram.getMaxValue());
        System.out.println("best_bid=" + book.bestBid() + " best_ask=" + book.bestAsk());
    }

    private static final class BlackholeSink implements MatchEventSink {
        private long consumed;

        @Override
        public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
            consumed ^= tradeId ^ buyOrderId ^ sellOrderId ^ priceTicks ^ quantity ^ tsNanos;
        }

        @Override
        public void onOrderAccepted(long orderId, long openQty, long tsNanos) {
            consumed ^= orderId ^ openQty ^ tsNanos;
        }

        @Override
        public void onOrderRejected(long orderId, String reason, long tsNanos) {
            consumed ^= orderId ^ tsNanos ^ reason.length();
        }

        @Override
        public void onOrderCanceled(long orderId, long remainingQty, long tsNanos) {
            consumed ^= orderId ^ remainingQty ^ tsNanos;
        }

        @Override
        public void onOrderFilled(long orderId, long tsNanos) {
            consumed ^= orderId ^ tsNanos;
        }

        @Override
        public void onOrderPartiallyFilled(long orderId, long remainingQty, long tsNanos) {
            consumed ^= orderId ^ remainingQty ^ tsNanos;
        }
    }
}

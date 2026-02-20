package io.pulseengine.app;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.EnginePipeline;

import java.util.concurrent.ThreadFactory;

public final class PipelineDemo {
    public static void main(String[] args) {
        TopOfBookView topOfBook = new TopOfBookView();
        MatchEventSink sink = new BlackholeSink();
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "pulse-match-core");
            t.setDaemon(true);
            return t;
        };

        try (EnginePipeline engine = new EnginePipeline(1 << 16, sink, SmpPolicy.CANCEL_AGGRESSOR, topOfBook, threadFactory)) {
            long orderId = 1;
            for (int i = 0; i < 200_000; i++) {
                engine.submitLimit(orderId++, 101 + i, Side.BUY, 49_900, 10, TimeInForce.GTC, 0);
                engine.submitLimit(orderId++, 201 + i, Side.SELL, 50_100, 10, TimeInForce.GTC, 0);
            }

            long before = topOfBook.sequence();
            int burst = 1_000_000;
            long t0 = System.nanoTime();
            for (int i = 0; i < burst; i++) {
                Side side = (i & 1) == 0 ? Side.BUY : Side.SELL;
                long px = side == Side.BUY ? 50_200 : 49_800;
                engine.submitLimit(orderId++, 777, side, px, 1, TimeInForce.IOC, 0);
            }

            long target = before + burst;
            while (topOfBook.sequence() < target) {
                Thread.onSpinWait();
            }
            long elapsedNs = System.nanoTime() - t0;
            double seconds = elapsedNs / 1_000_000_000.0;
            double throughput = burst / seconds;

            System.out.println("processed=" + burst);
            System.out.println("elapsed_ms=" + (elapsedNs / 1_000_000.0));
            System.out.println("throughput_ops=" + (long) throughput);
            System.out.println("best_bid=" + topOfBook.bestBid() + " best_ask=" + topOfBook.bestAsk());
        }
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
        public void onOrderRejected(long orderId, byte reasonCode, long tsNanos) {
            consumed ^= orderId ^ tsNanos ^ reasonCode;
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

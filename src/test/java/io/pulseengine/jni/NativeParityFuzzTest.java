package io.pulseengine.jni;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeParityFuzzTest {

    @Test
    void randomizedOrderFlowKeepsNativeAndJavaInSync() {
        Assumptions.assumeTrue(NativeOrderBook.isNativeAvailable(), "Native library unavailable");

        final int scenarios = 5;
        final int operationsPerScenario = 5_000;

        for (int s = 0; s < scenarios; s++) {
            runScenario(31_415_926L + s, operationsPerScenario);
        }
    }

    private static void runScenario(long seed, int operations) {
        Random random = new Random(seed);
        OrderBook javaBook = new OrderBook();
        TrackingSink sink = new TrackingSink();

        try (NativeOrderBook nativeBook = new NativeOrderBook()) {
            long nextOrderId = 1;
            for (int i = 0; i < operations; i++) {
                boolean isLimit = random.nextInt(100) < 65;
                boolean isBuy = random.nextBoolean();
                long orderId = nextOrderId++;
                long qty = 1 + random.nextInt(20);

                if (isLimit) {
                    long price = isBuy
                        ? (49_850 + random.nextInt(51))   // 49_850..49_900
                        : (50_100 + random.nextInt(51));  // 50_100..50_150

                    javaBook.process(
                        OrderRequest.limit(orderId, 10_000 + i, isBuy ? Side.BUY : Side.SELL, price, qty, TimeInForce.GTC),
                        sink,
                        SmpPolicy.NONE,
                        i + 1
                    );
                    nativeBook.insertLimitOrder(orderId, price, qty, isBuy);
                } else {
                    long filledBefore = sink.filledQty(orderId);
                    javaBook.process(
                        OrderRequest.market(orderId, 20_000 + i, isBuy ? Side.BUY : Side.SELL, qty, TimeInForce.IOC),
                        sink,
                        SmpPolicy.NONE,
                        i + 1
                    );
                    long javaFilled = sink.filledQty(orderId) - filledBefore;

                    NativeOrderBook.MatchResult nativeResult = nativeBook.matchMarketOrder(orderId, qty, isBuy);
                    assertEquals(javaFilled, nativeResult.filledQty, "filledQty mismatch at op=" + i + " seed=" + seed);
                }

                NativeOrderBook.L2Update l2 = nativeBook.publishL2Update();
                assertEquals(javaBook.bestBid(), Math.round(l2.bestBid), "bestBid mismatch at op=" + i + " seed=" + seed);
                assertEquals(javaBook.bestAsk(), Math.round(l2.bestAsk), "bestAsk mismatch at op=" + i + " seed=" + seed);
                assertEquals(javaBook.bestBidQty(), l2.bestBidQty, "bestBidQty mismatch at op=" + i + " seed=" + seed);
                assertEquals(javaBook.bestAskQty(), l2.bestAskQty, "bestAskQty mismatch at op=" + i + " seed=" + seed);
            }
        }
    }

    private static final class TrackingSink implements MatchEventSink {
        private final Map<Long, Long> filledByOrder = new HashMap<>();

        long filledQty(long orderId) {
            return filledByOrder.getOrDefault(orderId, 0L);
        }

        @Override
        public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
            filledByOrder.merge(buyOrderId, quantity, Long::sum);
            filledByOrder.merge(sellOrderId, quantity, Long::sum);
        }

        @Override
        public void onOrderAccepted(long orderId, long openQty, long tsNanos) {
        }

        @Override
        public void onOrderRejected(long orderId, byte reasonCode, long tsNanos) {
        }

        @Override
        public void onOrderCanceled(long orderId, long remainingQty, long tsNanos) {
        }

        @Override
        public void onOrderFilled(long orderId, long tsNanos) {
        }

        @Override
        public void onOrderPartiallyFilled(long orderId, long remainingQty, long tsNanos) {
        }
    }
}

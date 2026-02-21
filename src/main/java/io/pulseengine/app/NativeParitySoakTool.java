package io.pulseengine.app;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.jni.NativeOrderBook;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class NativeParitySoakTool {
    private NativeParitySoakTool() {
    }

    public static void main(String[] args) {
        long seconds = args.length > 0 ? Long.parseLong(args[0]) : Duration.ofHours(6).toSeconds();
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20260221L;

        if (!NativeOrderBook.isNativeAvailable()) {
            throw new IllegalStateException("Native library unavailable for soak test");
        }

        Random random = new Random(seed);
        OrderBook javaBook = new OrderBook();
        TrackingSink sink = new TrackingSink();
        long startNs = System.nanoTime();
        long deadline = startNs + Duration.ofSeconds(seconds).toNanos();
        long nextOrderId = 1;
        long ops = 0;

        try (NativeOrderBook nativeBook = new NativeOrderBook()) {
            while (System.nanoTime() < deadline) {
                boolean isLimit = random.nextInt(100) < 65;
                boolean isBuy = random.nextBoolean();
                long orderId = nextOrderId++;
                long qty = 1 + random.nextInt(20);

                if (isLimit) {
                    long price = isBuy
                        ? (49_850 + random.nextInt(51))
                        : (50_100 + random.nextInt(51));
                    javaBook.process(
                        OrderRequest.limit(orderId, 10_000 + ops, isBuy ? Side.BUY : Side.SELL, price, qty, TimeInForce.GTC),
                        sink,
                        SmpPolicy.NONE,
                        ops + 1
                    );
                    nativeBook.insertLimitOrder(orderId, price, qty, isBuy);
                } else {
                    long before = sink.filledQty(orderId);
                    javaBook.process(
                        OrderRequest.market(orderId, 20_000 + ops, isBuy ? Side.BUY : Side.SELL, qty, TimeInForce.IOC),
                        sink,
                        SmpPolicy.NONE,
                        ops + 1
                    );
                    long javaFilled = sink.filledQty(orderId) - before;
                    NativeOrderBook.MatchResult nativeResult = nativeBook.matchMarketOrder(orderId, qty, isBuy);
                    if (javaFilled != nativeResult.filledQty) {
                        throw new IllegalStateException("Drift: filledQty java=" + javaFilled + " native=" + nativeResult.filledQty + " op=" + ops);
                    }
                }

                NativeOrderBook.L2Update l2 = nativeBook.publishL2Update();
                long bid = Math.round(l2.bestBid);
                long ask = Math.round(l2.bestAsk);
                if (javaBook.bestBid() != bid || javaBook.bestAsk() != ask
                    || javaBook.bestBidQty() != l2.bestBidQty || javaBook.bestAskQty() != l2.bestAskQty) {
                    throw new IllegalStateException(
                        "Drift: java=" + javaBook.bestBid() + "/" + javaBook.bestAsk()
                            + " native=" + bid + "/" + ask
                            + " bidQty(java/native)=" + javaBook.bestBidQty() + "/" + l2.bestBidQty
                            + " askQty(java/native)=" + javaBook.bestAskQty() + "/" + l2.bestAskQty
                            + " op=" + ops
                    );
                }

                ops++;
                if ((ops % 1_000_000) == 0) {
                    long elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000L;
                    System.out.println("soak_ops=" + ops + " elapsed_sec=" + elapsedSec + " best_bid=" + bid + " best_ask=" + ask);
                }
            }
        }

        long elapsedNs = System.nanoTime() - startNs;
        double sec = elapsedNs / 1_000_000_000.0;
        long throughput = sec > 0 ? (long) (ops / sec) : 0;
        System.out.println("soak_ok=true");
        System.out.println("duration_sec=" + (long) sec);
        System.out.println("ops=" + ops);
        System.out.println("throughput_ops=" + throughput);
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

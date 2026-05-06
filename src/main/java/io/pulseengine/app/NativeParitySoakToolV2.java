package io.pulseengine.app;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.RejectCode;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.jni.NativeOrderBook;
import io.pulseengine.ops.PrometheusMetricsServer;

import java.time.Duration;
import java.util.Random;

public final class NativeParitySoakToolV2 {
    private NativeParitySoakToolV2() {
    }

    public static void main(String[] args) {
        long seconds = args.length > 0 ? Long.parseLong(args[0]) : Duration.ofHours(6).toSeconds();
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20260221L;
        int metricsPort = args.length > 2 ? Integer.parseInt(args[2]) : 9400;
        int maxLevels = args.length > 3 ? Integer.parseInt(args[3]) : 4096;
        int maxOrders = args.length > 4 ? Integer.parseInt(args[4]) : 1_000_000;
        int limitPercent = args.length > 5 ? Integer.parseInt(args[5]) : 50;

        if (!NativeOrderBook.isNativeAvailable()) {
            throw new IllegalStateException("Native library unavailable for soak test");
        }
        if (limitPercent < 1 || limitPercent > 95) {
            throw new IllegalArgumentException("limitPercent must be in [1,95]");
        }

        Random random = new Random(seed);
        OrderBook javaBook = new OrderBook(maxLevels, maxOrders, Math.max(65_536, maxOrders));
        TrackingSink sink = new TrackingSink();
        long startNs = System.nanoTime();
        long deadline = startNs + Duration.ofSeconds(seconds).toNanos();
        long nextOrderId = 1;
        long ops = 0;
        long nativeRejects = 0;

        try (PrometheusMetricsServer metrics = new PrometheusMetricsServer(metricsPort);
             NativeOrderBook nativeBook = new NativeOrderBook(maxLevels, maxOrders)) {

            metrics.start();
            System.out.println("metrics_port=" + metricsPort);
            System.out.println("native_max_levels=" + maxLevels);
            System.out.println("native_max_orders=" + maxOrders);
            System.out.println("limit_percent=" + limitPercent);

            while (System.nanoTime() < deadline) {
                boolean isLimit = random.nextInt(100) < limitPercent;
                boolean isBuy = random.nextBoolean();
                long orderId = nextOrderId++;
                long qty = 1 + random.nextInt(20);

                if (isLimit) {
                    sink.beginLimitInsert();
                    long price = isBuy
                        ? (49_850 + random.nextInt(51))
                        : (50_100 + random.nextInt(51));

                    boolean isIceberg = random.nextInt(100) < 15 && qty > 2;
                    int status;
                    if (isIceberg) {
                        long peak = 1 + random.nextInt((int) Math.max(1, qty / 2));
                        status = nativeBook.tryInsertLimitIceberg(orderId, price, qty, peak, isBuy);
                        if (status == NativeOrderBook.INSERT_OK) {
                            OrderRequest req = OrderRequest.limit(orderId, 10_000 + ops, isBuy ? Side.BUY : Side.SELL, price, qty, TimeInForce.GTC);
                            req.peakSize = peak;
                            javaBook.process(req, sink, SmpPolicy.NONE, ops + 1);
                        }
                    } else {
                        status = nativeBook.tryInsertLimitOrder(orderId, price, qty, isBuy);
                        if (status == NativeOrderBook.INSERT_OK) {
                            javaBook.process(
                                OrderRequest.limit(orderId, 10_000 + ops, isBuy ? Side.BUY : Side.SELL, price, qty, TimeInForce.GTC),
                                sink,
                                SmpPolicy.NONE,
                                ops + 1
                            );
                        }
                    }

                    if (status == NativeOrderBook.INSERT_OK && sink.wasCapacityRejected()) {
                        throw new IllegalStateException("Capacity mismatch: java rejected accepted-native limit orderId=" + orderId + " op=" + ops);
                    }

                    if (status != NativeOrderBook.INSERT_OK) {
                        nativeRejects++;
                        metrics.set("pulseengine_native_insert_rejects_total", nativeRejects);
                    }
                } else {
                    sink.beginAggressor(orderId);
                    javaBook.process(
                        OrderRequest.market(orderId, 20_000 + ops, isBuy ? Side.BUY : Side.SELL, qty, TimeInForce.IOC),
                        sink,
                        SmpPolicy.NONE,
                        ops + 1
                    );
                    long javaFilled = sink.endAggressorAndGetFilled();
                    NativeOrderBook.MatchResult nativeResult = nativeBook.matchMarketOrder(orderId, qty, isBuy);
                    if (javaFilled != nativeResult.filledQty) {
                        metrics.set("pulseengine_parity_drift_total", 1);
                        throw new IllegalStateException("Drift: filledQty java=" + javaFilled + " native=" + nativeResult.filledQty + " op=" + ops);
                    }
                }

                NativeOrderBook.L2Update l2 = nativeBook.publishL2Update();
                long bid = Math.round(l2.bestBid);
                long ask = Math.round(l2.bestAsk);
                if (javaBook.bestBid() != bid || javaBook.bestAsk() != ask
                    || javaBook.bestBidQty() != l2.bestBidQty || javaBook.bestAskQty() != l2.bestAskQty) {
                    metrics.set("pulseengine_parity_drift_total", 1);
                    throw new IllegalStateException(
                        "Drift: java=" + javaBook.bestBid() + "/" + javaBook.bestAsk()
                            + " native=" + bid + "/" + ask
                            + " bidQty(java/native)=" + javaBook.bestBidQty() + "/" + l2.bestBidQty
                            + " askQty(java/native)=" + javaBook.bestAskQty() + "/" + l2.bestAskQty
                            + " op=" + ops
                    );
                }

                ops++;
                metrics.set("pulseengine_soak_ops_total", ops);
                metrics.set("pulseengine_best_bid", bid);
                metrics.set("pulseengine_best_ask", ask);

                if ((ops % 1_000_000) == 0) {
                    long elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000L;
                    long throughput = elapsedSec > 0 ? (ops / elapsedSec) : 0;
                    metrics.set("pulseengine_soak_throughput_ops", throughput);
                    System.out.println("soak_ops=" + ops + " elapsed_sec=" + elapsedSec + " best_bid=" + bid + " best_ask=" + ask + " native_rejects=" + nativeRejects);
                }
            }
        }

        long elapsedNs = System.nanoTime() - startNs;
        double sec = elapsedNs / 1_000_000_000.0;
        long throughput = sec > 0 ? (long) (ops / sec) : 0;
        System.out.println("soak_ok=true");
        System.out.println("seed=" + seed);
        System.out.println("parity_drift_total=0");
        System.out.println("duration_sec=" + (long) sec);
        System.out.println("ops=" + ops);
        System.out.println("throughput_ops=" + throughput);
        System.out.println("native_insert_rejects_total=" + nativeRejects);
    }

    private static final class TrackingSink implements MatchEventSink {
        private long activeAggressorOrderId = -1;
        private long activeAggressorFilledQty = 0;
        private boolean sawCapacityReject;

        void beginAggressor(long orderId) {
            activeAggressorOrderId = orderId;
            activeAggressorFilledQty = 0;
        }

        void beginLimitInsert() {
            sawCapacityReject = false;
        }

        boolean wasCapacityRejected() {
            return sawCapacityReject;
        }

        long endAggressorAndGetFilled() {
            long filled = activeAggressorFilledQty;
            activeAggressorOrderId = -1;
            activeAggressorFilledQty = 0;
            return filled;
        }

        @Override
        public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
            if (buyOrderId == activeAggressorOrderId || sellOrderId == activeAggressorOrderId) {
                activeAggressorFilledQty += quantity;
            }
        }

        @Override
        public void onOrderAccepted(long orderId, long openQty, long tsNanos) {
        }

        @Override
        public void onOrderRejected(long orderId, byte reasonCode, long tsNanos) {
            if (reasonCode == RejectCode.CAPACITY_EXCEEDED) {
                sawCapacityReject = true;
            }
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

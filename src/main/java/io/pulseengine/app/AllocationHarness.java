package io.pulseengine.app;

import com.sun.management.ThreadMXBean;
import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.OrderType;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.EnginePipeline;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

public final class AllocationHarness {
    public static void main(String[] args) {
        ThreadMXBean threadMxBean = threadMxBean();

        double coreAlloc = measureCoreAllocBytesPerOp(threadMxBean);
        double pipelineAlloc = measurePipelineAllocBytesPerOp(threadMxBean);

        System.out.println("core_alloc_bytes_per_op=" + coreAlloc);
        System.out.println("pipeline_core_alloc_bytes_per_op=" + pipelineAlloc);
    }

    private static double measureCoreAllocBytesPerOp(ThreadMXBean threadMxBean) {
        OrderBook book = new OrderBook();
        MatchEventSink sink = new BlackholeSink();
        OrderRequest seed = new OrderRequest();

        for (int i = 1; i <= 100_000; i++) {
            fillLimit(seed, i, 100 + i, Side.BUY, 49_900, 10, TimeInForce.GTC);
            book.process(seed, sink, SmpPolicy.NONE, System.nanoTime());
            fillLimit(seed, 1_000_000L + i, 200 + i, Side.SELL, 50_100, 10, TimeInForce.GTC);
            book.process(seed, sink, SmpPolicy.NONE, System.nanoTime());
        }

        OrderRequest req = new OrderRequest();
        for (int i = 0; i < 50_000; i++) {
            Side side = (i & 1) == 0 ? Side.BUY : Side.SELL;
            long px = side == Side.BUY ? 50_200 : 49_800;
            fillLimit(req, 10_000_000L + i, 9_999, side, px, 1, TimeInForce.IOC);
            book.process(req, sink, SmpPolicy.CANCEL_AGGRESSOR, System.nanoTime());
        }

        long threadId = Thread.currentThread().threadId();
        long before = threadMxBean.getThreadAllocatedBytes(threadId);
        int burst = 500_000;
        for (int i = 0; i < burst; i++) {
            Side side = (i & 1) == 0 ? Side.BUY : Side.SELL;
            long px = side == Side.BUY ? 50_200 : 49_800;
            fillLimit(req, 20_000_000L + i, 9_999, side, px, 1, TimeInForce.IOC);
            book.process(req, sink, SmpPolicy.CANCEL_AGGRESSOR, System.nanoTime());
        }
        long after = threadMxBean.getThreadAllocatedBytes(threadId);
        return safeAllocPerOp(before, after, burst);
    }

    private static double measurePipelineAllocBytesPerOp(ThreadMXBean threadMxBean) {
        TopOfBookView topOfBook = new TopOfBookView();
        MatchEventSink sink = new BlackholeSink();
        CapturingThreadFactory threadFactory = new CapturingThreadFactory();

        try (EnginePipeline engine = new EnginePipeline(1 << 16, sink, SmpPolicy.CANCEL_AGGRESSOR, topOfBook, threadFactory)) {
            long orderId = 1;
            for (int i = 0; i < 100_000; i++) {
                engine.submitLimit(orderId++, 101 + i, Side.BUY, 49_900, 10, TimeInForce.GTC, 0);
                engine.submitLimit(orderId++, 201 + i, Side.SELL, 50_100, 10, TimeInForce.GTC, 0);
            }

            long seededTarget = 200_000;
            while (topOfBook.sequence() < seededTarget) {
                Thread.onSpinWait();
            }

            Thread coreThread = threadFactory.awaitThread();

            long warmupBefore = topOfBook.sequence();
            for (int i = 0; i < 20_000; i++) {
                Side side = (i & 1) == 0 ? Side.BUY : Side.SELL;
                long px = side == Side.BUY ? 50_200 : 49_800;
                engine.submitLimit(orderId++, 777, side, px, 1, TimeInForce.IOC, 0);
            }
            while (topOfBook.sequence() < (warmupBefore + 20_000)) {
                Thread.onSpinWait();
            }

            long before = threadMxBean.getThreadAllocatedBytes(coreThread.threadId());
            long beforeSeq = topOfBook.sequence();
            int burst = 200_000;
            for (int i = 0; i < burst; i++) {
                Side side = (i & 1) == 0 ? Side.BUY : Side.SELL;
                long px = side == Side.BUY ? 50_200 : 49_800;
                engine.submitLimit(orderId++, 888, side, px, 1, TimeInForce.IOC, 0);
            }
            while (topOfBook.sequence() < (beforeSeq + burst)) {
                Thread.onSpinWait();
            }
            long after = threadMxBean.getThreadAllocatedBytes(coreThread.threadId());
            return safeAllocPerOp(before, after, burst);
        }
    }

    private static ThreadMXBean threadMxBean() {
        ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!threadMxBean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("Thread allocated memory is not supported on this JVM");
        }
        if (!threadMxBean.isThreadAllocatedMemoryEnabled()) {
            threadMxBean.setThreadAllocatedMemoryEnabled(true);
        }
        return threadMxBean;
    }

    private static void fillLimit(OrderRequest req, long orderId, long traderId, Side side, long priceTicks, long qty, TimeInForce tif) {
        req.orderId = orderId;
        req.traderId = traderId;
        req.side = side;
        req.type = OrderType.LIMIT;
        req.tif = tif;
        req.priceTicks = priceTicks;
        req.stopPriceTicks = 0;
        req.quantity = qty;
        req.peakSize = 0;
        req.sequence = orderId;
    }

    private static double safeAllocPerOp(long before, long after, int burst) {
        if (before < 0 || after < 0 || burst <= 0) {
            return 0.0;
        }
        double alloc = (after - before) / (double) burst;
        if (!Double.isFinite(alloc) || alloc < 0.0) {
            return 0.0;
        }
        return alloc;
    }

    private static final class CapturingThreadFactory implements ThreadFactory {
        private final AtomicReference<Thread> threadRef = new AtomicReference<>();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "alloc-harness-core");
            thread.setDaemon(true);
            threadRef.set(thread);
            return thread;
        }

        private Thread awaitThread() {
            Thread thread;
            while ((thread = threadRef.get()) == null) {
                Thread.onSpinWait();
            }
            return thread;
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

package io.pulseengine.app;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.jni.NativeOrderBook;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.NativeIngressAdapter;

public final class NativePipelineDemo {
    private NativePipelineDemo() {
    }

    public static void main(String[] args) {
        MatchingBackend backend = MatchingBackend.resolve();
        System.out.println("matching_backend=" + backend);

        if (backend == MatchingBackend.JAVA) {
            runJava();
            return;
        }
        runNative();
    }

    private static void runNative() {
        TopOfBookView top = new TopOfBookView();
        try (NativeIngressAdapter nativeIngress = new NativeIngressAdapter(top)) {
            nativeIngress.insertLimitOrder(1, 49_900, 10, true);
            nativeIngress.insertLimitOrder(2, 50_100, 15, false);

            NativeOrderBook.MatchResult result = nativeIngress.matchMarketOrder(3, 7, true);
            NativeOrderBook.L2Update l2 = nativeIngress.publishL2Update();

            System.out.println("filled=" + result.filledQty + " remaining=" + result.remainingQty + " trades=" + result.trades);
            System.out.println("bestBid=" + l2.bestBid + " bestAsk=" + l2.bestAsk);
            System.out.println("topOfBook=" + top.bestBid() + "/" + top.bestAsk());
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            System.err.println("Native library is not loaded. Build cpp/ and set java.library.path.");
            throw e;
        }
    }

    private static void runJava() {
        OrderBook book = new OrderBook();
        MatchEventSink sink = new BlackholeSink();
        book.process(OrderRequest.limit(1, 10, Side.BUY, 49_900, 10, TimeInForce.GTC), sink, SmpPolicy.CANCEL_AGGRESSOR, System.nanoTime());
        book.process(OrderRequest.limit(2, 11, Side.SELL, 50_100, 15, TimeInForce.GTC), sink, SmpPolicy.CANCEL_AGGRESSOR, System.nanoTime());
        book.process(OrderRequest.market(3, 20, Side.BUY, 7, TimeInForce.IOC), sink, SmpPolicy.CANCEL_AGGRESSOR, System.nanoTime());

        System.out.println("bestBid=" + book.bestBid() + " bestAsk=" + book.bestAsk());
        System.out.println("topOfBook=" + book.bestBid() + "/" + book.bestAsk());
    }

    private static final class BlackholeSink implements MatchEventSink {
        @Override
        public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
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

package io.pulseengine.bench;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.NativeIngressAdapter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

public class NativeVsJavaBenchmark {

    @State(Scope.Thread)
    public static class JavaState {
        OrderBook book;
        BlackholeSink sink;
        long nextOrderId;

        @Setup(Level.Iteration)
        public void setup() {
            book = new OrderBook();
            sink = new BlackholeSink();
            nextOrderId = 100;
            book.process(OrderRequest.limit(1, 10, io.pulseengine.core.Side.SELL, 50_000, 1_000_000, TimeInForce.GTC), sink, SmpPolicy.NONE, 1);
        }
    }

    @State(Scope.Thread)
    public static class NativeState {
        NativeIngressAdapter nativeIngress;
        boolean available;
        long nextOrderId;

        @Setup(Level.Iteration)
        public void setup() {
            try {
                nativeIngress = new NativeIngressAdapter(new TopOfBookView());
                available = true;
                nextOrderId = 100;
                nativeIngress.insertLimitOrder(1, 50_000, 1_000_000, false);
            } catch (RuntimeException | UnsatisfiedLinkError e) {
                available = false;
            }
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            if (nativeIngress != null) {
                nativeIngress.close();
            }
        }
    }

    @Benchmark
    public long javaOrderBookMarketMatch(JavaState s) {
        long orderId = ++s.nextOrderId;
        OrderRequest req = OrderRequest.market(orderId, 20, io.pulseengine.core.Side.BUY, 10, TimeInForce.IOC);
        s.book.process(req, s.sink, SmpPolicy.NONE, System.nanoTime());
        return s.sink.value;
    }

    @Benchmark
    public long nativeOrderBookMarketMatch(NativeState s) {
        if (!s.available) {
            return -1;
        }
        long orderId = ++s.nextOrderId;
        return s.nativeIngress.matchMarketOrder(orderId, 10, true).filledQty;
    }

    private static final class BlackholeSink implements MatchEventSink {
        long value;

        @Override
        public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
            value ^= tradeId ^ buyOrderId ^ sellOrderId ^ priceTicks ^ quantity ^ tsNanos;
        }

        @Override
        public void onOrderAccepted(long orderId, long openQty, long tsNanos) {
            value ^= orderId ^ openQty ^ tsNanos;
        }

        @Override
        public void onOrderRejected(long orderId, byte reasonCode, long tsNanos) {
            value ^= orderId ^ reasonCode ^ tsNanos;
        }

        @Override
        public void onOrderCanceled(long orderId, long remainingQty, long tsNanos) {
            value ^= orderId ^ remainingQty ^ tsNanos;
        }

        @Override
        public void onOrderFilled(long orderId, long tsNanos) {
            value ^= orderId ^ tsNanos;
        }

        @Override
        public void onOrderPartiallyFilled(long orderId, long remainingQty, long tsNanos) {
            value ^= orderId ^ remainingQty ^ tsNanos;
        }
    }
}

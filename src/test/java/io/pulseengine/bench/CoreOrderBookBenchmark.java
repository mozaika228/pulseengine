package io.pulseengine.bench;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1)
public class CoreOrderBookBenchmark {
    @State(Scope.Thread)
    public static class DirectState {
        private final MatchEventSink sink = new BlackholeSink();
        private OrderBook book;
        private long orderId;

        @Setup(Level.Trial)
        public void setup() {
            book = new OrderBook();
            orderId = 1;
            for (int i = 0; i < 20_000; i++) {
                book.process(
                    OrderRequest.limit(orderId++, 10_000 + i, Side.BUY, 49_900, 10, TimeInForce.GTC),
                    sink,
                    SmpPolicy.NONE,
                    System.nanoTime()
                );
                book.process(
                    OrderRequest.limit(orderId++, 20_000 + i, Side.SELL, 50_100, 10, TimeInForce.GTC),
                    sink,
                    SmpPolicy.NONE,
                    System.nanoTime()
                );
            }
        }
    }

    @Benchmark
    public void iocCrossingOrder(DirectState state) {
        final long id = state.orderId++;
        final Side side = (id & 1L) == 0 ? Side.BUY : Side.SELL;
        final long px = side == Side.BUY ? 50_200 : 49_800;
        state.book.process(
            OrderRequest.limit(id, 777, side, px, 1, TimeInForce.IOC),
            state.sink,
            SmpPolicy.CANCEL_AGGRESSOR,
            System.nanoTime()
        );
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

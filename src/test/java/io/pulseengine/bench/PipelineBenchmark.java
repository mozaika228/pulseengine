package io.pulseengine.bench;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.BinaryMdPublisher;
import io.pulseengine.md.MdMessageSink;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.EnginePipeline;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1)
public class PipelineBenchmark {

    @State(Scope.Thread)
    public static class PipelineState {
        @Param({"false", "true"})
        public boolean enableMd;

        private EnginePipeline engine;
        private TopOfBookView topOfBook;
        private long orderId;

        @Setup(Level.Trial)
        public void setup() {
            topOfBook = new TopOfBookView();
            MatchEventSink sink = new BlackholeSink();
            if (enableMd) {
                MdMessageSink mdSink = new BlackholeMdSink();
                BinaryMdPublisher publisher = new BinaryMdPublisher(mdSink);
                engine = new EnginePipeline(1 << 16, sink, SmpPolicy.CANCEL_AGGRESSOR, topOfBook, publisher, r -> {
                    Thread t = new Thread(r, "jmh-pipeline");
                    t.setDaemon(true);
                    return t;
                });
            } else {
                engine = new EnginePipeline(1 << 16, sink, SmpPolicy.CANCEL_AGGRESSOR, topOfBook, r -> {
                    Thread t = new Thread(r, "jmh-pipeline");
                    t.setDaemon(true);
                    return t;
                });
            }

            orderId = 1;
            long startSeq = topOfBook.sequence();
            for (int i = 0; i < 20_000; i++) {
                engine.submitLimit(orderId++, 30_000 + i, Side.BUY, 49_900, 10, TimeInForce.GTC, 0);
                engine.submitLimit(orderId++, 40_000 + i, Side.SELL, 50_100, 10, TimeInForce.GTC, 0);
            }
            long target = startSeq + 40_000;
            while (topOfBook.sequence() < target) {
                Thread.onSpinWait();
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            engine.close();
        }
    }

    @Benchmark
    public void iocCrossingOrder(PipelineState state) {
        final long id = state.orderId++;
        final Side side = (id & 1L) == 0 ? Side.BUY : Side.SELL;
        final long px = side == Side.BUY ? 50_200 : 49_800;
        final long before = state.topOfBook.sequence();

        state.engine.submitLimit(id, 777, side, px, 1, TimeInForce.IOC, 0);

        while (state.topOfBook.sequence() <= before) {
            Thread.onSpinWait();
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

    private static final class BlackholeMdSink implements MdMessageSink {
        private long consumed;

        @Override
        public void onMessage(byte[] buffer, int offset, int length) {
            consumed ^= buffer[offset] ^ length;
        }
    }
}

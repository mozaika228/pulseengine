package io.pulseengine.persistence;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.EnginePipeline;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderBookSnapshotStoreTest {

    @Test
    void snapshotRestoresEngineStateWithoutJournalReplay() throws Exception {
        Path snapshot = Files.createTempFile("pulseengine-snapshot", ".bin");

        TopOfBookView live = new TopOfBookView();
        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, live, OrderBookSnapshotStoreTest::newDaemon)) {
            long start = live.sequence();

            engine.submitLimit(1, 10, Side.BUY, 49_900, 50, TimeInForce.GTC, 0);
            engine.submitLimit(2, 11, Side.SELL, 50_100, 40, TimeInForce.GTC, 0);
            engine.submitLimit(3, 12, Side.BUY, 49_800, 10, TimeInForce.GTC, 0);

            long target = start + 3;
            while (live.sequence() < target) {
                Thread.onSpinWait();
            }

            engine.saveStateSnapshot(snapshot);
        }

        TopOfBookView restored = new TopOfBookView();
        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, restored, OrderBookSnapshotStoreTest::newDaemon)) {
            engine.loadStateSnapshot(snapshot);
            assertEquals(49_900, restored.bestBid());
            assertEquals(50_100, restored.bestAsk());
        }

        Files.deleteIfExists(snapshot);
    }

    @Test
    void corruptedSnapshotIsRejected() throws Exception {
        Path snapshot = Files.createTempFile("pulseengine-snapshot-corrupt", ".bin");

        TopOfBookView live = new TopOfBookView();
        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, live, OrderBookSnapshotStoreTest::newDaemon)) {
            long start = live.sequence();
            engine.submitLimit(1, 10, Side.BUY, 49_900, 50, TimeInForce.GTC, 0);
            while (live.sequence() < (start + 1)) {
                Thread.onSpinWait();
            }
            engine.saveStateSnapshot(snapshot);
        }

        byte[] data = Files.readAllBytes(snapshot);
        data[data.length - 1] ^= 0x7F;
        Files.write(snapshot, data);

        TopOfBookView restored = new TopOfBookView();
        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, restored, OrderBookSnapshotStoreTest::newDaemon)) {
            assertThrows(IllegalStateException.class, () -> engine.loadStateSnapshot(snapshot));
        }

        Files.deleteIfExists(snapshot);
    }

    private static Thread newDaemon(Runnable runnable) {
        Thread t = new Thread(runnable, "snapshot-test");
        t.setDaemon(true);
        return t;
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

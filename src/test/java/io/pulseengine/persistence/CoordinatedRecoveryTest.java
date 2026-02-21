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

class CoordinatedRecoveryTest {

    @Test
    void snapshotAndJournalCatchupRestoresLatestBook() throws Exception {
        Path journal = Files.createTempFile("pulseengine-coordinated", ".journal.bin");
        Path snapshot = Files.createTempFile("pulseengine-coordinated", ".snapshot.bin");
        Path checkpoint = Files.createTempFile("pulseengine-coordinated", ".checkpoint.bin");

        TopOfBookView live = new TopOfBookView();
        long liveBid;
        long liveAsk;
        long checkpointRecord;
        long liveSeq;

        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, live, CoordinatedRecoveryTest::newDaemon);
             FileCommandJournal commandJournal = new FileCommandJournal(journal, true);
             JournaledEngineGateway gateway = new JournaledEngineGateway(engine, commandJournal)) {

            long start = live.sequence();
            gateway.submitLimit(1, 10, Side.BUY, 49_900, 50, TimeInForce.GTC, 0);
            gateway.submitLimit(2, 11, Side.SELL, 50_100, 40, TimeInForce.GTC, 0);
            while (live.sequence() < start + 2) {
                Thread.onSpinWait();
            }

            SnapshotCheckpointStore.SnapshotCheckpoint cp = CoordinatedRecovery.captureSnapshot(
                engine, journal, snapshot, checkpoint
            );
            checkpointRecord = cp.journalRecord();

            long postSnapshotStart = live.sequence();
            gateway.submitLimit(3, 12, Side.BUY, 50_200, 2, TimeInForce.IOC, 0);
            gateway.submitLimit(4, 13, Side.SELL, 49_800, 1, TimeInForce.IOC, 0);
            while (live.sequence() < postSnapshotStart + 2) {
                Thread.onSpinWait();
            }

            liveSeq = live.sequence();
            liveBid = live.bestBid();
            liveAsk = live.bestAsk();
        }

        TopOfBookView restored = new TopOfBookView();
        long replayed;
        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, restored, CoordinatedRecoveryTest::newDaemon)) {
            long start = restored.sequence();
            replayed = CoordinatedRecovery.restoreWithCatchup(engine, snapshot, checkpoint, journal);
            long target = start + 1 + replayed;
            while (restored.sequence() < target) {
                Thread.onSpinWait();
            }
        }

        assertEquals(2, checkpointRecord);
        assertEquals(2, replayed);
        assertEquals(liveBid, restored.bestBid());
        assertEquals(liveAsk, restored.bestAsk());
        assertEquals(liveSeq - 1, restored.sequence());

        Files.deleteIfExists(journal);
        Files.deleteIfExists(snapshot);
        Files.deleteIfExists(checkpoint);
    }

    private static Thread newDaemon(Runnable runnable) {
        Thread t = new Thread(runnable, "coordinated-recovery-test");
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

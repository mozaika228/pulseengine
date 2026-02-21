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

class JournalReplayTest {

    @Test
    void replayRestoresTopOfBook() throws Exception {
        Path file = Files.createTempFile("pulseengine-journal", ".bin");

        TopOfBookView live = new TopOfBookView();
        long liveSeq;
        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, live, JournalReplayTest::newDaemon);
             FileCommandJournal journal = new FileCommandJournal(file, true);
             JournaledEngineGateway gateway = new JournaledEngineGateway(engine, journal)) {
            long start = live.sequence();

            gateway.submitLimit(1, 10, Side.BUY, 49_900, 50, TimeInForce.GTC, 0);
            gateway.submitLimit(2, 11, Side.SELL, 50_100, 40, TimeInForce.GTC, 0);
            gateway.submitLimit(3, 12, Side.BUY, 50_200, 2, TimeInForce.IOC, 0);
            gateway.submitLimit(4, 13, Side.SELL, 49_800, 1, TimeInForce.IOC, 0);

            long target = start + 4;
            while (live.sequence() < target) {
                Thread.onSpinWait();
            }
            liveSeq = live.sequence();
        }

        TopOfBookView replay = new TopOfBookView();
        long replayed;
        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, replay, JournalReplayTest::newDaemon)) {
            long start = replay.sequence();
            replayed = JournalReplayer.replay(file, engine);
            long target = start + replayed;
            while (replay.sequence() < target) {
                Thread.onSpinWait();
            }
        }

        assertEquals(4, replayed);
        assertEquals(liveSeq, replay.sequence());
        assertEquals(live.bestBid(), replay.bestBid());
        assertEquals(live.bestAsk(), replay.bestAsk());

        Files.deleteIfExists(file);
    }

    private static Thread newDaemon(Runnable runnable) {
        Thread t = new Thread(runnable, "journal-test");
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

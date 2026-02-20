package io.pulseengine.app;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.persistence.FileCommandJournal;
import io.pulseengine.persistence.JournalReplayer;
import io.pulseengine.persistence.JournaledEngineGateway;
import io.pulseengine.pipeline.EnginePipeline;

import java.nio.file.Files;
import java.nio.file.Path;

public final class JournalReplayDemo {
    public static void main(String[] args) {
        Path journalPath = Path.of("target", "orders.journal.bin");
        try {
            Files.deleteIfExists(journalPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reset journal file: " + journalPath, e);
        }

        TopOfBookView liveBook = new TopOfBookView();
        MatchEventSink sink = new BlackholeSink();

        long recordedSeq;
        try (EnginePipeline engine = new EnginePipeline(1 << 15, sink, SmpPolicy.CANCEL_AGGRESSOR, liveBook, JournalReplayDemo::newDaemon);
             FileCommandJournal journal = new FileCommandJournal(journalPath, true);
             JournaledEngineGateway gateway = new JournaledEngineGateway(engine, journal)) {

            long liveStartSeq = liveBook.sequence();
            gateway.submitLimit(1, 10, Side.BUY, 49_900, 50, TimeInForce.GTC, 0);
            gateway.submitLimit(2, 11, Side.SELL, 50_100, 40, TimeInForce.GTC, 0);
            gateway.submitLimit(3, 12, Side.BUY, 50_200, 2, TimeInForce.IOC, 0);
            gateway.submitLimit(4, 13, Side.SELL, 49_800, 1, TimeInForce.IOC, 0);

            long target = liveStartSeq + 4;
            while (liveBook.sequence() < target) {
                Thread.onSpinWait();
            }

            recordedSeq = liveBook.sequence();
            System.out.println("live_seq=" + recordedSeq);
            System.out.println("live_best_bid=" + liveBook.bestBid() + " live_best_ask=" + liveBook.bestAsk());
        }

        TopOfBookView replayBook = new TopOfBookView();
        try (EnginePipeline replayEngine = new EnginePipeline(1 << 15, sink, SmpPolicy.CANCEL_AGGRESSOR, replayBook, JournalReplayDemo::newDaemon)) {
            long replayStartSeq = replayBook.sequence();
            long replayed = JournalReplayer.replay(journalPath, replayEngine);

            long target = replayStartSeq + replayed;
            while (replayBook.sequence() < target) {
                Thread.onSpinWait();
            }

            System.out.println("replayed_records=" + replayed);
            System.out.println("replay_best_bid=" + replayBook.bestBid() + " replay_best_ask=" + replayBook.bestAsk());
        }
    }

    private static Thread newDaemon(Runnable runnable) {
        Thread t = new Thread(runnable, "pulse-journal-demo");
        t.setDaemon(true);
        return t;
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

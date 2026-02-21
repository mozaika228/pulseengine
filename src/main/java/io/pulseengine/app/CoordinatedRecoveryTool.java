package io.pulseengine.app;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.persistence.CoordinatedRecovery;
import io.pulseengine.persistence.SnapshotCheckpointStore;
import io.pulseengine.pipeline.EnginePipeline;

import java.nio.file.Path;

public final class CoordinatedRecoveryTool {
    private CoordinatedRecoveryTool() {
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("usage: CoordinatedRecoveryTool <capture|restore> <journalPath> <snapshotPath> <checkpointPath>");
            return;
        }

        String command = args[0];
        Path journalPath = Path.of(args[1]);
        Path snapshotPath = Path.of(args[2]);
        Path checkpointPath = Path.of(args[3]);

        TopOfBookView top = new TopOfBookView();
        try (EnginePipeline engine = new EnginePipeline(1 << 14, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, top, CoordinatedRecoveryTool::newDaemon)) {
            if ("capture".equalsIgnoreCase(command)) {
                SnapshotCheckpointStore.SnapshotCheckpoint cp = CoordinatedRecovery.captureSnapshot(
                    engine,
                    journalPath,
                    snapshotPath,
                    checkpointPath
                );
                System.out.println("capture_ok=true");
                System.out.println("checkpoint_record=" + cp.journalRecord());
                System.out.println("checkpoint_created_nanos=" + cp.createdNanos());
                return;
            }

            if ("restore".equalsIgnoreCase(command)) {
                long start = top.sequence();
                long replayed = CoordinatedRecovery.restoreWithCatchup(engine, snapshotPath, checkpointPath, journalPath);
                long target = start + 1 + replayed;
                while (top.sequence() < target) {
                    Thread.onSpinWait();
                }
                System.out.println("restore_ok=true");
                System.out.println("replayed_delta_records=" + replayed);
                System.out.println("best_bid=" + top.bestBid());
                System.out.println("best_ask=" + top.bestAsk());
                return;
            }

            throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static Thread newDaemon(Runnable runnable) {
        Thread t = new Thread(runnable, "pulse-coordinated-recovery");
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

package io.pulseengine.persistence;

import io.pulseengine.pipeline.EnginePipeline;

import java.nio.file.Path;

public final class CoordinatedRecovery {
    private CoordinatedRecovery() {
    }

    public static SnapshotCheckpointStore.SnapshotCheckpoint captureSnapshot(
        EnginePipeline engine,
        Path journalPath,
        Path snapshotPath,
        Path checkpointPath
    ) {
        JournalScanner.ScanResult before = JournalScanner.scan(journalPath);
        if (before.corrupted()) {
            throw new IllegalStateException("Journal is corrupted, cannot capture coordinated snapshot");
        }

        engine.saveStateSnapshot(snapshotPath);

        JournalScanner.ScanResult after = JournalScanner.scan(journalPath);
        if (after.corrupted()) {
            throw new IllegalStateException("Journal became corrupted during snapshot capture");
        }
        if (before.validRecords() != after.validRecords()) {
            throw new IllegalStateException(
                "Journal advanced during snapshot capture. Retry capture in a quiesced window."
            );
        }

        long createdNanos = System.nanoTime();
        SnapshotCheckpointStore.write(checkpointPath, after.validRecords(), createdNanos);
        return new SnapshotCheckpointStore.SnapshotCheckpoint(after.validRecords(), createdNanos);
    }

    public static long restoreWithCatchup(
        EnginePipeline engine,
        Path snapshotPath,
        Path checkpointPath,
        Path journalPath
    ) {
        engine.loadStateSnapshot(snapshotPath);
        SnapshotCheckpointStore.SnapshotCheckpoint checkpoint = SnapshotCheckpointStore.load(checkpointPath);
        return JournalReplayer.replayFromRecord(journalPath, engine, checkpoint.journalRecord());
    }
}

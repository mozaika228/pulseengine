package io.pulseengine.persistence;

import io.pulseengine.core.OrderType;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalRecoveryTest {
    @Test
    void detectAndRepairCorruption() throws Exception {
        Path file = Files.createTempFile("pulse-journal-corrupt", ".bin");
        try (FileCommandJournal journal = new FileCommandJournal(file, true)) {
            journal.appendNew(System.nanoTime(), 1, 100, Side.BUY, OrderType.LIMIT, TimeInForce.GTC, 49_900, 0, 10, 0);
            journal.appendCancel(System.nanoTime(), 1);
        }

        // Corrupt second record payload byte.
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            ByteBuffer one = ByteBuffer.allocate(1);
            one.put((byte) 0x7F).flip();
            channel.position(JournalCodec.RECORD_LENGTH + JournalCodec.offQuantity());
            channel.write(one);
        }

        JournalScanner.ScanResult before = JournalScanner.scan(file);
        assertTrue(before.corrupted());

        JournalScanner.ScanResult after = JournalScanner.repair(file);
        assertFalse(after.corrupted());
        assertTrue(after.validRecords() >= 1);

        Files.deleteIfExists(file);
    }
}

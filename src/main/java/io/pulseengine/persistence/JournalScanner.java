package io.pulseengine.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JournalScanner {
    private JournalScanner() {
    }

    public static ScanResult scan(Path path) {
        byte[] record = new byte[JournalCodec.RECORD_LENGTH];
        ByteBuffer buffer = ByteBuffer.wrap(record);

        long validRecords = 0;
        long validBytes = 0;

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            while (true) {
                buffer.clear();
                int bytes = 0;
                while (bytes < JournalCodec.RECORD_LENGTH) {
                    int read = channel.read(buffer);
                    if (read < 0) {
                        if (bytes == 0) {
                            return new ScanResult(validRecords, validBytes, -1L, false, null);
                        }
                        return new ScanResult(validRecords, validBytes, validRecords, true, "partial_record");
                    }
                    bytes += read;
                }

                if (!JournalCodec.validateMagic(record)) {
                    return new ScanResult(validRecords, validBytes, validRecords, true, "bad_magic");
                }
                if (!JournalCodec.validateChecksum(record)) {
                    return new ScanResult(validRecords, validBytes, validRecords, true, "bad_checksum");
                }

                validRecords++;
                validBytes += JournalCodec.RECORD_LENGTH;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan journal: " + path, e);
        }
    }

    public static ScanResult repair(Path path) {
        ScanResult result = scan(path);
        if (!result.corrupted()) {
            return result;
        }

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(result.validBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to repair journal: " + path, e);
        }
        return scan(path);
    }

    public record ScanResult(long validRecords, long validBytes, long corruptedRecordIndex, boolean corrupted, String reason) {
    }
}

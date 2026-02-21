package io.pulseengine.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;

public final class SnapshotCheckpointStore {
    private static final int MAGIC = 0x50454350; // PECP
    private static final int VERSION = 1;
    private static final int PAYLOAD_BYTES = 8 + 8;
    private static final int HEADER_BYTES = 4 + 4 + 4 + 4;

    private SnapshotCheckpointStore() {
    }

    public static void write(Path path, long journalRecord, long createdNanos) {
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        payload.putLong(journalRecord);
        payload.putLong(createdNanos);
        byte[] payloadBytes = payload.array();

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + PAYLOAD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.putInt(VERSION);
        buffer.putInt(PAYLOAD_BYTES);
        buffer.putInt(checksum(payloadBytes));
        buffer.put(payloadBytes);
        buffer.flip();

        try (FileChannel channel = FileChannel.open(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write checkpoint: " + path, e);
        }
    }

    public static SnapshotCheckpoint load(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size != (HEADER_BYTES + PAYLOAD_BYTES)) {
                throw new IllegalStateException("Corrupt checkpoint: invalid size");
            }

            ByteBuffer buffer = ByteBuffer.allocate((int) size).order(ByteOrder.LITTLE_ENDIAN);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) {
                    throw new IllegalStateException("Corrupt checkpoint: unexpected EOF");
                }
            }
            buffer.flip();

            int magic = buffer.getInt();
            if (magic != MAGIC) {
                throw new IllegalStateException("Corrupt checkpoint: bad magic");
            }
            int version = buffer.getInt();
            if (version != VERSION) {
                throw new IllegalStateException("Unsupported checkpoint version: " + version);
            }
            int payloadLength = buffer.getInt();
            int storedChecksum = buffer.getInt();
            if (payloadLength != PAYLOAD_BYTES) {
                throw new IllegalStateException("Corrupt checkpoint: bad payload length");
            }

            byte[] payload = new byte[PAYLOAD_BYTES];
            buffer.get(payload);
            if (checksum(payload) != storedChecksum) {
                throw new IllegalStateException("Corrupt checkpoint: bad checksum");
            }

            ByteBuffer payloadBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            long journalRecord = payloadBuffer.getLong();
            long createdNanos = payloadBuffer.getLong();
            return new SnapshotCheckpoint(journalRecord, createdNanos);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load checkpoint: " + path, e);
        }
    }

    private static int checksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        return (int) crc.getValue();
    }

    public record SnapshotCheckpoint(long journalRecord, long createdNanos) {
    }
}

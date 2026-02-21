package io.pulseengine.persistence;

import io.pulseengine.core.OrderBook;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;

public final class OrderBookSnapshotStore {
    private static final int FILE_MAGIC = 0x50455346; // PESF
    private static final int FILE_VERSION = 1;
    private static final int FILE_HEADER_BYTES = 4 + 4 + 4 + 4;

    private OrderBookSnapshotStore() {
    }

    public static void write(Path path, OrderBook orderBook) {
        int payloadBytes = orderBook.snapshotSizeBytes();
        ByteBuffer payload = ByteBuffer.allocateDirect(payloadBytes).order(ByteOrder.LITTLE_ENDIAN);
        orderBook.writeSnapshot(payload);
        payload.flip();

        byte[] payloadArray = new byte[payload.remaining()];
        payload.get(payloadArray);
        int checksum = checksum(payloadArray);

        ByteBuffer fileBuffer = ByteBuffer.allocate(FILE_HEADER_BYTES + payloadArray.length).order(ByteOrder.LITTLE_ENDIAN);
        fileBuffer.putInt(FILE_MAGIC);
        fileBuffer.putInt(FILE_VERSION);
        fileBuffer.putInt(payloadArray.length);
        fileBuffer.putInt(checksum);
        fileBuffer.put(payloadArray);
        fileBuffer.flip();

        try (FileChannel channel = FileChannel.open(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
            while (fileBuffer.hasRemaining()) {
                channel.write(fileBuffer);
            }
            channel.force(true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write snapshot: " + path, e);
        }
    }

    public static void load(Path path, OrderBook orderBook) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size < FILE_HEADER_BYTES) {
                throw new IllegalStateException("Corrupt snapshot: too small");
            }
            if (size > Integer.MAX_VALUE) {
                throw new IllegalStateException("Snapshot too large: " + size);
            }

            ByteBuffer fileBuffer = ByteBuffer.allocate((int) size).order(ByteOrder.LITTLE_ENDIAN);
            while (fileBuffer.hasRemaining()) {
                int read = channel.read(fileBuffer);
                if (read < 0) {
                    throw new IllegalStateException("Corrupt snapshot: unexpected EOF");
                }
            }
            fileBuffer.flip();

            int magic = fileBuffer.getInt();
            if (magic != FILE_MAGIC) {
                throw new IllegalStateException("Corrupt snapshot: bad magic");
            }
            int version = fileBuffer.getInt();
            if (version != FILE_VERSION) {
                throw new IllegalStateException("Unsupported snapshot version: " + version);
            }
            int payloadLength = fileBuffer.getInt();
            int storedChecksum = fileBuffer.getInt();
            if (payloadLength < 0 || payloadLength != fileBuffer.remaining()) {
                throw new IllegalStateException("Corrupt snapshot: bad payload length");
            }

            byte[] payloadArray = new byte[payloadLength];
            fileBuffer.get(payloadArray);
            if (checksum(payloadArray) != storedChecksum) {
                throw new IllegalStateException("Corrupt snapshot: bad checksum");
            }

            ByteBuffer payload = ByteBuffer.wrap(payloadArray).order(ByteOrder.LITTLE_ENDIAN);
            orderBook.loadSnapshot(payload);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load snapshot: " + path, e);
        }
    }

    private static int checksum(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload, 0, payload.length);
        return (int) crc.getValue();
    }
}

package io.pulseengine.persistence;

import io.pulseengine.core.OrderType;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public final class FileCommandJournal implements CommandJournal {
    private final FileChannel channel;
    private final byte[] record = new byte[JournalCodec.RECORD_LENGTH];
    private final ByteBuffer buffer = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN);
    private final boolean forceOnWrite;

    public FileCommandJournal(Path path, boolean forceOnWrite) {
        try {
            this.channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open journal: " + path, e);
        }
        this.forceOnWrite = forceOnWrite;
    }

    @Override
    public synchronized void appendNew(
        long tsNanos,
        long orderId,
        long traderId,
        Side side,
        OrderType orderType,
        TimeInForce tif,
        long price,
        long stopPrice,
        long quantity,
        long peak
    ) {
        resetRecord();
        buffer.put(JournalCodec.offRecordType(), JournalCodec.TYPE_NEW);
        buffer.put(JournalCodec.offSide(), JournalCodec.mapSide(side));
        buffer.put(JournalCodec.offOrderType(), JournalCodec.mapOrderType(orderType));
        buffer.put(JournalCodec.offTif(), JournalCodec.mapTif(tif));
        buffer.putLong(JournalCodec.offTs(), tsNanos);
        buffer.putLong(JournalCodec.offOrderId(), orderId);
        buffer.putLong(JournalCodec.offTraderId(), traderId);
        buffer.putLong(JournalCodec.offPrice(), price);
        buffer.putLong(JournalCodec.offStopPrice(), stopPrice);
        buffer.putLong(JournalCodec.offQuantity(), quantity);
        buffer.putLong(JournalCodec.offPeak(), peak);
        buffer.putLong(JournalCodec.offCancelOrderId(), 0L);

        writeRecord();
    }

    @Override
    public synchronized void appendCancel(long tsNanos, long cancelOrderId) {
        resetRecord();
        buffer.put(JournalCodec.offRecordType(), JournalCodec.TYPE_CANCEL);
        buffer.putLong(JournalCodec.offTs(), tsNanos);
        buffer.putLong(JournalCodec.offCancelOrderId(), cancelOrderId);

        writeRecord();
    }

    private void resetRecord() {
        Arrays.fill(record, (byte) 0);
        JournalCodec.initializeRecord(buffer);
    }

    private void writeRecord() {
        try {
            JournalCodec.writeChecksum(record);
            ByteBuffer write = ByteBuffer.wrap(record);
            while (write.hasRemaining()) {
                channel.write(write);
            }
            if (forceOnWrite) {
                channel.force(false);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append journal record", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close journal", e);
        }
    }
}

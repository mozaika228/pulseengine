package io.pulseengine.md;

import io.pulseengine.sbe.MdL2IncrementalDecoder;
import io.pulseengine.sbe.MdL2DepthSnapshotDecoder;
import io.pulseengine.sbe.MdL2SnapshotDecoder;
import io.pulseengine.sbe.MdL3IncrementalDecoder;
import io.pulseengine.sbe.MessageHeaderDecoder;
import org.agrona.concurrent.UnsafeBuffer;

public final class BinaryMdDecoder {
    private static final UnsafeBuffer READ_BUFFER = new UnsafeBuffer(new byte[0]);
    private static final MessageHeaderDecoder HEADER = new MessageHeaderDecoder();
    private static final MdL2SnapshotDecoder SNAPSHOT = new MdL2SnapshotDecoder();
    private static final MdL2IncrementalDecoder INCREMENTAL = new MdL2IncrementalDecoder();
    private static final MdL2DepthSnapshotDecoder DEPTH = new MdL2DepthSnapshotDecoder();
    private static final MdL3IncrementalDecoder L3 = new MdL3IncrementalDecoder();

    private BinaryMdDecoder() {
    }

    public static short msgType(byte[] buffer, int offset) {
        READ_BUFFER.wrap(buffer);
        HEADER.wrap(READ_BUFFER, offset);
        return (short) HEADER.templateId();
    }

    public static long sequence(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        return switch (HEADER.templateId()) {
            case MdMessageType.SNAPSHOT_L2 -> SNAPSHOT.seq();
            case MdMessageType.INCREMENTAL_L2 -> INCREMENTAL.seq();
            case MdMessageType.SNAPSHOT_L2_DEPTH -> DEPTH.seq();
            case MdMessageType.INCREMENTAL_L3 -> L3.seq();
            default -> 0;
        };
    }

    public static long tsNanos(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        return switch (HEADER.templateId()) {
            case MdMessageType.SNAPSHOT_L2 -> SNAPSHOT.tsNanos();
            case MdMessageType.INCREMENTAL_L2 -> INCREMENTAL.tsNanos();
            case MdMessageType.SNAPSHOT_L2_DEPTH -> DEPTH.tsNanos();
            case MdMessageType.INCREMENTAL_L3 -> L3.tsNanos();
            default -> 0;
        };
    }

    public static long bidPx(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        return switch (HEADER.templateId()) {
            case MdMessageType.SNAPSHOT_L2 -> SNAPSHOT.bidPx();
            case MdMessageType.INCREMENTAL_L2 -> INCREMENTAL.bidPx();
            default -> 0;
        };
    }

    public static long bidQty(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        return switch (HEADER.templateId()) {
            case MdMessageType.SNAPSHOT_L2 -> SNAPSHOT.bidQty();
            case MdMessageType.INCREMENTAL_L2 -> INCREMENTAL.bidQty();
            default -> 0;
        };
    }

    public static long askPx(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        return switch (HEADER.templateId()) {
            case MdMessageType.SNAPSHOT_L2 -> SNAPSHOT.askPx();
            case MdMessageType.INCREMENTAL_L2 -> INCREMENTAL.askPx();
            default -> 0;
        };
    }

    public static long askQty(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        return switch (HEADER.templateId()) {
            case MdMessageType.SNAPSHOT_L2 -> SNAPSHOT.askQty();
            case MdMessageType.INCREMENTAL_L2 -> INCREMENTAL.askQty();
            default -> 0;
        };
    }

    public static byte l3EventType(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        if (HEADER.templateId() != MdMessageType.INCREMENTAL_L3) {
            return 0;
        }
        return switch (L3.eventType()) {
            case Add -> MdMessageType.L3_ADD;
            case Modify -> MdMessageType.L3_MODIFY;
            case Cancel -> MdMessageType.L3_CANCEL;
            case Trade -> MdMessageType.L3_TRADE;
            default -> 0;
        };
    }

    public static long l3OrderId(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        return HEADER.templateId() == MdMessageType.INCREMENTAL_L3 ? L3.orderId() : 0;
    }

    public static long l3TradeId(byte[] buffer, int offset) {
        wrapBody(buffer, offset);
        return HEADER.templateId() == MdMessageType.INCREMENTAL_L3 ? L3.tradeId() : 0;
    }

    private static void wrapBody(byte[] buffer, int offset) {
        READ_BUFFER.wrap(buffer);
        HEADER.wrap(READ_BUFFER, offset);
        int bodyOffset = offset + MessageHeaderDecoder.ENCODED_LENGTH;
        if (HEADER.templateId() == MdMessageType.SNAPSHOT_L2) {
            SNAPSHOT.wrap(
                READ_BUFFER,
                bodyOffset,
                HEADER.blockLength(),
                HEADER.version()
            );
        } else if (HEADER.templateId() == MdMessageType.INCREMENTAL_L2) {
            INCREMENTAL.wrap(
                READ_BUFFER,
                bodyOffset,
                HEADER.blockLength(),
                HEADER.version()
            );
        } else if (HEADER.templateId() == MdMessageType.SNAPSHOT_L2_DEPTH) {
            DEPTH.wrap(
                READ_BUFFER,
                bodyOffset,
                HEADER.blockLength(),
                HEADER.version()
            );
        } else if (HEADER.templateId() == MdMessageType.INCREMENTAL_L3) {
            L3.wrap(
                READ_BUFFER,
                bodyOffset,
                HEADER.blockLength(),
                HEADER.version()
            );
        }
    }
}

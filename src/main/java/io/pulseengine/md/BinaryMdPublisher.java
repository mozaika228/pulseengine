package io.pulseengine.md;

import io.pulseengine.sbe.MdL2IncrementalEncoder;
import io.pulseengine.sbe.MdL2DepthSnapshotEncoder;
import io.pulseengine.sbe.MdL2SnapshotEncoder;
import io.pulseengine.sbe.MdL3IncrementalEncoder;
import io.pulseengine.sbe.MessageHeaderEncoder;
import io.pulseengine.sbe.BookSide;
import io.pulseengine.sbe.L3EventType;
import org.agrona.concurrent.UnsafeBuffer;

public final class BinaryMdPublisher {
    private final byte[] bytes = new byte[256];
    private final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MdL2SnapshotEncoder snapshotEncoder = new MdL2SnapshotEncoder();
    private final MdL2IncrementalEncoder incrementalEncoder = new MdL2IncrementalEncoder();
    private final MdL2DepthSnapshotEncoder depthSnapshotEncoder = new MdL2DepthSnapshotEncoder();
    private final MdL3IncrementalEncoder l3IncrementalEncoder = new MdL3IncrementalEncoder();
    private final MdMessageSink sink;
    private long sequence;
    private long lastBidPx = Long.MIN_VALUE;
    private long lastBidQty = Long.MIN_VALUE;
    private long lastAskPx = Long.MIN_VALUE;
    private long lastAskQty = Long.MIN_VALUE;
    private boolean snapshotPublished;

    public BinaryMdPublisher(MdMessageSink sink) {
        this.sink = sink;
    }

    public void publishSnapshot(long tsNanos, long bidPx, long bidQty, long askPx, long askQty) {
        encodeL2(MdMessageType.SNAPSHOT_L2, tsNanos, bidPx, bidQty, askPx, askQty);
        snapshotPublished = true;
        remember(bidPx, bidQty, askPx, askQty);
    }

    public void publishIncrementalIfChanged(long tsNanos, long bidPx, long bidQty, long askPx, long askQty) {
        if (!snapshotPublished) {
            publishSnapshot(tsNanos, bidPx, bidQty, askPx, askQty);
            return;
        }

        if (bidPx == lastBidPx && bidQty == lastBidQty && askPx == lastAskPx && askQty == lastAskQty) {
            return;
        }

        encodeL2(MdMessageType.INCREMENTAL_L2, tsNanos, bidPx, bidQty, askPx, askQty);
        remember(bidPx, bidQty, askPx, askQty);
    }

    public void publishDepthSnapshot(long tsNanos, int depth, long[] bidPx, long[] bidQty, long[] askPx, long[] askQty) {
        long seq = ++sequence;
        MdL2DepthSnapshotEncoder encoder = depthSnapshotEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        encoder.seq(seq).tsNanos(tsNanos);
        MdL2DepthSnapshotEncoder.LevelsEncoder levels = encoder.levelsCount(depth * 2);

        for (int i = 0; i < depth; i++) {
            levels.next()
                .side(BookSide.Bid)
                .price(bidPx[i])
                .quantity(bidQty[i]);
            levels.next()
                .side(BookSide.Ask)
                .price(askPx[i])
                .quantity(askQty[i]);
        }
        int length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
        sink.onMessage(bytes, 0, length);
    }

    public void publishL3Incremental(
        long tsNanos,
        byte eventType,
        long orderId,
        long tradeId,
        byte side,
        long price,
        long quantity,
        long remainingQty
    ) {
        long seq = ++sequence;
        l3IncrementalEncoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .seq(seq)
            .tsNanos(tsNanos)
            .eventType(mapL3EventType(eventType))
            .orderId(orderId)
            .tradeId(tradeId)
            .side(mapSide(side))
            .price(price)
            .quantity(quantity)
            .remainingQty(remainingQty);
        int length = MessageHeaderEncoder.ENCODED_LENGTH + l3IncrementalEncoder.encodedLength();
        sink.onMessage(bytes, 0, length);
    }

    private void encodeL2(short msgType, long tsNanos, long bidPx, long bidQty, long askPx, long askQty) {
        long seq = ++sequence;
        int length;
        if (msgType == MdMessageType.SNAPSHOT_L2) {
            snapshotEncoder
                .wrapAndApplyHeader(buffer, 0, headerEncoder)
                .seq(seq)
                .tsNanos(tsNanos)
                .bidPx(bidPx)
                .bidQty(bidQty)
                .askPx(askPx)
                .askQty(askQty);
            length = MessageHeaderEncoder.ENCODED_LENGTH + snapshotEncoder.encodedLength();
        } else {
            incrementalEncoder
                .wrapAndApplyHeader(buffer, 0, headerEncoder)
                .seq(seq)
                .tsNanos(tsNanos)
                .bidPx(bidPx)
                .bidQty(bidQty)
                .askPx(askPx)
                .askQty(askQty);
            length = MessageHeaderEncoder.ENCODED_LENGTH + incrementalEncoder.encodedLength();
        }
        sink.onMessage(bytes, 0, length);
    }

    private void remember(long bidPx, long bidQty, long askPx, long askQty) {
        lastBidPx = bidPx;
        lastBidQty = bidQty;
        lastAskPx = askPx;
        lastAskQty = askQty;
    }

    private static L3EventType mapL3EventType(byte eventType) {
        return switch (eventType) {
            case MdMessageType.L3_ADD -> L3EventType.Add;
            case MdMessageType.L3_MODIFY -> L3EventType.Modify;
            case MdMessageType.L3_CANCEL -> L3EventType.Cancel;
            case MdMessageType.L3_TRADE -> L3EventType.Trade;
            default -> L3EventType.NULL_VAL;
        };
    }

    private static BookSide mapSide(byte side) {
        return side == 1 ? BookSide.Ask : BookSide.Bid;
    }
}

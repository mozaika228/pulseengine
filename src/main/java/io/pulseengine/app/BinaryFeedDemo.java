package io.pulseengine.app;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.BinaryMdDecoder;
import io.pulseengine.md.BinaryMdPublisher;
import io.pulseengine.md.MdMessageSink;
import io.pulseengine.md.MdMessageType;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.EnginePipeline;

import java.util.concurrent.ThreadFactory;

public final class BinaryFeedDemo {
    public static void main(String[] args) {
        TopOfBookView topOfBook = new TopOfBookView();
        DecodingSink mdSink = new DecodingSink();
        BinaryMdPublisher publisher = new BinaryMdPublisher(mdSink);
        MatchEventSink eventSink = new BlackholeSink();

        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "pulse-md-core");
            t.setDaemon(true);
            return t;
        };

        try (EnginePipeline engine = new EnginePipeline(1 << 15, eventSink, SmpPolicy.NONE, topOfBook, publisher, threadFactory)) {
            long startSeq = topOfBook.sequence();
            long id = 1;
            engine.submitLimit(id++, 10, Side.BUY, 49_900, 50, TimeInForce.GTC, 0);
            engine.submitLimit(id++, 11, Side.SELL, 50_100, 40, TimeInForce.GTC, 0);
            engine.submitLimit(id++, 12, Side.BUY, 50_200, 2, TimeInForce.IOC, 0);
            engine.submitLimit(id++, 13, Side.SELL, 49_800, 1, TimeInForce.IOC, 0);

            long targetSeq = startSeq + 4;
            while (topOfBook.sequence() < targetSeq) {
                Thread.onSpinWait();
            }

            System.out.println("decoded_messages=" + mdSink.messageCount);
            System.out.println("snapshot_messages=" + mdSink.snapshotCount);
            System.out.println("incremental_messages=" + mdSink.incrementalCount);
            System.out.println("l3_messages=" + mdSink.l3Count);
            System.out.println("depth_snapshot_messages=" + mdSink.depthCount);
            System.out.println("last_seq=" + mdSink.lastSeq);
            System.out.println("last_bid=" + mdSink.lastBidPx + "@" + mdSink.lastBidQty);
            System.out.println("last_ask=" + mdSink.lastAskPx + "@" + mdSink.lastAskQty);
        }
    }

    private static final class DecodingSink implements MdMessageSink {
        long messageCount;
        long snapshotCount;
        long incrementalCount;
        long l3Count;
        long depthCount;
        long lastSeq;
        long lastBidPx;
        long lastBidQty;
        long lastAskPx;
        long lastAskQty;

        @Override
        public void onMessage(byte[] buffer, int offset, int length) {
            messageCount++;
            short type = BinaryMdDecoder.msgType(buffer, offset);
            if (type == MdMessageType.SNAPSHOT_L2) {
                snapshotCount++;
            } else if (type == MdMessageType.INCREMENTAL_L2) {
                incrementalCount++;
            } else if (type == MdMessageType.INCREMENTAL_L3) {
                l3Count++;
            } else if (type == MdMessageType.SNAPSHOT_L2_DEPTH) {
                depthCount++;
            }
            lastSeq = BinaryMdDecoder.sequence(buffer, offset);
            if (type == MdMessageType.SNAPSHOT_L2 || type == MdMessageType.INCREMENTAL_L2) {
                lastBidPx = BinaryMdDecoder.bidPx(buffer, offset);
                lastBidQty = BinaryMdDecoder.bidQty(buffer, offset);
                lastAskPx = BinaryMdDecoder.askPx(buffer, offset);
                lastAskQty = BinaryMdDecoder.askQty(buffer, offset);
            }
        }
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
        public void onOrderRejected(long orderId, String reason, long tsNanos) {
            consumed ^= orderId ^ tsNanos ^ reason.length();
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

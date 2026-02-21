package io.pulseengine.pipeline;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.OrderBook;
import io.pulseengine.core.OrderRequest;
import io.pulseengine.core.OrderType;
import io.pulseengine.core.RejectCode;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.BinaryMdPublisher;
import io.pulseengine.md.MdMessageType;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.persistence.OrderBookSnapshotStore;

import java.nio.file.Path;
import java.util.concurrent.ThreadFactory;

public final class EnginePipeline implements AutoCloseable {
    private static final byte RC_INVALID_QTY = RejectCode.INVALID_QTY;
    private static final byte RC_INVALID_SIDE = RejectCode.INVALID_ORDER;
    private static final byte RC_INVALID_ORDER_TYPE = RejectCode.INVALID_ORDER;

    private static final EventTranslatorOneArg<MdEvent, EngineEvent> MD_TRANSLATOR = (event, sequence, src) -> {
        event.tsNanos = src.ingressNanos;
        event.bidPx = src.postBidPx;
        event.bidQty = src.postBidQty;
        event.askPx = src.postAskPx;
        event.askQty = src.postAskQty;

        event.l3EventType = src.l3EventType;
        event.l3OrderId = src.l3OrderId;
        event.l3TradeId = src.l3TradeId;
        event.l3Side = src.l3Side;
        event.l3PriceTicks = src.l3PriceTicks;
        event.l3Qty = src.l3Qty;
        event.l3RemainingQty = src.l3RemainingQty;
    };

    private final Disruptor<EngineEvent> coreDisruptor;
    private final RingBuffer<EngineEvent> coreRing;

    private final Disruptor<MdEvent> mdDisruptor;
    private final RingBuffer<MdEvent> mdRing;
    private final boolean mdAsyncEnabled;

    private final OrderBook orderBook;
    private final TopOfBookView topOfBook;

    public EnginePipeline(
        int ringSize,
        MatchEventSink eventSink,
        SmpPolicy smpPolicy,
        TopOfBookView topOfBook,
        ThreadFactory threadFactory
    ) {
        this(ringSize, eventSink, smpPolicy, topOfBook, null, threadFactory);
    }

    public EnginePipeline(
        int ringSize,
        MatchEventSink eventSink,
        SmpPolicy smpPolicy,
        TopOfBookView topOfBook,
        BinaryMdPublisher mdPublisher,
        ThreadFactory threadFactory
    ) {
        this.orderBook = new OrderBook();
        this.topOfBook = topOfBook;

        if (mdPublisher != null) {
            EventFactory<MdEvent> mdFactory = MdEvent::new;
            this.mdDisruptor = new Disruptor<>(
                mdFactory,
                ringSize,
                namedFactory(threadFactory, "-md"),
                ProducerType.SINGLE,
                new BlockingWaitStrategy()
            );
            this.mdDisruptor.handleEventsWith(new MarketDataHandler(orderBook, topOfBook, mdPublisher));
            this.mdRing = mdDisruptor.start();
            this.mdAsyncEnabled = true;
        } else {
            this.mdDisruptor = null;
            this.mdRing = null;
            this.mdAsyncEnabled = false;
        }

        EventFactory<EngineEvent> coreFactory = EngineEvent::new;
        this.coreDisruptor = new Disruptor<>(
            coreFactory,
            ringSize,
            namedFactory(threadFactory, "-core"),
            ProducerType.SINGLE,
            new BlockingWaitStrategy()
        );

        RiskHandler riskHandler = new RiskHandler();
        MatchHandler matchHandler = new MatchHandler(orderBook, eventSink, smpPolicy, topOfBook, mdRing, mdAsyncEnabled);

        coreDisruptor.handleEventsWith(riskHandler).then(matchHandler);
        this.coreRing = coreDisruptor.start();
    }

    public void submitLimit(long orderId, long traderId, Side side, long priceTicks, long quantity, TimeInForce tif, long peakSize) {
        submitLimitAt(orderId, traderId, side, priceTicks, quantity, tif, peakSize, System.nanoTime());
    }

    public boolean trySubmitLimit(long orderId, long traderId, Side side, long priceTicks, long quantity, TimeInForce tif, long peakSize) {
        return trySubmitLimitAt(orderId, traderId, side, priceTicks, quantity, tif, peakSize, System.nanoTime());
    }

    public void submitLimitAt(
        long orderId,
        long traderId,
        Side side,
        long priceTicks,
        long quantity,
        TimeInForce tif,
        long peakSize,
        long ingressNanos
    ) {
        long seq = coreRing.next();
        try {
            EngineEvent e = coreRing.get(seq);
            e.commandType = EngineCommandType.NEW_ORDER;
            e.orderId = orderId;
            e.traderId = traderId;
            e.side = side;
            e.orderType = OrderType.LIMIT;
            e.tif = tif;
            e.priceTicks = priceTicks;
            e.quantity = quantity;
            e.peakSize = peakSize;
            e.ingressNanos = ingressNanos;
        } finally {
            coreRing.publish(seq);
        }
    }

    public boolean trySubmitLimitAt(
        long orderId,
        long traderId,
        Side side,
        long priceTicks,
        long quantity,
        TimeInForce tif,
        long peakSize,
        long ingressNanos
    ) {
        final long seq;
        try {
            seq = coreRing.tryNext();
        } catch (InsufficientCapacityException e) {
            return false;
        }
        try {
            EngineEvent ev = coreRing.get(seq);
            ev.commandType = EngineCommandType.NEW_ORDER;
            ev.orderId = orderId;
            ev.traderId = traderId;
            ev.side = side;
            ev.orderType = OrderType.LIMIT;
            ev.tif = tif;
            ev.priceTicks = priceTicks;
            ev.quantity = quantity;
            ev.peakSize = peakSize;
            ev.ingressNanos = ingressNanos;
        } finally {
            coreRing.publish(seq);
        }
        return true;
    }

    public void submitMarket(long orderId, long traderId, Side side, long quantity, TimeInForce tif) {
        submitMarketAt(orderId, traderId, side, quantity, tif, System.nanoTime());
    }

    public boolean trySubmitMarket(long orderId, long traderId, Side side, long quantity, TimeInForce tif) {
        return trySubmitMarketAt(orderId, traderId, side, quantity, tif, System.nanoTime());
    }

    public void submitMarketAt(
        long orderId,
        long traderId,
        Side side,
        long quantity,
        TimeInForce tif,
        long ingressNanos
    ) {
        long seq = coreRing.next();
        try {
            EngineEvent e = coreRing.get(seq);
            e.commandType = EngineCommandType.NEW_ORDER;
            e.orderId = orderId;
            e.traderId = traderId;
            e.side = side;
            e.orderType = OrderType.MARKET;
            e.tif = tif;
            e.quantity = quantity;
            e.ingressNanos = ingressNanos;
        } finally {
            coreRing.publish(seq);
        }
    }

    public boolean trySubmitMarketAt(
        long orderId,
        long traderId,
        Side side,
        long quantity,
        TimeInForce tif,
        long ingressNanos
    ) {
        final long seq;
        try {
            seq = coreRing.tryNext();
        } catch (InsufficientCapacityException e) {
            return false;
        }
        try {
            EngineEvent ev = coreRing.get(seq);
            ev.commandType = EngineCommandType.NEW_ORDER;
            ev.orderId = orderId;
            ev.traderId = traderId;
            ev.side = side;
            ev.orderType = OrderType.MARKET;
            ev.tif = tif;
            ev.quantity = quantity;
            ev.ingressNanos = ingressNanos;
        } finally {
            coreRing.publish(seq);
        }
        return true;
    }

    public void submitStopMarket(long orderId, long traderId, Side side, long stopPriceTicks, long quantity) {
        submitStopMarketAt(orderId, traderId, side, stopPriceTicks, quantity, System.nanoTime());
    }

    public boolean trySubmitStopMarket(long orderId, long traderId, Side side, long stopPriceTicks, long quantity) {
        return trySubmitStopMarketAt(orderId, traderId, side, stopPriceTicks, quantity, System.nanoTime());
    }

    public void submitStopMarketAt(
        long orderId,
        long traderId,
        Side side,
        long stopPriceTicks,
        long quantity,
        long ingressNanos
    ) {
        long seq = coreRing.next();
        try {
            EngineEvent e = coreRing.get(seq);
            e.commandType = EngineCommandType.NEW_ORDER;
            e.orderId = orderId;
            e.traderId = traderId;
            e.side = side;
            e.orderType = OrderType.STOP_MARKET;
            e.tif = TimeInForce.GTC;
            e.stopPriceTicks = stopPriceTicks;
            e.quantity = quantity;
            e.ingressNanos = ingressNanos;
        } finally {
            coreRing.publish(seq);
        }
    }

    public boolean trySubmitStopMarketAt(
        long orderId,
        long traderId,
        Side side,
        long stopPriceTicks,
        long quantity,
        long ingressNanos
    ) {
        final long seq;
        try {
            seq = coreRing.tryNext();
        } catch (InsufficientCapacityException e) {
            return false;
        }
        try {
            EngineEvent ev = coreRing.get(seq);
            ev.commandType = EngineCommandType.NEW_ORDER;
            ev.orderId = orderId;
            ev.traderId = traderId;
            ev.side = side;
            ev.orderType = OrderType.STOP_MARKET;
            ev.tif = TimeInForce.GTC;
            ev.stopPriceTicks = stopPriceTicks;
            ev.quantity = quantity;
            ev.ingressNanos = ingressNanos;
        } finally {
            coreRing.publish(seq);
        }
        return true;
    }

    public void submitCancel(long cancelOrderId) {
        submitCancelAt(cancelOrderId, System.nanoTime());
    }

    public boolean trySubmitCancel(long cancelOrderId) {
        return trySubmitCancelAt(cancelOrderId, System.nanoTime());
    }

    public void submitCancelAt(long cancelOrderId, long ingressNanos) {
        long seq = coreRing.next();
        try {
            EngineEvent e = coreRing.get(seq);
            e.commandType = EngineCommandType.CANCEL;
            e.cancelOrderId = cancelOrderId;
            e.ingressNanos = ingressNanos;
        } finally {
            coreRing.publish(seq);
        }
    }

    public boolean trySubmitCancelAt(long cancelOrderId, long ingressNanos) {
        final long seq;
        try {
            seq = coreRing.tryNext();
        } catch (InsufficientCapacityException e) {
            return false;
        }
        try {
            EngineEvent ev = coreRing.get(seq);
            ev.commandType = EngineCommandType.CANCEL;
            ev.cancelOrderId = cancelOrderId;
            ev.ingressNanos = ingressNanos;
        } finally {
            coreRing.publish(seq);
        }
        return true;
    }

    public TopOfBookView topOfBook() {
        return topOfBook;
    }

    public void saveStateSnapshot(Path path) {
        OrderBookSnapshotStore.write(path, orderBook);
    }

    public void loadStateSnapshot(Path path) {
        OrderBookSnapshotStore.load(path, orderBook);
        topOfBook.publish(orderBook.bestBid(), orderBook.bestAsk());
    }

    @Override
    public void close() {
        coreDisruptor.shutdown();
        if (mdDisruptor != null) {
            mdDisruptor.shutdown();
        }
    }

    private static ThreadFactory namedFactory(ThreadFactory base, String suffix) {
        return runnable -> {
            Thread t = base.newThread(runnable);
            if (t.getName() == null || t.getName().isEmpty()) {
                t.setName("pulseengine" + suffix);
            } else {
                t.setName(t.getName() + suffix);
            }
            return t;
        };
    }

    private static final class RiskHandler implements EventHandler<EngineEvent> {
        @Override
        public void onEvent(EngineEvent event, long sequence, boolean endOfBatch) {
            if (event.commandType == EngineCommandType.CANCEL) {
                return;
            }
            if (event.quantity <= 0) {
                event.rejected = true;
                event.rejectCode = RC_INVALID_QTY;
                return;
            }
            if (event.side == null) {
                event.rejected = true;
                event.rejectCode = RC_INVALID_SIDE;
                return;
            }
            if (event.orderType == null) {
                event.rejected = true;
                event.rejectCode = RC_INVALID_ORDER_TYPE;
            }
        }
    }

    private static final class MatchHandler implements EventHandler<EngineEvent> {
        private final OrderBook orderBook;
        private final MatchEventSink sink;
        private final SmpPolicy smpPolicy;
        private final TopOfBookView topOfBook;
        private final RingBuffer<MdEvent> mdRing;
        private final boolean mdAsyncEnabled;
        private final RecordingSink recordingSink = new RecordingSink();

        private MatchHandler(
            OrderBook orderBook,
            MatchEventSink sink,
            SmpPolicy smpPolicy,
            TopOfBookView topOfBook,
            RingBuffer<MdEvent> mdRing,
            boolean mdAsyncEnabled
        ) {
            this.orderBook = orderBook;
            this.sink = sink;
            this.smpPolicy = smpPolicy;
            this.topOfBook = topOfBook;
            this.mdRing = mdRing;
            this.mdAsyncEnabled = mdAsyncEnabled;
        }

        @Override
        public void onEvent(EngineEvent event, long sequence, boolean endOfBatch) {
            if (event.rejected) {
                sink.onOrderRejected(event.orderId, event.rejectCode, event.ingressNanos);
                event.l3EventType = 0;
                captureBookState(event);
                publishMd(event);
                event.reset();
                return;
            }

            if (event.commandType == EngineCommandType.CANCEL) {
                recordingSink.attach(event, sink);
                boolean canceled = orderBook.cancel(event.cancelOrderId, recordingSink, event.ingressNanos);
                if (!canceled) {
                    sink.onOrderRejected(event.cancelOrderId, RejectCode.UNKNOWN_CANCEL, event.ingressNanos);
                    event.l3EventType = 0;
                }
                captureBookState(event);
                publishMd(event);
                event.reset();
                return;
            }

            OrderRequest req = event.reusableOrderRequest;
            req.orderId = event.orderId;
            req.traderId = event.traderId;
            req.side = event.side;
            req.type = event.orderType;
            req.tif = event.tif;
            req.priceTicks = event.priceTicks;
            req.stopPriceTicks = event.stopPriceTicks;
            req.quantity = event.quantity;
            req.peakSize = event.peakSize;

            recordingSink.attach(event, sink);
            orderBook.process(req, recordingSink, smpPolicy, event.ingressNanos);
            captureBookState(event);
            publishMd(event);
            event.reset();
        }

        private void publishMd(EngineEvent event) {
            if (mdAsyncEnabled) {
                mdRing.publishEvent(MD_TRANSLATOR, event);
            } else {
                topOfBook.publish(event.postBidPx, event.postAskPx);
            }
        }

        private void captureBookState(EngineEvent event) {
            event.postBidPx = orderBook.bestBid();
            event.postBidQty = orderBook.bestBidQty();
            event.postAskPx = orderBook.bestAsk();
            event.postAskQty = orderBook.bestAskQty();
        }

        private static final class RecordingSink implements MatchEventSink {
            private EngineEvent event;
            private MatchEventSink downstream;

            void attach(EngineEvent event, MatchEventSink downstream) {
                this.event = event;
                this.downstream = downstream;
                event.l3EventType = 0;
            }

            @Override
            public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
                downstream.onTrade(tradeId, buyOrderId, sellOrderId, priceTicks, quantity, tsNanos);
                event.l3EventType = MdMessageType.L3_TRADE;
                event.l3TradeId = tradeId;
                event.l3OrderId = event.side == Side.BUY ? buyOrderId : sellOrderId;
                event.l3Side = event.side;
                event.l3PriceTicks = priceTicks;
                event.l3Qty = quantity;
                event.l3RemainingQty = 0;
            }

            @Override
            public void onOrderAccepted(long orderId, long openQty, long tsNanos) {
                downstream.onOrderAccepted(orderId, openQty, tsNanos);
                event.l3EventType = MdMessageType.L3_ADD;
                event.l3OrderId = orderId;
                event.l3TradeId = 0;
                event.l3Side = event.side;
                event.l3PriceTicks = event.priceTicks;
                event.l3Qty = openQty;
                event.l3RemainingQty = openQty;
            }

            @Override
            public void onOrderRejected(long orderId, byte reasonCode, long tsNanos) {
                downstream.onOrderRejected(orderId, reasonCode, tsNanos);
            }

            @Override
            public void onOrderCanceled(long orderId, long remainingQty, long tsNanos) {
                downstream.onOrderCanceled(orderId, remainingQty, tsNanos);
                event.l3EventType = MdMessageType.L3_CANCEL;
                event.l3OrderId = orderId;
                event.l3TradeId = 0;
                event.l3Side = event.side;
                event.l3PriceTicks = event.priceTicks;
                event.l3Qty = remainingQty;
                event.l3RemainingQty = 0;
            }

            @Override
            public void onOrderFilled(long orderId, long tsNanos) {
                downstream.onOrderFilled(orderId, tsNanos);
            }

            @Override
            public void onOrderPartiallyFilled(long orderId, long remainingQty, long tsNanos) {
                downstream.onOrderPartiallyFilled(orderId, remainingQty, tsNanos);
                event.l3EventType = MdMessageType.L3_MODIFY;
                event.l3OrderId = orderId;
                event.l3TradeId = 0;
                event.l3Side = event.side;
                event.l3PriceTicks = event.priceTicks;
                event.l3Qty = 0;
                event.l3RemainingQty = remainingQty;
            }
        }
    }

    private static final class MarketDataHandler implements EventHandler<MdEvent> {
        private static final int DEPTH = 5;
        private static final int DEPTH_SNAPSHOT_EVERY = 50_000;

        private final OrderBook orderBook;
        private final TopOfBookView topOfBook;
        private final BinaryMdPublisher mdPublisher;
        private final long[] bidPx = new long[DEPTH];
        private final long[] bidQty = new long[DEPTH];
        private final long[] askPx = new long[DEPTH];
        private final long[] askQty = new long[DEPTH];

        private long published;
        private long pendingTs;
        private long pendingBidPx;
        private long pendingBidQty;
        private long pendingAskPx;
        private long pendingAskQty;
        private boolean hasPendingL2;

        private MarketDataHandler(OrderBook orderBook, TopOfBookView topOfBook, BinaryMdPublisher mdPublisher) {
            this.orderBook = orderBook;
            this.topOfBook = topOfBook;
            this.mdPublisher = mdPublisher;
        }

        @Override
        public void onEvent(MdEvent event, long sequence, boolean endOfBatch) {
            topOfBook.publish(event.bidPx, event.askPx);

            if (mdPublisher != null) {
                pendingTs = event.tsNanos;
                pendingBidPx = event.bidPx;
                pendingBidQty = event.bidQty;
                pendingAskPx = event.askPx;
                pendingAskQty = event.askQty;
                hasPendingL2 = true;

                if (event.l3EventType != 0) {
                    mdPublisher.publishL3Incremental(
                        event.tsNanos,
                        event.l3EventType,
                        event.l3OrderId,
                        event.l3TradeId,
                        event.l3Side == Side.SELL ? (byte) 1 : (byte) 0,
                        event.l3PriceTicks,
                        event.l3Qty,
                        event.l3RemainingQty
                    );
                }

                published++;
                if (published == 1 || (published % DEPTH_SNAPSHOT_EVERY) == 0) {
                    int depth = orderBook.snapshotDepth(DEPTH, bidPx, bidQty, askPx, askQty);
                    if (depth > 0) {
                        mdPublisher.publishDepthSnapshot(event.tsNanos, depth, bidPx, bidQty, askPx, askQty);
                    }
                }

                if (endOfBatch && hasPendingL2) {
                    mdPublisher.publishIncrementalIfChanged(
                        pendingTs,
                        pendingBidPx,
                        pendingBidQty,
                        pendingAskPx,
                        pendingAskQty
                    );
                    hasPendingL2 = false;
                }
            }

            event.reset();
        }
    }
}

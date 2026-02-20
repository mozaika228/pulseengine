package io.pulseengine.core;

import org.agrona.collections.Long2ObjectHashMap;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class OrderBook {
    private final NavigableMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Long2ObjectHashMap<BookOrder> liveOrders = new Long2ObjectHashMap<>();

    private final Deque<BookOrder> orderPool = new ArrayDeque<>();
    private final Deque<StopOrder> stopBuys = new ArrayDeque<>();
    private final Deque<StopOrder> stopSells = new ArrayDeque<>();
    private final Deque<StopOrder> stopPool = new ArrayDeque<>();

    private long tradeSeq;
    private long lastTradePrice;

    public void process(OrderRequest req, MatchEventSink sink, SmpPolicy smpPolicy, long tsNanos) {
        if (req.quantity <= 0) {
            sink.onOrderRejected(req.orderId, "qty<=0", tsNanos);
            return;
        }

        if (req.type == OrderType.STOP_MARKET) {
            enqueueStop(req, sink, tsNanos);
            return;
        }

        long effectivePrice = req.type == OrderType.MARKET
            ? (req.side == Side.BUY ? Long.MAX_VALUE : 0)
            : req.priceTicks;

        if (req.tif == TimeInForce.FOK && !canFullyFill(req.side, req.type, effectivePrice, req.quantity)) {
            sink.onOrderRejected(req.orderId, "FOK_unfilled", tsNanos);
            return;
        }

        BookOrder aggressor = borrowOrder();
        aggressor.init(req);
        aggressor.priceTicks = effectivePrice;

        long remaining = match(aggressor, sink, smpPolicy, tsNanos);

        if (remaining == 0) {
            sink.onOrderFilled(req.orderId, tsNanos);
            recycleOrder(aggressor);
        } else if (req.tif == TimeInForce.IOC || req.type == OrderType.MARKET) {
            sink.onOrderCanceled(req.orderId, remaining, tsNanos);
            recycleOrder(aggressor);
        } else {
            aggressor.openQty = remaining;
            aggressor.visibleQty = aggressor.isIceberg() ? Math.min(aggressor.peakSize, remaining) : remaining;
            addToBook(aggressor);
            liveOrders.put(aggressor.orderId, aggressor);
            sink.onOrderAccepted(req.orderId, remaining, tsNanos);
        }

        activateStops(sink, smpPolicy, tsNanos);
    }

    public boolean cancel(long orderId, MatchEventSink sink, long tsNanos) {
        BookOrder order = liveOrders.remove(orderId);
        if (order == null) {
            return false;
        }
        PriceLevel level = order.level;
        level.remove(order);
        if (level.isEmpty()) {
            bookSide(order.side).remove(level.priceTicks);
        }
        sink.onOrderCanceled(orderId, order.openQty, tsNanos);
        recycleOrder(order);
        return true;
    }

    private long match(BookOrder aggressor, MatchEventSink sink, SmpPolicy smpPolicy, long tsNanos) {
        NavigableMap<Long, PriceLevel> passiveBook = aggressor.side == Side.BUY ? asks : bids;

        while (aggressor.openQty > 0 && !passiveBook.isEmpty()) {
            PriceLevel bestLevel = passiveBook.firstEntry().getValue();
            if (!crosses(aggressor.side, aggressor.priceTicks, bestLevel.priceTicks)) {
                break;
            }

            BookOrder passive = bestLevel.head;
            while (passive != null && aggressor.openQty > 0) {
                BookOrder nextPassive = passive.next;
                if (smpPolicy == SmpPolicy.CANCEL_AGGRESSOR && passive.traderId == aggressor.traderId) {
                    return aggressor.openQty;
                }

                long tradeQty = Math.min(aggressor.openQty, passive.visibleQty);
                aggressor.openQty -= tradeQty;
                passive.openQty -= tradeQty;
                passive.visibleQty -= tradeQty;
                lastTradePrice = bestLevel.priceTicks;

                long tradeId = ++tradeSeq;
                long buyOrderId = aggressor.side == Side.BUY ? aggressor.orderId : passive.orderId;
                long sellOrderId = aggressor.side == Side.SELL ? aggressor.orderId : passive.orderId;
                sink.onTrade(tradeId, buyOrderId, sellOrderId, bestLevel.priceTicks, tradeQty, tsNanos);

                if (passive.openQty == 0) {
                    bestLevel.remove(passive);
                    liveOrders.remove(passive.orderId);
                    sink.onOrderFilled(passive.orderId, tsNanos);
                    recycleOrder(passive);
                } else if (passive.visibleQty == 0 && passive.isIceberg()) {
                    passive.visibleQty = Math.min(passive.peakSize, passive.openQty);
                    bestLevel.moveToTail(passive);
                    sink.onOrderPartiallyFilled(passive.orderId, passive.openQty, tsNanos);
                } else {
                    sink.onOrderPartiallyFilled(passive.orderId, passive.openQty, tsNanos);
                }
                passive = nextPassive;
            }

            if (bestLevel.isEmpty()) {
                passiveBook.remove(bestLevel.priceTicks);
            }
        }

        return aggressor.openQty;
    }

    private boolean crosses(Side side, long aggressorPrice, long passivePrice) {
        if (side == Side.BUY) {
            return aggressorPrice >= passivePrice;
        }
        return aggressorPrice <= passivePrice;
    }

    private boolean canFullyFill(Side side, OrderType type, long priceTicks, long quantity) {
        long need = quantity;
        NavigableMap<Long, PriceLevel> passiveBook = side == Side.BUY ? asks : bids;

        for (PriceLevel level : passiveBook.values()) {
            if (type == OrderType.LIMIT && !crosses(side, priceTicks, level.priceTicks)) {
                break;
            }
            BookOrder order = level.head;
            while (order != null && need > 0) {
                need -= order.visibleQty;
                order = order.next;
            }
            if (need <= 0) {
                return true;
            }
        }
        return false;
    }

    private void addToBook(BookOrder order) {
        NavigableMap<Long, PriceLevel> sideBook = bookSide(order.side);
        PriceLevel level = sideBook.get(order.priceTicks);
        if (level == null) {
            level = new PriceLevel(order.priceTicks);
            sideBook.put(order.priceTicks, level);
        }
        level.addLast(order);
    }

    private NavigableMap<Long, PriceLevel> bookSide(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    private void enqueueStop(OrderRequest req, MatchEventSink sink, long tsNanos) {
        StopOrder stop = borrowStop();
        stop.orderId = req.orderId;
        stop.traderId = req.traderId;
        stop.side = req.side;
        stop.stopPriceTicks = req.stopPriceTicks;
        stop.quantity = req.quantity;

        if (req.side == Side.BUY) {
            stopBuys.addLast(stop);
        } else {
            stopSells.addLast(stop);
        }
        sink.onOrderAccepted(req.orderId, req.quantity, tsNanos);
    }

    private void activateStops(MatchEventSink sink, SmpPolicy smpPolicy, long tsNanos) {
        if (lastTradePrice == 0) {
            return;
        }

        int buyCount = stopBuys.size();
        for (int i = 0; i < buyCount; i++) {
            StopOrder stop = stopBuys.removeFirst();
            if (lastTradePrice >= stop.stopPriceTicks) {
                executeTriggeredStop(stop, sink, smpPolicy, tsNanos);
                recycleStop(stop);
            } else {
                stopBuys.addLast(stop);
            }
        }

        int sellCount = stopSells.size();
        for (int i = 0; i < sellCount; i++) {
            StopOrder stop = stopSells.removeFirst();
            if (lastTradePrice <= stop.stopPriceTicks) {
                executeTriggeredStop(stop, sink, smpPolicy, tsNanos);
                recycleStop(stop);
            } else {
                stopSells.addLast(stop);
            }
        }
    }

    private void executeTriggeredStop(StopOrder stop, MatchEventSink sink, SmpPolicy smpPolicy, long tsNanos) {
        BookOrder aggressor = borrowOrder();
        aggressor.orderId = stop.orderId;
        aggressor.traderId = stop.traderId;
        aggressor.side = stop.side;
        aggressor.openQty = stop.quantity;
        aggressor.visibleQty = stop.quantity;
        aggressor.priceTicks = stop.side == Side.BUY ? Long.MAX_VALUE : 0;
        aggressor.peakSize = 0;

        long remaining = match(aggressor, sink, smpPolicy, tsNanos);
        if (remaining == 0) {
            sink.onOrderFilled(stop.orderId, tsNanos);
        } else {
            sink.onOrderCanceled(stop.orderId, remaining, tsNanos);
        }
        recycleOrder(aggressor);
    }

    public long bestBid() {
        return bids.isEmpty() ? 0 : bids.firstKey();
    }

    public long bestBidQty() {
        if (bids.isEmpty()) {
            return 0;
        }
        PriceLevel level = bids.firstEntry().getValue();
        return level.head == null ? 0 : level.head.visibleQty;
    }

    public long bestAsk() {
        return asks.isEmpty() ? 0 : asks.firstKey();
    }

    public long bestAskQty() {
        if (asks.isEmpty()) {
            return 0;
        }
        PriceLevel level = asks.firstEntry().getValue();
        return level.head == null ? 0 : level.head.visibleQty;
    }

    public int snapshotDepth(
        int depth,
        long[] bidPx,
        long[] bidQty,
        long[] askPx,
        long[] askQty
    ) {
        int bidCount = 0;
        for (PriceLevel level : bids.values()) {
            if (bidCount >= depth) {
                break;
            }
            bidPx[bidCount] = level.priceTicks;
            bidQty[bidCount] = level.totalVisibleQty();
            bidCount++;
        }

        int askCount = 0;
        for (PriceLevel level : asks.values()) {
            if (askCount >= depth) {
                break;
            }
            askPx[askCount] = level.priceTicks;
            askQty[askCount] = level.totalVisibleQty();
            askCount++;
        }
        return Math.min(bidCount, askCount);
    }

    private BookOrder borrowOrder() {
        BookOrder order = orderPool.pollFirst();
        return order != null ? order : new BookOrder();
    }

    private void recycleOrder(BookOrder order) {
        order.orderId = 0;
        order.traderId = 0;
        order.side = null;
        order.priceTicks = 0;
        order.openQty = 0;
        order.visibleQty = 0;
        order.peakSize = 0;
        order.sequence = 0;
        order.prev = null;
        order.next = null;
        order.level = null;
        orderPool.addFirst(order);
    }

    private StopOrder borrowStop() {
        StopOrder stop = stopPool.pollFirst();
        return stop != null ? stop : new StopOrder();
    }

    private void recycleStop(StopOrder stop) {
        stop.orderId = 0;
        stop.traderId = 0;
        stop.side = null;
        stop.stopPriceTicks = 0;
        stop.quantity = 0;
        stopPool.addFirst(stop);
    }

    private static final class StopOrder {
        long orderId;
        long traderId;
        Side side;
        long stopPriceTicks;
        long quantity;
    }
}

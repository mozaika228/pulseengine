package io.pulseengine.core;

import org.agrona.collections.Long2ObjectHashMap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class OrderBook {
    private static final int SNAPSHOT_MAGIC = 0x50455331;
    private static final int SNAPSHOT_VERSION = 1;
    private static final int SNAPSHOT_HEADER_BYTES = 4 + 4 + 8 + 8 + 4 + 4 + 4;
    private static final int SNAPSHOT_ORDER_BYTES = 8 + 8 + 1 + 8 + 8 + 8 + 8 + 8;
    private static final int SNAPSHOT_STOP_BYTES = 8 + 8 + 1 + 8 + 8;

    private static final int DEFAULT_MAX_LEVELS_PER_SIDE = 4096;
    private static final int DEFAULT_MAX_ORDERS = 262144;
    private static final int DEFAULT_MAX_STOPS_PER_SIDE = 65536;

    private final int maxLevelsPerSide;
    private final PriceLevel[] bids;
    private final PriceLevel[] asks;
    private final PriceLevel[] bidLevelPool;
    private final PriceLevel[] askLevelPool;
    private int freeBidLevels;
    private int freeAskLevels;

    private final Long2ObjectHashMap<BookOrder> liveOrders;
    private final BookOrder[] orderPool;
    private int freeOrders;

    private final StopOrder[] stopPool;
    private int freeStops;
    private final StopQueue stopBuys;
    private final StopQueue stopSells;

    private int bidCount;
    private int askCount;
    private long tradeSeq;
    private long lastTradePrice;

    public OrderBook() {
        this(DEFAULT_MAX_LEVELS_PER_SIDE, DEFAULT_MAX_ORDERS, DEFAULT_MAX_STOPS_PER_SIDE);
    }

    public OrderBook(int maxLevelsPerSide, int maxOrders, int maxStopsPerSide) {
        if (maxLevelsPerSide <= 0 || maxOrders <= 0 || maxStopsPerSide <= 0) {
            throw new IllegalArgumentException("OrderBook capacities must be positive");
        }

        this.maxLevelsPerSide = maxLevelsPerSide;
        this.bids = new PriceLevel[maxLevelsPerSide];
        this.asks = new PriceLevel[maxLevelsPerSide];
        this.bidLevelPool = new PriceLevel[maxLevelsPerSide];
        this.askLevelPool = new PriceLevel[maxLevelsPerSide];
        this.freeBidLevels = maxLevelsPerSide;
        this.freeAskLevels = maxLevelsPerSide;
        for (int i = 0; i < maxLevelsPerSide; i++) {
            bidLevelPool[i] = new PriceLevel();
            askLevelPool[i] = new PriceLevel();
        }

        int mapCapacity = Math.max(8, maxOrders * 2);
        this.liveOrders = new Long2ObjectHashMap<>(mapCapacity, 0.65f);
        this.orderPool = new BookOrder[maxOrders];
        this.freeOrders = maxOrders;
        for (int i = 0; i < maxOrders; i++) {
            orderPool[i] = new BookOrder();
        }

        this.stopPool = new StopOrder[maxStopsPerSide * 2];
        this.freeStops = stopPool.length;
        for (int i = 0; i < stopPool.length; i++) {
            stopPool[i] = new StopOrder();
        }
        this.stopBuys = new StopQueue(maxStopsPerSide);
        this.stopSells = new StopQueue(maxStopsPerSide);
    }

    public void process(OrderRequest req, MatchEventSink sink, SmpPolicy smpPolicy, long tsNanos) {
        if (req.quantity <= 0) {
            sink.onOrderRejected(req.orderId, RejectCode.INVALID_QTY, tsNanos);
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
            sink.onOrderRejected(req.orderId, RejectCode.FOK_UNFILLED, tsNanos);
            return;
        }

        BookOrder aggressor = borrowOrder();
        if (aggressor == null) {
            sink.onOrderRejected(req.orderId, RejectCode.CAPACITY_EXCEEDED, tsNanos);
            return;
        }

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
            if (!addToBook(aggressor)) {
                sink.onOrderRejected(req.orderId, RejectCode.CAPACITY_EXCEEDED, tsNanos);
                recycleOrder(aggressor);
            } else {
                liveOrders.put(aggressor.orderId, aggressor);
                sink.onOrderAccepted(req.orderId, remaining, tsNanos);
            }
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
            removeEmptyLevel(order.side, level.priceTicks);
        }
        sink.onOrderCanceled(orderId, order.openQty, tsNanos);
        recycleOrder(order);
        return true;
    }

    private long match(BookOrder aggressor, MatchEventSink sink, SmpPolicy smpPolicy, long tsNanos) {
        PriceLevel[] passiveLevels = aggressor.side == Side.BUY ? asks : bids;
        int passiveCount = aggressor.side == Side.BUY ? askCount : bidCount;

        while (aggressor.openQty > 0 && passiveCount > 0) {
            PriceLevel bestLevel = passiveLevels[0];
            if (!crosses(aggressor.side, aggressor.priceTicks, bestLevel.priceTicks)) {
                break;
            }

            BookOrder passive = bestLevel.head;
            while (passive != null && aggressor.openQty > 0) {
                BookOrder nextPassive = passive.next;
                if (smpPolicy == SmpPolicy.CANCEL_AGGRESSOR && passive.traderId == aggressor.traderId) {
                    updatePassiveCount(aggressor.side, passiveCount);
                    return aggressor.openQty;
                }

                long tradeQty = Math.min(aggressor.openQty, passive.visibleQty);
                aggressor.openQty -= tradeQty;
                passive.openQty -= tradeQty;
                passive.visibleQty -= tradeQty;
                bestLevel.totalVisibleQty -= tradeQty;
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
                    bestLevel.totalVisibleQty += passive.visibleQty;
                    bestLevel.moveToTail(passive);
                    sink.onOrderPartiallyFilled(passive.orderId, passive.openQty, tsNanos);
                } else {
                    sink.onOrderPartiallyFilled(passive.orderId, passive.openQty, tsNanos);
                }
                passive = nextPassive;
            }

            if (bestLevel.isEmpty()) {
                passiveCount = removeFirstLevel(aggressor.side == Side.BUY ? Side.SELL : Side.BUY);
            } else {
                passiveCount = aggressor.side == Side.BUY ? askCount : bidCount;
            }
        }

        updatePassiveCount(aggressor.side, passiveCount);
        return aggressor.openQty;
    }

    private void updatePassiveCount(Side aggressorSide, int passiveCount) {
        if (aggressorSide == Side.BUY) {
            askCount = passiveCount;
        } else {
            bidCount = passiveCount;
        }
    }

    private boolean crosses(Side side, long aggressorPrice, long passivePrice) {
        if (side == Side.BUY) {
            return aggressorPrice >= passivePrice;
        }
        return aggressorPrice <= passivePrice;
    }

    private boolean canFullyFill(Side side, OrderType type, long priceTicks, long quantity) {
        long need = quantity;
        PriceLevel[] passiveLevels = side == Side.BUY ? asks : bids;
        int passiveCount = side == Side.BUY ? askCount : bidCount;

        for (int i = 0; i < passiveCount; i++) {
            PriceLevel level = passiveLevels[i];
            if (type == OrderType.LIMIT && !crosses(side, priceTicks, level.priceTicks)) {
                break;
            }
            need -= level.totalVisibleQty();
            if (need <= 0) {
                return true;
            }
        }
        return false;
    }    private boolean addToBook(BookOrder order) {
        PriceLevel[] sideBook = levelsFor(order.side);
        int count = levelCount(order.side);
        int pos = findInsertPos(sideBook, count, order.priceTicks, order.side == Side.BUY);

        PriceLevel level;
        if (pos < count && sideBook[pos].priceTicks == order.priceTicks) {
            level = sideBook[pos];
        } else {
            if (count >= maxLevelsPerSide) {
                return false;
            }
            level = borrowLevel(order.side);
            if (level == null) {
                return false;
            }
            level.init(order.priceTicks);
            shiftRight(sideBook, count, pos);
            sideBook[pos] = level;
            count++;
            setLevelCount(order.side, count);
        }
        level.addLast(order);
        return true;
    }

    private int removeFirstLevel(Side side) {
        PriceLevel[] sideBook = levelsFor(side);
        int count = levelCount(side);
        recycleLevel(side, sideBook[0]);
        shiftLeft(sideBook, count, 0);
        count--;
        setLevelCount(side, count);
        return count;
    }

    private void removeEmptyLevel(Side side, long priceTicks) {
        PriceLevel[] sideBook = levelsFor(side);
        int count = levelCount(side);
        for (int i = 0; i < count; i++) {
            if (sideBook[i].priceTicks == priceTicks) {
                recycleLevel(side, sideBook[i]);
                shiftLeft(sideBook, count, i);
                setLevelCount(side, count - 1);
                return;
            }
        }
    }

    private PriceLevel[] levelsFor(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    private int levelCount(Side side) {
        return side == Side.BUY ? bidCount : askCount;
    }

    private void setLevelCount(Side side, int count) {
        if (side == Side.BUY) {
            bidCount = count;
        } else {
            askCount = count;
        }
    }

    private void enqueueStop(OrderRequest req, MatchEventSink sink, long tsNanos) {
        StopOrder stop = borrowStop();
        if (stop == null) {
            sink.onOrderRejected(req.orderId, RejectCode.CAPACITY_EXCEEDED, tsNanos);
            return;
        }

        stop.orderId = req.orderId;
        stop.traderId = req.traderId;
        stop.side = req.side;
        stop.stopPriceTicks = req.stopPriceTicks;
        stop.quantity = req.quantity;

        StopQueue queue = req.side == Side.BUY ? stopBuys : stopSells;
        if (!queue.offer(stop)) {
            recycleStop(stop);
            sink.onOrderRejected(req.orderId, RejectCode.CAPACITY_EXCEEDED, tsNanos);
            return;
        }
        sink.onOrderAccepted(req.orderId, req.quantity, tsNanos);
    }

    private void activateStops(MatchEventSink sink, SmpPolicy smpPolicy, long tsNanos) {
        if (lastTradePrice == 0) {
            return;
        }

        int buyCountSnapshot = stopBuys.size();
        for (int i = 0; i < buyCountSnapshot; i++) {
            StopOrder stop = stopBuys.pollFirst();
            if (stop == null) {
                break;
            }
            if (lastTradePrice >= stop.stopPriceTicks) {
                executeTriggeredStop(stop, sink, smpPolicy, tsNanos);
                recycleStop(stop);
            } else {
                stopBuys.offer(stop);
            }
        }

        int sellCountSnapshot = stopSells.size();
        for (int i = 0; i < sellCountSnapshot; i++) {
            StopOrder stop = stopSells.pollFirst();
            if (stop == null) {
                break;
            }
            if (lastTradePrice <= stop.stopPriceTicks) {
                executeTriggeredStop(stop, sink, smpPolicy, tsNanos);
                recycleStop(stop);
            } else {
                stopSells.offer(stop);
            }
        }
    }

    private void executeTriggeredStop(StopOrder stop, MatchEventSink sink, SmpPolicy smpPolicy, long tsNanos) {
        BookOrder aggressor = borrowOrder();
        if (aggressor == null) {
            sink.onOrderRejected(stop.orderId, RejectCode.CAPACITY_EXCEEDED, tsNanos);
            return;
        }

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
        return bidCount == 0 ? 0 : bids[0].priceTicks;
    }

    public long bestBidQty() {
        return bidCount == 0 ? 0 : bids[0].totalVisibleQty();
    }

    public long bestAsk() {
        return askCount == 0 ? 0 : asks[0].priceTicks;
    }

    public long bestAskQty() {
        return askCount == 0 ? 0 : asks[0].totalVisibleQty();
    }

    public int snapshotDepth(int depth, long[] bidPx, long[] bidQty, long[] askPx, long[] askQty) {
        int bidDepth = Math.min(depth, bidCount);
        for (int i = 0; i < bidDepth; i++) {
            bidPx[i] = bids[i].priceTicks;
            bidQty[i] = bids[i].totalVisibleQty();
        }

        int askDepth = Math.min(depth, askCount);
        for (int i = 0; i < askDepth; i++) {
            askPx[i] = asks[i].priceTicks;
            askQty[i] = asks[i].totalVisibleQty();
        }
        return Math.min(bidDepth, askDepth);
    }

    public int snapshotSizeBytes() {
        int live = liveOrders.size();
        return SNAPSHOT_HEADER_BYTES + (live * SNAPSHOT_ORDER_BYTES) + ((stopBuys.size() + stopSells.size()) * SNAPSHOT_STOP_BYTES);
    }

    public void writeSnapshot(ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int live = liveOrders.size();
        int buyStops = stopBuys.size();
        int sellStops = stopSells.size();
        int required = SNAPSHOT_HEADER_BYTES + (live * SNAPSHOT_ORDER_BYTES) + ((buyStops + sellStops) * SNAPSHOT_STOP_BYTES);
        if (buffer.remaining() < required) {
            throw new IllegalArgumentException("Snapshot buffer too small: need=" + required + " have=" + buffer.remaining());
        }

        buffer.putInt(SNAPSHOT_MAGIC);
        buffer.putInt(SNAPSHOT_VERSION);
        buffer.putLong(tradeSeq);
        buffer.putLong(lastTradePrice);
        buffer.putInt(live);
        buffer.putInt(buyStops);
        buffer.putInt(sellStops);

        writeLiveSide(buffer, bids, bidCount);
        writeLiveSide(buffer, asks, askCount);
        writeStops(buffer, stopBuys);
        writeStops(buffer, stopSells);
    }

    public void loadSnapshot(ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        if (magic != SNAPSHOT_MAGIC) {
            throw new IllegalArgumentException("Bad snapshot magic");
        }
        int version = buffer.getInt();
        if (version != SNAPSHOT_VERSION) {
            throw new IllegalArgumentException("Unsupported snapshot version: " + version);
        }

        clearState();
        tradeSeq = buffer.getLong();
        lastTradePrice = buffer.getLong();
        int liveCount = buffer.getInt();
        int buyStops = buffer.getInt();
        int sellStops = buffer.getInt();

        if (liveCount < 0 || buyStops < 0 || sellStops < 0) {
            throw new IllegalArgumentException("Negative snapshot counts");
        }

        for (int i = 0; i < liveCount; i++) {
            BookOrder order = borrowOrder();
            if (order == null) {
                throw new IllegalArgumentException("Snapshot exceeds preallocated order capacity");
            }
            order.orderId = buffer.getLong();
            order.traderId = buffer.getLong();
            order.side = readSide(buffer.get());
            order.priceTicks = buffer.getLong();
            order.openQty = buffer.getLong();
            order.visibleQty = buffer.getLong();
            order.peakSize = buffer.getLong();
            order.sequence = buffer.getLong();

            if (order.openQty <= 0 || order.visibleQty <= 0 || order.visibleQty > order.openQty) {
                throw new IllegalArgumentException("Invalid live order state in snapshot");
            }
            if (!addToBook(order)) {
                throw new IllegalArgumentException("Snapshot exceeds preallocated book level capacity");
            }
            liveOrders.put(order.orderId, order);
        }

        for (int i = 0; i < buyStops; i++) {
            StopOrder stop = readStop(buffer);
            if (!stopBuys.offer(stop)) {
                throw new IllegalArgumentException("Snapshot exceeds preallocated stop-buy capacity");
            }
        }
        for (int i = 0; i < sellStops; i++) {
            StopOrder stop = readStop(buffer);
            if (!stopSells.offer(stop)) {
                throw new IllegalArgumentException("Snapshot exceeds preallocated stop-sell capacity");
            }
        }
    }
    private void writeLiveSide(ByteBuffer buffer, PriceLevel[] sideBook, int count) {
        for (int i = 0; i < count; i++) {
            BookOrder order = sideBook[i].head;
            while (order != null) {
                buffer.putLong(order.orderId);
                buffer.putLong(order.traderId);
                buffer.put(order.side == Side.SELL ? (byte) 1 : (byte) 0);
                buffer.putLong(order.priceTicks);
                buffer.putLong(order.openQty);
                buffer.putLong(order.visibleQty);
                buffer.putLong(order.peakSize);
                buffer.putLong(order.sequence);
                order = order.next;
            }
        }
    }

    private void writeStops(ByteBuffer buffer, StopQueue stops) {
        for (int i = 0; i < stops.size(); i++) {
            StopOrder stop = stops.get(i);
            buffer.putLong(stop.orderId);
            buffer.putLong(stop.traderId);
            buffer.put(stop.side == Side.SELL ? (byte) 1 : (byte) 0);
            buffer.putLong(stop.stopPriceTicks);
            buffer.putLong(stop.quantity);
        }
    }

    private StopOrder readStop(ByteBuffer buffer) {
        StopOrder stop = borrowStop();
        if (stop == null) {
            throw new IllegalArgumentException("Snapshot exceeds preallocated stop capacity");
        }
        stop.orderId = buffer.getLong();
        stop.traderId = buffer.getLong();
        stop.side = readSide(buffer.get());
        stop.stopPriceTicks = buffer.getLong();
        stop.quantity = buffer.getLong();
        if (stop.quantity <= 0) {
            throw new IllegalArgumentException("Invalid stop state in snapshot");
        }
        return stop;
    }

    private static Side readSide(byte side) {
        return side == 1 ? Side.SELL : Side.BUY;
    }

    private void clearState() {
        recycleBookSide(bids, bidCount, Side.BUY);
        recycleBookSide(asks, askCount, Side.SELL);
        bidCount = 0;
        askCount = 0;
        liveOrders.clear();
        recycleStops(stopBuys);
        recycleStops(stopSells);
        tradeSeq = 0;
        lastTradePrice = 0;
    }

    private void recycleBookSide(PriceLevel[] sideBook, int count, Side side) {
        for (int i = 0; i < count; i++) {
            PriceLevel level = sideBook[i];
            BookOrder order = level.head;
            while (order != null) {
                BookOrder next = order.next;
                recycleOrder(order);
                order = next;
            }
            recycleLevel(side, level);
            sideBook[i] = null;
        }
    }

    private void recycleStops(StopQueue stops) {
        StopOrder stop;
        while ((stop = stops.pollFirst()) != null) {
            recycleStop(stop);
        }
    }

    private BookOrder borrowOrder() {
        if (freeOrders == 0) {
            return null;
        }
        return orderPool[--freeOrders];
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
        orderPool[freeOrders++] = order;
    }

    private StopOrder borrowStop() {
        if (freeStops == 0) {
            return null;
        }
        return stopPool[--freeStops];
    }

    private void recycleStop(StopOrder stop) {
        stop.orderId = 0;
        stop.traderId = 0;
        stop.side = null;
        stop.stopPriceTicks = 0;
        stop.quantity = 0;
        stopPool[freeStops++] = stop;
    }

    private PriceLevel borrowLevel(Side side) {
        if (side == Side.BUY) {
            if (freeBidLevels == 0) {
                return null;
            }
            return bidLevelPool[--freeBidLevels];
        }
        if (freeAskLevels == 0) {
            return null;
        }
        return askLevelPool[--freeAskLevels];
    }

    private void recycleLevel(Side side, PriceLevel level) {
        if (level == null) {
            return;
        }
        level.init(0);
        if (side == Side.BUY) {
            bidLevelPool[freeBidLevels++] = level;
        } else {
            askLevelPool[freeAskLevels++] = level;
        }
    }

    private static int findInsertPos(PriceLevel[] levels, int count, long priceTicks, boolean isBuy) {
        int lo = 0;
        int hi = count;
        while (lo < hi) {
            int mid = lo + ((hi - lo) >>> 1);
            long midPrice = levels[mid].priceTicks;
            if (midPrice == priceTicks) {
                return mid;
            }
            if (isBuy) {
                if (midPrice < priceTicks) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            } else if (midPrice > priceTicks) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    private static void shiftRight(PriceLevel[] levels, int count, int from) {
        for (int i = count; i > from; i--) {
            levels[i] = levels[i - 1];
        }
    }

    private static void shiftLeft(PriceLevel[] levels, int count, int from) {
        for (int i = from; i < count - 1; i++) {
            levels[i] = levels[i + 1];
        }
        levels[count - 1] = null;
    }

    private static final class StopOrder {
        long orderId;
        long traderId;
        Side side;
        long stopPriceTicks;
        long quantity;
    }

    private static final class StopQueue {
        private final StopOrder[] entries;
        private int head;
        private int tail;
        private int size;

        private StopQueue(int capacity) {
            this.entries = new StopOrder[capacity];
        }

        boolean offer(StopOrder stop) {
            if (size == entries.length) {
                return false;
            }
            entries[tail] = stop;
            tail++;
            if (tail == entries.length) {
                tail = 0;
            }
            size++;
            return true;
        }

        StopOrder pollFirst() {
            if (size == 0) {
                return null;
            }
            StopOrder stop = entries[head];
            entries[head] = null;
            head++;
            if (head == entries.length) {
                head = 0;
            }
            size--;
            return stop;
        }

        StopOrder get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }
            int pos = head + index;
            if (pos >= entries.length) {
                pos -= entries.length;
            }
            return entries[pos];
        }

        int size() {
            return size;
        }
    }
}

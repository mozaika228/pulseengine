package io.pulseengine.core;

final class BookOrder {
    long orderId;
    long traderId;
    Side side;
    long priceTicks;
    long openQty;
    long visibleQty;
    long peakSize;
    long sequence;

    BookOrder prev;
    BookOrder next;
    PriceLevel level;

    void init(OrderRequest req) {
        this.orderId = req.orderId;
        this.traderId = req.traderId;
        this.side = req.side;
        this.priceTicks = req.priceTicks;
        this.openQty = req.quantity;
        this.peakSize = req.peakSize;
        this.visibleQty = peakSize > 0 ? Math.min(peakSize, req.quantity) : req.quantity;
        this.sequence = req.sequence;
    }

    boolean isIceberg() {
        return peakSize > 0;
    }
}

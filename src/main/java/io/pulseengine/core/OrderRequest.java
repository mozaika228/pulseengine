package io.pulseengine.core;

public final class OrderRequest {
    public long orderId;
    public long traderId;
    public Side side;
    public OrderType type;
    public TimeInForce tif;
    public long priceTicks;
    public long stopPriceTicks;
    public long quantity;
    public long peakSize;
    public long sequence;

    public static OrderRequest limit(long orderId, long traderId, Side side, long priceTicks, long quantity, TimeInForce tif) {
        OrderRequest req = new OrderRequest();
        req.orderId = orderId;
        req.traderId = traderId;
        req.side = side;
        req.type = OrderType.LIMIT;
        req.tif = tif;
        req.priceTicks = priceTicks;
        req.quantity = quantity;
        return req;
    }

    public static OrderRequest market(long orderId, long traderId, Side side, long quantity, TimeInForce tif) {
        OrderRequest req = new OrderRequest();
        req.orderId = orderId;
        req.traderId = traderId;
        req.side = side;
        req.type = OrderType.MARKET;
        req.tif = tif;
        req.quantity = quantity;
        return req;
    }

    public static OrderRequest stopMarket(long orderId, long traderId, Side side, long stopPriceTicks, long quantity) {
        OrderRequest req = new OrderRequest();
        req.orderId = orderId;
        req.traderId = traderId;
        req.side = side;
        req.type = OrderType.STOP_MARKET;
        req.tif = TimeInForce.GTC;
        req.stopPriceTicks = stopPriceTicks;
        req.quantity = quantity;
        return req;
    }
}

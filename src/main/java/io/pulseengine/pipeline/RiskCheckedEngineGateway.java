package io.pulseengine.pipeline;

import io.pulseengine.core.RejectCode;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.TopOfBookView;
import org.agrona.collections.Long2LongHashMap;

public final class RiskCheckedEngineGateway {
    private static final long MISSING = Long.MIN_VALUE;

    private final EnginePipeline engine;
    private final TopOfBookView topOfBook;
    private final RiskLimits limits;

    private final Long2LongHashMap rateSecondByTrader = new Long2LongHashMap(MISSING);
    private final Long2LongHashMap rateCountByTrader = new Long2LongHashMap(MISSING);
    private final Long2LongHashMap openExposureByTrader = new Long2LongHashMap(MISSING);
    private final Long2LongHashMap orderExposureById = new Long2LongHashMap(MISSING);
    private final Long2LongHashMap orderTraderById = new Long2LongHashMap(MISSING);

    public RiskCheckedEngineGateway(EnginePipeline engine, TopOfBookView topOfBook, RiskLimits limits) {
        this.engine = engine;
        this.topOfBook = topOfBook;
        this.limits = limits == null ? RiskLimits.relaxed() : limits;
    }

    public byte submitLimit(long orderId, long traderId, Side side, long priceTicks, long quantity, TimeInForce tif, long peakSize) {
        return submitLimitAt(orderId, traderId, side, priceTicks, quantity, tif, peakSize, System.nanoTime());
    }

    public byte submitLimitAt(
        long orderId,
        long traderId,
        Side side,
        long priceTicks,
        long quantity,
        TimeInForce tif,
        long peakSize,
        long ingressNanos
    ) {
        byte reject = validateNew(traderId, side, priceTicks, quantity, true, tif, ingressNanos);
        if (reject != RejectCode.NONE) {
            return reject;
        }
        trackExposure(orderId, traderId, side, quantity, tif);
        engine.submitLimitAt(orderId, traderId, side, priceTicks, quantity, tif, peakSize, ingressNanos);
        return RejectCode.NONE;
    }

    public byte submitMarket(long orderId, long traderId, Side side, long quantity, TimeInForce tif) {
        return submitMarketAt(orderId, traderId, side, quantity, tif, System.nanoTime());
    }

    public byte submitMarketAt(long orderId, long traderId, Side side, long quantity, TimeInForce tif, long ingressNanos) {
        byte reject = validateNew(traderId, side, 0, quantity, false, tif, ingressNanos);
        if (reject != RejectCode.NONE) {
            return reject;
        }
        engine.submitMarketAt(orderId, traderId, side, quantity, tif, ingressNanos);
        return RejectCode.NONE;
    }

    public byte submitStopMarket(long orderId, long traderId, Side side, long stopPriceTicks, long quantity) {
        return submitStopMarketAt(orderId, traderId, side, stopPriceTicks, quantity, System.nanoTime());
    }

    public byte submitStopMarketAt(long orderId, long traderId, Side side, long stopPriceTicks, long quantity, long ingressNanos) {
        byte reject = validateNew(traderId, side, 0, quantity, false, TimeInForce.GTC, ingressNanos);
        if (reject != RejectCode.NONE) {
            return reject;
        }
        engine.submitStopMarketAt(orderId, traderId, side, stopPriceTicks, quantity, ingressNanos);
        return RejectCode.NONE;
    }

    public byte submitCancel(long cancelOrderId) {
        return submitCancelAt(cancelOrderId, System.nanoTime());
    }

    public byte submitCancelAt(long cancelOrderId, long ingressNanos) {
        long signed = orderExposureById.remove(cancelOrderId);
        if (signed != MISSING) {
            long traderId = orderTraderById.remove(cancelOrderId);
            if (traderId != MISSING) {
                openExposureByTrader.put(traderId, valueOrZero(openExposureByTrader, traderId) - signed);
            }
        }
        engine.submitCancelAt(cancelOrderId, ingressNanos);
        return RejectCode.NONE;
    }

    private byte validateNew(
        long traderId,
        Side side,
        long priceTicks,
        long quantity,
        boolean isLimit,
        TimeInForce tif,
        long ingressNanos
    ) {
        if (quantity <= 0) {
            return RejectCode.INVALID_QTY;
        }
        if (side == null) {
            return RejectCode.INVALID_ORDER;
        }
        if (violatesRateLimit(traderId, ingressNanos)) {
            return RejectCode.RATE_LIMIT;
        }
        if (isLimit && violatesFatFinger(priceTicks)) {
            return RejectCode.FAT_FINGER;
        }
        if (violatesPositionLimit(traderId, side, quantity, isLimit, tif)) {
            return RejectCode.POSITION_LIMIT;
        }
        return RejectCode.NONE;
    }

    private boolean violatesRateLimit(long traderId, long ingressNanos) {
        if (limits.maxOrdersPerSecond() <= 0 || traderId <= 0) {
            return false;
        }
        long second = ingressNanos / 1_000_000_000L;
        long lastSecond = rateSecondByTrader.get(traderId);
        long count = valueOrZero(rateCountByTrader, traderId);
        if (lastSecond != second) {
            count = 0;
        }
        count++;
        rateSecondByTrader.put(traderId, second);
        rateCountByTrader.put(traderId, count);
        return count > limits.maxOrdersPerSecond();
    }

    private boolean violatesFatFinger(long priceTicks) {
        if (limits.maxPriceDeviationTicks() <= 0 || priceTicks <= 0) {
            return false;
        }
        long bid = topOfBook.bestBid();
        long ask = topOfBook.bestAsk();
        if (bid <= 0 || ask <= 0) {
            return false;
        }
        long mid = (bid + ask) >>> 1;
        long diff = priceTicks - mid;
        long abs = diff >= 0 ? diff : -diff;
        return abs > limits.maxPriceDeviationTicks();
    }

    private boolean violatesPositionLimit(long traderId, Side side, long quantity, boolean isLimit, TimeInForce tif) {
        if (limits.maxAbsOpenExposure() <= 0 || traderId <= 0 || !isLimit || tif != TimeInForce.GTC) {
            return false;
        }
        long signed = side == Side.SELL ? -quantity : quantity;
        long projected = valueOrZero(openExposureByTrader, traderId) + signed;
        long abs = projected >= 0 ? projected : -projected;
        return abs > limits.maxAbsOpenExposure();
    }

    private void trackExposure(long orderId, long traderId, Side side, long quantity, TimeInForce tif) {
        if (traderId <= 0 || tif != TimeInForce.GTC) {
            return;
        }
        long signed = side == Side.SELL ? -quantity : quantity;
        openExposureByTrader.put(traderId, valueOrZero(openExposureByTrader, traderId) + signed);
        orderExposureById.put(orderId, signed);
        orderTraderById.put(orderId, traderId);
    }

    private static long valueOrZero(Long2LongHashMap map, long key) {
        long value = map.get(key);
        return value == MISSING ? 0L : value;
    }
}
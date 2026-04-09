package io.pulseengine.jni;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeIcebergTest {

    @Test
    void icebergRefreshesVisibleQuantityAndMaintainsBestLevel() {
        Assumptions.assumeTrue(NativeOrderBook.isNativeAvailable(), "Native library unavailable");

        try (NativeOrderBook book = new NativeOrderBook()) {
            book.insertLimitIceberg(1, 50_100, 10, 3, false);

            NativeOrderBook.L2Update first = book.publishL2Update();
            assertEquals(50_100, Math.round(first.bestAsk));
            assertEquals(3, first.bestAskQty);

            NativeOrderBook.MatchResult r1 = book.matchMarketOrder(2, 2, true);
            assertEquals(2, r1.filledQty);
            NativeOrderBook.L2Update afterFirstHit = book.publishL2Update();
            assertEquals(1, afterFirstHit.bestAskQty);

            NativeOrderBook.MatchResult r2 = book.matchMarketOrder(3, 2, true);
            assertEquals(2, r2.filledQty);
            NativeOrderBook.L2Update afterRefresh = book.publishL2Update();
            assertEquals(2, afterRefresh.bestAskQty);
        }
    }

    @Test
    void explicitStatusCodesReturnedOnNativeCapacityOverflow() {
        Assumptions.assumeTrue(NativeOrderBook.isNativeAvailable(), "Native library unavailable");

        try (NativeOrderBook book = new NativeOrderBook(1, 1)) {
            assertEquals(NativeOrderBook.INSERT_OK, book.tryInsertLimitOrder(1, 50_000, 10, true));
            assertEquals(NativeOrderBook.INSERT_BOOK_LEVELS_FULL, book.tryInsertLimitOrder(2, 49_900, 10, true));
            assertEquals(NativeOrderBook.INSERT_ORDER_POOL_EXHAUSTED, book.tryInsertLimitOrder(3, 50_000, 10, true));
        }
    }
}

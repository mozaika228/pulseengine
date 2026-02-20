package io.pulseengine.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    @Test
    void limitOrdersMatchByPriceTimePriority() {
        OrderBook book = new OrderBook();
        RecordingSink sink = new RecordingSink();

        book.process(OrderRequest.limit(1, 100, Side.SELL, 50_100, 5, TimeInForce.GTC), sink, SmpPolicy.NONE, 1);
        book.process(OrderRequest.limit(2, 101, Side.SELL, 50_100, 7, TimeInForce.GTC), sink, SmpPolicy.NONE, 2);

        book.process(OrderRequest.limit(3, 200, Side.BUY, 50_100, 8, TimeInForce.IOC), sink, SmpPolicy.NONE, 3);

        assertEquals(50_100, book.bestAsk());
        assertEquals(4, book.bestAskQty());

        assertEquals(2, sink.trades.size());
        assertEquals("trade:3:1:5", sink.trades.get(0));
        assertEquals("trade:3:2:3", sink.trades.get(1));
    }

    @Test
    void fokRejectsWhenCannotFullyFill() {
        OrderBook book = new OrderBook();
        RecordingSink sink = new RecordingSink();

        book.process(OrderRequest.limit(10, 500, Side.SELL, 50_100, 3, TimeInForce.GTC), sink, SmpPolicy.NONE, 1);
        book.process(OrderRequest.limit(11, 501, Side.BUY, 50_100, 4, TimeInForce.FOK), sink, SmpPolicy.NONE, 2);

        assertTrue(sink.rejections.contains("reject:11:FOK_unfilled"));
        assertEquals(50_100, book.bestAsk());
        assertEquals(3, book.bestAskQty());
    }

    @Test
    void iocCancelsRemainingQuantity() {
        OrderBook book = new OrderBook();
        RecordingSink sink = new RecordingSink();

        book.process(OrderRequest.limit(20, 700, Side.SELL, 50_100, 2, TimeInForce.GTC), sink, SmpPolicy.NONE, 1);
        book.process(OrderRequest.limit(21, 701, Side.BUY, 50_100, 5, TimeInForce.IOC), sink, SmpPolicy.NONE, 2);

        assertTrue(sink.cancels.contains("cancel:21:3"));
        assertEquals(0, book.bestAsk());
    }

    @Test
    void cancelRemovesLiveOrder() {
        OrderBook book = new OrderBook();
        RecordingSink sink = new RecordingSink();

        book.process(OrderRequest.limit(30, 800, Side.BUY, 49_900, 10, TimeInForce.GTC), sink, SmpPolicy.NONE, 1);

        assertTrue(book.cancel(30, sink, 2));
        assertFalse(book.cancel(30, sink, 3));
        assertEquals(0, book.bestBid());
    }

    @Test
    void stopMarketActivatesAfterTriggerTrade() {
        OrderBook book = new OrderBook();
        RecordingSink sink = new RecordingSink();

        book.process(OrderRequest.stopMarket(40, 900, Side.BUY, 50_200, 2), sink, SmpPolicy.NONE, 1);
        book.process(OrderRequest.limit(41, 901, Side.SELL, 50_200, 2, TimeInForce.GTC), sink, SmpPolicy.NONE, 2);
        book.process(OrderRequest.limit(42, 902, Side.BUY, 50_200, 1, TimeInForce.IOC), sink, SmpPolicy.NONE, 3);

        assertTrue(sink.filled.contains("filled:40") || sink.cancels.stream().anyMatch(v -> v.startsWith("cancel:40:")));
    }

    private static final class RecordingSink implements MatchEventSink {
        private final List<String> trades = new ArrayList<>();
        private final List<String> rejections = new ArrayList<>();
        private final List<String> cancels = new ArrayList<>();
        private final List<String> filled = new ArrayList<>();

        @Override
        public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
            trades.add("trade:" + buyOrderId + ":" + sellOrderId + ":" + quantity);
        }

        @Override
        public void onOrderAccepted(long orderId, long openQty, long tsNanos) {
        }

        @Override
        public void onOrderRejected(long orderId, String reason, long tsNanos) {
            rejections.add("reject:" + orderId + ":" + reason);
        }

        @Override
        public void onOrderCanceled(long orderId, long remainingQty, long tsNanos) {
            cancels.add("cancel:" + orderId + ":" + remainingQty);
        }

        @Override
        public void onOrderFilled(long orderId, long tsNanos) {
            filled.add("filled:" + orderId);
        }

        @Override
        public void onOrderPartiallyFilled(long orderId, long remainingQty, long tsNanos) {
        }
    }
}

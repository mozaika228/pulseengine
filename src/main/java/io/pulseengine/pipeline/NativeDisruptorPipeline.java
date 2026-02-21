package io.pulseengine.pipeline;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.pulseengine.jni.NativeOrderBook;
import io.pulseengine.md.TopOfBookView;

import java.util.concurrent.ThreadFactory;

public final class NativeDisruptorPipeline implements AutoCloseable {
    private final Disruptor<NativeCommandEvent> disruptor;
    private final RingBuffer<NativeCommandEvent> ring;
    private final TopOfBookView topOfBook;
    private final NativeOrderBook orderBook;

    public NativeDisruptorPipeline(int ringSize, TopOfBookView topOfBook, ThreadFactory threadFactory) {
        this.topOfBook = topOfBook;
        this.orderBook = new NativeOrderBook();
        EventFactory<NativeCommandEvent> factory = NativeCommandEvent::new;
        this.disruptor = new Disruptor<>(
            factory,
            ringSize,
            threadFactory,
            ProducerType.SINGLE,
            new BlockingWaitStrategy()
        );
        this.disruptor.handleEventsWith(new NativeCommandHandler(orderBook, topOfBook));
        this.ring = disruptor.start();
    }

    public void submitLimit(long orderId, double price, long qty, boolean isBuy) {
        long seq = ring.next();
        try {
            NativeCommandEvent e = ring.get(seq);
            e.type = NativeCommandType.LIMIT;
            e.orderId = orderId;
            e.price = price;
            e.qty = qty;
            e.isBuy = isBuy;
        } finally {
            ring.publish(seq);
        }
    }

    public void submitMarket(long orderId, long qty, boolean isBuy) {
        long seq = ring.next();
        try {
            NativeCommandEvent e = ring.get(seq);
            e.type = NativeCommandType.MARKET;
            e.orderId = orderId;
            e.qty = qty;
            e.isBuy = isBuy;
            e.price = 0.0;
        } finally {
            ring.publish(seq);
        }
    }

    public TopOfBookView topOfBook() {
        return topOfBook;
    }

    @Override
    public void close() {
        disruptor.shutdown();
        orderBook.close();
    }

    private enum NativeCommandType {
        LIMIT,
        MARKET
    }

    private static final class NativeCommandEvent {
        NativeCommandType type;
        long orderId;
        double price;
        long qty;
        boolean isBuy;

        void reset() {
            type = null;
            orderId = 0;
            price = 0.0;
            qty = 0;
            isBuy = false;
        }
    }

    private static final class NativeCommandHandler implements EventHandler<NativeCommandEvent> {
        private final NativeOrderBook orderBook;
        private final TopOfBookView topOfBook;

        private NativeCommandHandler(NativeOrderBook orderBook, TopOfBookView topOfBook) {
            this.orderBook = orderBook;
            this.topOfBook = topOfBook;
        }

        @Override
        public void onEvent(NativeCommandEvent event, long sequence, boolean endOfBatch) {
            if (event.type == NativeCommandType.LIMIT) {
                orderBook.insertLimitOrder(event.orderId, event.price, event.qty, event.isBuy);
            } else if (event.type == NativeCommandType.MARKET) {
                orderBook.matchMarketOrder(event.orderId, event.qty, event.isBuy);
            }
            NativeOrderBook.L2Update l2 = orderBook.publishL2Update();
            long bid = l2.bestBid > 0 ? Math.round(l2.bestBid) : 0;
            long ask = l2.bestAsk > 0 ? Math.round(l2.bestAsk) : 0;
            topOfBook.publish(bid, ask);
            event.reset();
        }
    }
}

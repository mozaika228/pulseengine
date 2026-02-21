package io.pulseengine.app;

import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.NativeDisruptorPipeline;

public final class NativeDisruptorDemo {
    private NativeDisruptorDemo() {
    }

    public static void main(String[] args) {
        TopOfBookView top = new TopOfBookView();
        try (NativeDisruptorPipeline pipeline = new NativeDisruptorPipeline(1 << 14, top, NativeDisruptorDemo::newDaemon)) {
            long start = top.sequence();
            pipeline.submitLimit(1, 49_900, 10, true);
            pipeline.submitLimit(2, 50_100, 12, false);
            pipeline.submitMarket(3, 5, true);

            long target = start + 3;
            while (top.sequence() < target) {
                Thread.onSpinWait();
            }
            System.out.println("native_disruptor_best_bid=" + top.bestBid() + " best_ask=" + top.bestAsk());
        }
    }

    private static Thread newDaemon(Runnable r) {
        Thread t = new Thread(r, "pulse-native-disruptor");
        t.setDaemon(true);
        return t;
    }
}

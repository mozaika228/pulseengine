package io.pulseengine.app;

import io.pulseengine.jni.NativeMatchingEngine;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.NativeIngressAdapter;

public final class NativePipelineDemo {
    private NativePipelineDemo() {
    }

    public static void main(String[] args) {
        TopOfBookView top = new TopOfBookView();
        try (NativeIngressAdapter nativeIngress = new NativeIngressAdapter(top)) {
            nativeIngress.insertLimitOrder(1, 49_900, 10, true);
            nativeIngress.insertLimitOrder(2, 50_100, 15, false);

            NativeMatchingEngine.MatchResult result = nativeIngress.matchMarketOrder(3, 7, true);
            NativeMatchingEngine.L2Update l2 = nativeIngress.publishL2Update();

            System.out.println("filled=" + result.filledQty + " remaining=" + result.remainingQty + " trades=" + result.trades);
            System.out.println("bestBid=" + l2.bestBid + " bestAsk=" + l2.bestAsk);
            System.out.println("topOfBook=" + top.bestBid() + "/" + top.bestAsk());
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native library is not loaded. Build cpp/ and set java.library.path.");
            throw e;
        }
    }
}

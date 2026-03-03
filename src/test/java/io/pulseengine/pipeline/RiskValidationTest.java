package io.pulseengine.pipeline;

import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.RejectCode;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.TopOfBookView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskValidationTest {

    @Test
    void rateLimitRejectsThirdOrderWithinOneSecond() {
        TopOfBookView top = new TopOfBookView();
        try (EnginePipeline engine = new EnginePipeline(1 << 12, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, top, RiskValidationTest::newDaemon)) {
            RiskLimits limits = new RiskLimits(10_000, 2, 1_000_000);
            RiskCheckedEngineGateway gateway = new RiskCheckedEngineGateway(engine, top, limits);
            long t = 1_000_000_000L;

            assertEquals(RejectCode.NONE, gateway.submitLimitAt(1, 42, Side.BUY, 100, 10, TimeInForce.GTC, 0, t));
            assertEquals(RejectCode.NONE, gateway.submitLimitAt(2, 42, Side.BUY, 101, 10, TimeInForce.GTC, 0, t + 1));
            assertEquals(RejectCode.RATE_LIMIT, gateway.submitLimitAt(3, 42, Side.BUY, 102, 10, TimeInForce.GTC, 0, t + 2));
        }
    }

    @Test
    void fatFingerRejectsPriceTooFarFromMid() {
        TopOfBookView top = new TopOfBookView();
        top.publish(100, 102);
        try (EnginePipeline engine = new EnginePipeline(1 << 12, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, top, RiskValidationTest::newDaemon)) {
            RiskLimits limits = new RiskLimits(10_000, 100, 1);
            RiskCheckedEngineGateway gateway = new RiskCheckedEngineGateway(engine, top, limits);

            assertEquals(RejectCode.FAT_FINGER, gateway.submitLimitAt(10, 7, Side.BUY, 110, 1, TimeInForce.GTC, 0, 1_000));
            assertEquals(RejectCode.NONE, gateway.submitLimitAt(11, 7, Side.BUY, 101, 1, TimeInForce.GTC, 0, 2_000));
        }
    }

    @Test
    void positionLimitRejectsProjectedExposure() {
        TopOfBookView top = new TopOfBookView();
        try (EnginePipeline engine = new EnginePipeline(1 << 12, new BlackholeSink(), SmpPolicy.CANCEL_AGGRESSOR, top, RiskValidationTest::newDaemon)) {
            RiskLimits limits = new RiskLimits(100, 100, 1_000_000);
            RiskCheckedEngineGateway gateway = new RiskCheckedEngineGateway(engine, top, limits);

            assertEquals(RejectCode.NONE, gateway.submitLimitAt(20, 99, Side.BUY, 100, 70, TimeInForce.GTC, 0, 1_000));
            assertEquals(RejectCode.POSITION_LIMIT, gateway.submitLimitAt(21, 99, Side.BUY, 100, 40, TimeInForce.GTC, 0, 2_000));
            assertEquals(RejectCode.NONE, gateway.submitLimitAt(22, 99, Side.SELL, 101, 50, TimeInForce.GTC, 0, 3_000));
        }
    }

    private static Thread newDaemon(Runnable runnable) {
        Thread t = new Thread(runnable, "risk-test");
        t.setDaemon(true);
        return t;
    }

    private static final class BlackholeSink implements MatchEventSink {
        @Override
        public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
        }

        @Override
        public void onOrderAccepted(long orderId, long openQty, long tsNanos) {
        }

        @Override
        public void onOrderRejected(long orderId, byte reasonCode, long tsNanos) {
        }

        @Override
        public void onOrderCanceled(long orderId, long remainingQty, long tsNanos) {
        }

        @Override
        public void onOrderFilled(long orderId, long tsNanos) {
        }

        @Override
        public void onOrderPartiallyFilled(long orderId, long remainingQty, long tsNanos) {
        }
    }
}
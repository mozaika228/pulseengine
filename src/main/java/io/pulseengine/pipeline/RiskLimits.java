package io.pulseengine.pipeline;

public record RiskLimits(long maxAbsOpenExposure, int maxOrdersPerSecond, long maxPriceDeviationTicks) {
    public static RiskLimits relaxed() {
        return new RiskLimits(-1, -1, -1);
    }
}

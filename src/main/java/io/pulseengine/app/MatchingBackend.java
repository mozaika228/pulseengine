package io.pulseengine.app;

public enum MatchingBackend {
    JAVA,
    NATIVE;

    public static MatchingBackend resolve() {
        String value = System.getProperty("pulseengine.matchingBackend");
        if (value == null || value.isBlank()) {
            value = System.getenv("PULSEENGINE_MATCHING_BACKEND");
        }
        if (value == null || value.isBlank()) {
            return NATIVE;
        }
        return switch (value.trim().toUpperCase()) {
            case "JAVA" -> JAVA;
            default -> NATIVE;
        };
    }
}

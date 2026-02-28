package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import java.util.Map;

/**
 * Final trading decision produced by a strategy, including position sizing and
 * metadata.
 */
public record Decision(
        Action action,
        int quantity,
        double price,
        String reasoning,
        double confidence,
        Map<String, Object> metadata) {

    public static Decision hold() {
        return new Decision(Action.HOLD, 0, 0, "No action", 0, Map.of());
    }
}

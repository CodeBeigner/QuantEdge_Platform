package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import java.time.Instant;

public record Candle(
    Instant timestamp,
    double open,
    double high,
    double low,
    double close,
    double volume,
    TimeFrame timeFrame
) {
    public double typicalPrice() {
        return (high + low + close) / 3.0;
    }

    public boolean isBullish() {
        return close > open;
    }

    public boolean isBearish() {
        return close < open;
    }

    public double body() {
        return Math.abs(close - open);
    }

    public double range() {
        return high - low;
    }

    public double upperWick() {
        return isBullish() ? high - close : high - open;
    }

    public double lowerWick() {
        return isBullish() ? open - low : close - low;
    }
}

package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

/**
 * Identifies which quantitative model a strategy uses.
 */
public enum ModelType {
    MOMENTUM, VOLATILITY, MACRO, CORRELATION, REGIME,
    // New multi-timeframe strategies
    TREND_CONTINUATION, MEAN_REVERSION, FUNDING_SENTIMENT
}

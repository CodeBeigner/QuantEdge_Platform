package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

public record IndicatorSnapshot(
    TimeFrame timeFrame,
    double ema21,
    double ema50,
    double ema21Slope,
    double rsi14,
    double bollingerUpper,
    double bollingerMiddle,
    double bollingerLower,
    double bollingerWidth,
    double bollingerPercentB,
    double atr14,
    double vwap,
    double adx,
    double volumeRatio,
    double macd,
    double macdSignal,
    double macdHistogram
) {}

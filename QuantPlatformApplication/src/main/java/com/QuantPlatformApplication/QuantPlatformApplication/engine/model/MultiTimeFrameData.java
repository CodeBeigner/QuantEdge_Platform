package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MultiTimeFrameData {

    private final List<Candle> candles15m;
    private final List<Candle> candles1h;
    private final List<Candle> candles4h;

    private final IndicatorSnapshot indicators15m;
    private final IndicatorSnapshot indicators1h;
    private final IndicatorSnapshot indicators4h;

    private final double fundingRate;
    private final double fundingRatePredicted;
    private final List<Double> fundingRateHistory;
    private final double openInterest;
    private final double openInterestChange24h;
    private final double longShortRatio;

    private final String symbol;
    private final double currentPrice;
    private final double currentVolume;

    public Candle latestCandle(TimeFrame tf) {
        List<Candle> candles = switch (tf) {
            case M15 -> candles15m;
            case H1 -> candles1h;
            case H4 -> candles4h;
        };
        return candles != null && !candles.isEmpty() ? candles.get(candles.size() - 1) : null;
    }

    public IndicatorSnapshot indicators(TimeFrame tf) {
        return switch (tf) {
            case M15 -> indicators15m;
            case H1 -> indicators1h;
            case H4 -> indicators4h;
        };
    }

    public boolean isComplete() {
        return indicators15m != null && indicators1h != null && indicators4h != null;
    }
}

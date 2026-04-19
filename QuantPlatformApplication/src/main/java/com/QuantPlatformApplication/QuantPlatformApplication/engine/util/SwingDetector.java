package com.QuantPlatformApplication.QuantPlatformApplication.engine.util;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects swing highs and swing lows from candle data.
 * A swing high is a candle whose high is higher than the N candles before and after it.
 * A swing low is a candle whose low is lower than the N candles before and after it.
 */
@Component
public class SwingDetector {

    /**
     * Find swing high prices (resistance levels).
     * @param candles price data
     * @param lookback number of candles on each side to compare (e.g., 2 means 2 left + 2 right)
     * @return list of swing high prices, chronologically ordered
     */
    public List<Double> findSwingHighs(List<Candle> candles, int lookback) {
        List<Double> swingHighs = new ArrayList<>();
        if (candles.size() < lookback * 2 + 1) {
            return swingHighs;
        }

        for (int i = lookback; i < candles.size() - lookback; i++) {
            double candidateHigh = candles.get(i).high();
            boolean isSwingHigh = true;

            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (candles.get(j).high() >= candidateHigh) {
                    isSwingHigh = false;
                    break;
                }
            }

            if (isSwingHigh) {
                swingHighs.add(candidateHigh);
            }
        }

        return swingHighs;
    }

    /**
     * Find swing low prices (support levels).
     * @param candles price data
     * @param lookback number of candles on each side to compare
     * @return list of swing low prices, chronologically ordered
     */
    public List<Double> findSwingLows(List<Candle> candles, int lookback) {
        List<Double> swingLows = new ArrayList<>();
        if (candles.size() < lookback * 2 + 1) {
            return swingLows;
        }

        for (int i = lookback; i < candles.size() - lookback; i++) {
            double candidateLow = candles.get(i).low();
            boolean isSwingLow = true;

            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (candles.get(j).low() <= candidateLow) {
                    isSwingLow = false;
                    break;
                }
            }

            if (isSwingLow) {
                swingLows.add(candidateLow);
            }
        }

        return swingLows;
    }

    /**
     * Find the nearest support level below the given price.
     */
    public double nearestSupport(List<Candle> candles, double currentPrice, int lookback) {
        List<Double> swingLows = findSwingLows(candles, lookback);
        double nearest = 0;
        for (Double low : swingLows) {
            if (low < currentPrice && low > nearest) {
                nearest = low;
            }
        }
        return nearest;
    }

    /**
     * Find the nearest resistance level above the given price.
     */
    public double nearestResistance(List<Candle> candles, double currentPrice, int lookback) {
        List<Double> swingHighs = findSwingHighs(candles, lookback);
        double nearest = Double.MAX_VALUE;
        for (Double high : swingHighs) {
            if (high > currentPrice && high < nearest) {
                nearest = high;
            }
        }
        return nearest == Double.MAX_VALUE ? 0 : nearest;
    }
}

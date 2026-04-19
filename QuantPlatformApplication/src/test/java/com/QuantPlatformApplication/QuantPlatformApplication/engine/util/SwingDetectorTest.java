package com.QuantPlatformApplication.QuantPlatformApplication.engine.util;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwingDetectorTest {

    private final SwingDetector detector = new SwingDetector();

    private Candle candle(int i, double high, double low) {
        Instant ts = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 3600L);
        double open = (high + low) / 2;
        double close = open + 0.5;
        return new Candle(ts, open, high, low, close, 1000, TimeFrame.H1);
    }

    @Test
    void detectsSwingHigh() {
        // Pattern: rising highs then falling highs -> swing high at peak
        List<Candle> candles = List.of(
            candle(0, 100, 95), candle(1, 105, 100), candle(2, 110, 105),
            candle(3, 108, 103), candle(4, 106, 101)
        );

        List<Double> swingHighs = detector.findSwingHighs(candles, 2);
        assertFalse(swingHighs.isEmpty());
        assertEquals(110, swingHighs.get(swingHighs.size() - 1), 0.01);
    }

    @Test
    void detectsSwingLow() {
        // Pattern: falling lows then rising lows -> swing low at trough
        List<Candle> candles = List.of(
            candle(0, 110, 105), candle(1, 108, 100), candle(2, 106, 95),
            candle(3, 108, 98), candle(4, 110, 100)
        );

        List<Double> swingLows = detector.findSwingLows(candles, 2);
        assertFalse(swingLows.isEmpty());
        assertEquals(95, swingLows.get(swingLows.size() - 1), 0.01);
    }

    @Test
    void findsSupportResistanceLevels() {
        List<Candle> candles = new ArrayList<>();
        // Create data with clear S/R: highs cluster around 110, lows around 95
        for (int i = 0; i < 20; i++) {
            double high = 108 + (i % 3 == 0 ? 2 : -1);
            double low = 94 + (i % 4 == 0 ? 1 : 2);
            candles.add(candle(i, high, low));
        }

        List<Double> resistance = detector.findSwingHighs(candles, 2);
        List<Double> support = detector.findSwingLows(candles, 2);

        assertFalse(resistance.isEmpty(), "Should find resistance levels");
        assertFalse(support.isEmpty(), "Should find support levels");
    }

    @Test
    void emptyOrSmallInputReturnsEmpty() {
        assertTrue(detector.findSwingHighs(List.of(), 2).isEmpty());
        assertTrue(detector.findSwingLows(List.of(), 2).isEmpty());

        List<Candle> small = List.of(candle(0, 100, 95));
        assertTrue(detector.findSwingHighs(small, 2).isEmpty());
    }
}

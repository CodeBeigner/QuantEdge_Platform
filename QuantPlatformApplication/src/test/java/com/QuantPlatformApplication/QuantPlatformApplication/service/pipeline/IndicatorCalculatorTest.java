package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.IndicatorSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndicatorCalculatorTest {

    private final IndicatorCalculator calc = new IndicatorCalculator();

    private List<Candle> generateCandles(int count, double startPrice, double increment) {
        List<Candle> candles = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            double price = startPrice + i * increment;
            candles.add(new Candle(
                base.plusSeconds(i * 900L),
                price, price + 2, price - 1, price + 1, 1000 + i * 10, TimeFrame.M15
            ));
        }
        return candles;
    }

    @Test
    void emaConvergesToPriceInSteadyUptrend() {
        List<Candle> candles = generateCandles(100, 100, 1);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        double lastClose = candles.get(candles.size() - 1).close();
        assertTrue(snap.ema21() < lastClose, "EMA21 should lag below price in uptrend");
        assertTrue(snap.ema21() > lastClose - 30, "EMA21 should not be too far from price");
    }

    @Test
    void ema21SlopePositiveInUptrend() {
        List<Candle> candles = generateCandles(100, 100, 1);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertTrue(snap.ema21Slope() > 0, "EMA21 slope should be positive in uptrend");
    }

    @Test
    void rsiAbove50InUptrend() {
        List<Candle> candles = generateCandles(100, 100, 1);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertTrue(snap.rsi14() > 50, "RSI should be above 50 in consistent uptrend");
        assertTrue(snap.rsi14() <= 100, "RSI must not exceed 100");
    }

    @Test
    void rsiBelow50InDowntrend() {
        List<Candle> candles = generateCandles(100, 200, -1);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertTrue(snap.rsi14() < 50, "RSI should be below 50 in consistent downtrend");
        assertTrue(snap.rsi14() >= 0, "RSI must not go below 0");
    }

    @Test
    void bollingerBandsContainPrice() {
        List<Candle> candles = generateCandles(100, 100, 0.5);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertTrue(snap.bollingerUpper() > snap.bollingerMiddle());
        assertTrue(snap.bollingerMiddle() > snap.bollingerLower());
        assertTrue(snap.bollingerWidth() > 0);
    }

    @Test
    void atrPositiveWithVolatileData() {
        List<Candle> candles = generateCandles(100, 100, 1);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertTrue(snap.atr14() > 0, "ATR should be positive");
    }

    @Test
    void volumeRatioAboveOneWithIncreasingVolume() {
        List<Candle> candles = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 50; i++) {
            candles.add(new Candle(
                base.plusSeconds(i * 900L),
                100, 102, 99, 101, 100 + i * 50, TimeFrame.M15
            ));
        }
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertTrue(snap.volumeRatio() > 1.0, "Volume ratio should be above 1 when latest volume is above average");
    }

    @Test
    void insufficientDataReturnsNull() {
        List<Candle> candles = generateCandles(5, 100, 1);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertNull(snap, "Should return null with insufficient data (need at least 50 candles)");
    }

    @Test
    void adxHighInStrongTrend() {
        List<Candle> candles = generateCandles(100, 100, 2);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertTrue(snap.adx() > 20, "ADX should be above 20 in strong trend");
    }

    @Test
    void macdPositiveInUptrend() {
        List<Candle> candles = generateCandles(100, 100, 1);
        IndicatorSnapshot snap = calc.calculate(candles, TimeFrame.M15);
        assertTrue(snap.macd() > 0, "MACD should be positive in uptrend (fast EMA above slow EMA)");
    }
}

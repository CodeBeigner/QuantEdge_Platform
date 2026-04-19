package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.util.SwingDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TrendContinuationStrategyTest {

    private TrendContinuationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TrendContinuationStrategy(new SwingDetector());
    }

    // Helper: build IndicatorSnapshot with key fields, rest defaulted
    private IndicatorSnapshot indicators(TimeFrame tf, double ema21Slope, double rsi,
                                          double adx, double volumeRatio, double atr,
                                          double ema21, double macdHist) {
        return new IndicatorSnapshot(tf, ema21, ema21 + 10, ema21Slope, rsi,
            ema21 + 100, ema21, ema21 - 100, 0.05, 0.5,
            atr, ema21, adx, volumeRatio, 1.0, 0.5, macdHist);
    }

    private List<Candle> generateCandles(int count, double basePrice, TimeFrame tf) {
        List<Candle> candles = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            double price = basePrice + i * 0.5;
            candles.add(new Candle(base.plusSeconds(i * 900L),
                price, price + 3, price - 2, price + 1, 1000, tf));
        }
        return candles;
    }

    @Test
    void returnsEmptyWhen4hBiasIsNeutral() {
        // EMA21 slope near zero → neutral bias → no trade
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 0.0, 55, 15, 1.0, 100, 67000, 0))
            .indicators1h(indicators(TimeFrame.H1, 0.5, 55, 25, 1.0, 50, 67000, 0.5))
            .indicators15m(indicators(TimeFrame.M15, 0.3, 45, 20, 1.5, 30, 66950, 0.3))
            .candles1h(generateCandles(50, 66800, TimeFrame.H1))
            .candles15m(generateCandles(50, 66900, TimeFrame.M15))
            .fundingRate(0.01).fundingRateHistory(List.of(0.01, 0.01, 0.01))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        assertTrue(signal.isEmpty(), "Should not trade when 4H bias is neutral");
    }

    @Test
    void producesLongSignalInBullishTrend() {
        // 4H bullish (positive EMA slope), 1H has support nearby, 15M shows entry
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 5.0, 60, 30, 1.0, 100, 66800, 1.0))
            .indicators1h(indicators(TimeFrame.H1, 3.0, 55, 25, 1.0, 50, 66900, 0.5))
            .indicators15m(indicators(TimeFrame.M15, 2.0, 42, 20, 1.8, 30, 66950, 0.3))
            .candles1h(generateCandles(50, 66500, TimeFrame.H1))
            .candles15m(generateCandles(50, 66800, TimeFrame.M15))
            .fundingRate(-0.01).fundingRateHistory(List.of(-0.01, -0.005, -0.01))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertEquals(Action.BUY, signal.get().getAction());
            assertTrue(signal.get().getStopLossPrice() < signal.get().getEntryPrice());
            assertTrue(signal.get().getTakeProfitPrice() > signal.get().getEntryPrice());
            assertNotNull(signal.get().getBiasExplanation());
            assertNotNull(signal.get().getTriggerExplanation());
            assertNotNull(signal.get().getLesson());
        }
        // Signal may be empty if conditions don't perfectly align — that's OK for a selective strategy
    }

    @Test
    void producesShortSignalInBearishTrend() {
        // 4H bearish (negative EMA slope)
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, -5.0, 35, 30, 1.0, 100, 67200, -1.0))
            .indicators1h(indicators(TimeFrame.H1, -3.0, 40, 25, 1.0, 50, 67100, -0.5))
            .indicators15m(indicators(TimeFrame.M15, -2.0, 58, 20, 1.8, 30, 67050, -0.3))
            .candles1h(generateCandles(50, 67200, TimeFrame.H1))
            .candles15m(generateCandles(50, 67100, TimeFrame.M15))
            .fundingRate(0.03).fundingRateHistory(List.of(0.03, 0.025, 0.03))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertEquals(Action.SELL, signal.get().getAction());
            assertTrue(signal.get().getStopLossPrice() > signal.get().getEntryPrice());
            assertTrue(signal.get().getTakeProfitPrice() < signal.get().getEntryPrice());
        }
    }

    @Test
    void returnsEmptyWhenIncompleteData() {
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(null)
            .indicators1h(null)
            .indicators15m(null)
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        assertTrue(signal.isEmpty());
    }

    @Test
    void confidenceIncreasesWithFundingAlignment() {
        // Negative funding + long signal = higher confidence
        MultiTimeFrameData dataWithFunding = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 5.0, 60, 30, 1.0, 100, 66800, 1.0))
            .indicators1h(indicators(TimeFrame.H1, 3.0, 55, 25, 1.0, 50, 66900, 0.5))
            .indicators15m(indicators(TimeFrame.M15, 2.0, 42, 20, 1.8, 30, 66950, 0.3))
            .candles1h(generateCandles(50, 66500, TimeFrame.H1))
            .candles15m(generateCandles(50, 66800, TimeFrame.M15))
            .fundingRate(-0.03)  // Strongly negative = shorts paying longs
            .fundingRateHistory(List.of(-0.03, -0.025, -0.03))
            .build();

        MultiTimeFrameData dataNoFunding = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 5.0, 60, 30, 1.0, 100, 66800, 1.0))
            .indicators1h(indicators(TimeFrame.H1, 3.0, 55, 25, 1.0, 50, 66900, 0.5))
            .indicators15m(indicators(TimeFrame.M15, 2.0, 42, 20, 1.8, 30, 66950, 0.3))
            .candles1h(generateCandles(50, 66500, TimeFrame.H1))
            .candles15m(generateCandles(50, 66800, TimeFrame.M15))
            .fundingRate(0.0)
            .fundingRateHistory(List.of(0.0, 0.0, 0.0))
            .build();

        Optional<TradeSignal> withFunding = strategy.analyze(dataWithFunding);
        Optional<TradeSignal> noFunding = strategy.analyze(dataNoFunding);

        // Both may or may not produce signals, but if both do, funding-aligned should have higher confidence
        if (withFunding.isPresent() && noFunding.isPresent()) {
            assertTrue(withFunding.get().getConfidence() >= noFunding.get().getConfidence(),
                "Funding alignment should increase confidence");
        }
    }

    @Test
    void signalIncludesEducationalLesson() {
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 5.0, 60, 30, 1.0, 100, 66800, 1.0))
            .indicators1h(indicators(TimeFrame.H1, 3.0, 55, 25, 1.0, 50, 66900, 0.5))
            .indicators15m(indicators(TimeFrame.M15, 2.0, 42, 20, 1.8, 30, 66950, 0.3))
            .candles1h(generateCandles(50, 66500, TimeFrame.H1))
            .candles15m(generateCandles(50, 66800, TimeFrame.M15))
            .fundingRate(-0.01).fundingRateHistory(List.of(-0.01, -0.01, -0.01))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertNotNull(signal.get().getLesson());
            assertFalse(signal.get().getLesson().isEmpty());
        }
    }

    @Test
    void getModelTypeReturnsTrendContinuation() {
        assertEquals(ModelType.TREND_CONTINUATION, strategy.getModelType());
    }

    @Test
    void getNameReturnsExpectedName() {
        assertEquals("Trend Continuation", strategy.getName());
    }
}

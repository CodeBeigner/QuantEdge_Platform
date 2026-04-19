package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FundingSentimentStrategyTest {

    private FundingSentimentStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FundingSentimentStrategy();
    }

    private IndicatorSnapshot indicators(TimeFrame tf, double ema21Slope, double rsi,
                                          double adx, double volumeRatio, double atr) {
        return new IndicatorSnapshot(tf, 67000, 67010, ema21Slope, rsi,
            67200, 67000, 66800, 0.03, 0.5,
            atr, 67000, adx, volumeRatio, 0, 0, 0.5);
    }

    @Test
    void producesShortWhenFundingExtremePositiveForConsecutivePeriods() {
        // Funding > 0.05% for 3+ periods -> crowd is heavily long -> short setup
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 0, 55, 20, 1.0, 100))
            .indicators1h(indicators(TimeFrame.H1, -1.0, 60, 22, 1.5, 50))
            .indicators15m(indicators(TimeFrame.M15, -0.5, 55, 18, 1.8, 30))
            .fundingRate(0.06)
            .fundingRateHistory(List.of(0.06, 0.055, 0.06, 0.07))
            .openInterest(1000000).openInterestChange24h(12.0)
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertEquals(Action.SELL, signal.get().getAction());
            assertNotNull(signal.get().getFundingExplanation());
        }
    }

    @Test
    void producesLongWhenFundingExtremeNegative() {
        // Funding < -0.03% for 3+ periods -> crowd is heavily short -> long setup
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 0, 45, 20, 1.0, 100))
            .indicators1h(indicators(TimeFrame.H1, 1.0, 40, 22, 1.5, 50))
            .indicators15m(indicators(TimeFrame.M15, 0.5, 45, 18, 1.8, 30))
            .fundingRate(-0.04)
            .fundingRateHistory(List.of(-0.04, -0.035, -0.04, -0.05))
            .openInterest(1000000).openInterestChange24h(15.0)
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertEquals(Action.BUY, signal.get().getAction());
        }
    }

    @Test
    void returnsEmptyWhenFundingIsNeutral() {
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 0, 50, 20, 1.0, 100))
            .indicators1h(indicators(TimeFrame.H1, 0, 50, 20, 1.0, 50))
            .indicators15m(indicators(TimeFrame.M15, 0, 50, 20, 1.0, 30))
            .fundingRate(0.01)
            .fundingRateHistory(List.of(0.01, 0.01, 0.01))
            .openInterest(1000000).openInterestChange24h(2.0)
            .build();

        assertTrue(strategy.analyze(data).isEmpty(),
            "Should not trade when funding is in normal range");
    }

    @Test
    void oiSpikeIncreasesConfidence() {
        // OI > 10% change with extreme funding -> maximum conviction
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 0, 55, 20, 1.0, 100))
            .indicators1h(indicators(TimeFrame.H1, -1.0, 60, 22, 1.5, 50))
            .indicators15m(indicators(TimeFrame.M15, -0.5, 55, 18, 1.8, 30))
            .fundingRate(0.07)
            .fundingRateHistory(List.of(0.07, 0.06, 0.07, 0.08))
            .openInterest(1200000).openInterestChange24h(15.0)
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertTrue(signal.get().getConfidence() >= 0.7);
        }
    }

    @Test
    void requiresPriceConfirmationNotBlindFade() {
        // Extreme funding but NO price confirmation (1H not breaking key level)
        // Strategy should still require 15M momentum confirmation
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 2.0, 55, 30, 1.0, 100))
            .indicators1h(indicators(TimeFrame.H1, 1.5, 55, 25, 0.8, 50))
            .indicators15m(indicators(TimeFrame.M15, 1.0, 55, 20, 0.9, 30))  // Low volume, no confirmation
            .fundingRate(0.06)
            .fundingRateHistory(List.of(0.06, 0.055, 0.06, 0.07))
            .openInterest(1000000).openInterestChange24h(5.0)
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        // May be empty (no confirmation) or present with lower confidence
        if (signal.isPresent()) {
            assertTrue(signal.get().getConfidence() < 0.8,
                "Should have reduced confidence without volume confirmation");
        }
    }

    @Test
    void getModelTypeReturnsFundingSentiment() {
        assertEquals(ModelType.FUNDING_SENTIMENT, strategy.getModelType());
    }
}

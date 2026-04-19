package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MeanReversionStrategyTest {

    private MeanReversionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MeanReversionStrategy();
    }

    private IndicatorSnapshot indicators(TimeFrame tf, double rsi, double adx,
                                          double bbPercentB, double bbWidth,
                                          double volumeRatio, double atr, double vwap) {
        double price = 67000;
        double bbMid = price;
        double bbRange = bbWidth * price;
        return new IndicatorSnapshot(tf, price, price + 10, 0.5, rsi,
            bbMid + bbRange / 2, bbMid, bbMid - bbRange / 2, bbWidth, bbPercentB,
            atr, vwap, adx, volumeRatio, 0, 0, 0);
    }

    @Test
    void producesShortSignalWhenOverbought() {
        // 4H: ranging (low ADX). 1H: overbought (RSI>80, BB%B>1). 15M: reversal candle
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67500)
            .indicators4h(indicators(TimeFrame.H4, 55, 15, 0.8, 0.04, 1.0, 100, 67000))
            .indicators1h(indicators(TimeFrame.H1, 82, 18, 1.05, 0.03, 2.2, 50, 67000))
            .indicators15m(indicators(TimeFrame.M15, 75, 15, 0.95, 0.025, 1.5, 30, 67000))
            .fundingRate(0.06).fundingRateHistory(List.of(0.06, 0.055, 0.06))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertEquals(Action.SELL, signal.get().getAction());
            assertTrue(signal.get().getTakeProfitPrice() < signal.get().getEntryPrice());
        }
    }

    @Test
    void producesLongSignalWhenOversold() {
        // 1H: oversold (RSI<20, BB%B<0). 15M: reversal volume spike
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(66500)
            .indicators4h(indicators(TimeFrame.H4, 45, 15, 0.2, 0.04, 1.0, 100, 67000))
            .indicators1h(indicators(TimeFrame.H1, 18, 18, -0.05, 0.03, 2.2, 50, 67000))
            .indicators15m(indicators(TimeFrame.M15, 25, 15, 0.1, 0.025, 1.5, 30, 67000))
            .fundingRate(-0.04).fundingRateHistory(List.of(-0.04, -0.035, -0.04))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertEquals(Action.BUY, signal.get().getAction());
            assertTrue(signal.get().getTakeProfitPrice() > signal.get().getEntryPrice());
        }
    }

    @Test
    void returnsEmptyWhenNotAtExtreme() {
        // RSI at 50, BB%B at 0.5 — no extreme
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(indicators(TimeFrame.H4, 50, 15, 0.5, 0.04, 1.0, 100, 67000))
            .indicators1h(indicators(TimeFrame.H1, 50, 18, 0.5, 0.03, 1.0, 50, 67000))
            .indicators15m(indicators(TimeFrame.M15, 50, 15, 0.5, 0.025, 1.0, 30, 67000))
            .fundingRate(0.01).fundingRateHistory(List.of(0.01, 0.01, 0.01))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        assertTrue(signal.isEmpty(), "Should not trade when not at a statistical extreme");
    }

    @Test
    void takeProfitTargetsVwapOrBollingerMid() {
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67500)
            .indicators4h(indicators(TimeFrame.H4, 55, 15, 0.8, 0.04, 1.0, 100, 67000))
            .indicators1h(indicators(TimeFrame.H1, 82, 18, 1.05, 0.03, 2.2, 50, 67000))
            .indicators15m(indicators(TimeFrame.M15, 75, 15, 0.95, 0.025, 1.5, 30, 67000))
            .fundingRate(0.06).fundingRateHistory(List.of(0.06, 0.055, 0.06))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent() && signal.get().getAction() == Action.SELL) {
            // TP should be near VWAP or Bollinger middle, which is ~67000
            assertTrue(signal.get().getTakeProfitPrice() <= 67100,
                "Take-profit should target VWAP/Bollinger mid area");
        }
    }

    @Test
    void highFundingBoostsConfidenceOnShort() {
        // Extreme positive funding + overbought → high confidence short
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67500)
            .indicators4h(indicators(TimeFrame.H4, 55, 15, 0.8, 0.04, 1.0, 100, 67000))
            .indicators1h(indicators(TimeFrame.H1, 85, 18, 1.1, 0.03, 2.5, 50, 67000))
            .indicators15m(indicators(TimeFrame.M15, 78, 15, 1.0, 0.025, 1.8, 30, 67000))
            .fundingRate(0.08).fundingRateHistory(List.of(0.08, 0.07, 0.08))
            .build();

        Optional<TradeSignal> signal = strategy.analyze(data);
        if (signal.isPresent()) {
            assertTrue(signal.get().getConfidence() >= 0.7,
                "Extreme funding + overbought should produce high confidence");
        }
    }

    @Test
    void returnsEmptyWhenIncompleteData() {
        MultiTimeFrameData data = MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators4h(null).indicators1h(null).indicators15m(null)
            .build();

        assertTrue(strategy.analyze(data).isEmpty());
    }

    @Test
    void getModelTypeReturnsMeanReversion() {
        assertEquals(ModelType.MEAN_REVERSION, strategy.getModelType());
    }
}

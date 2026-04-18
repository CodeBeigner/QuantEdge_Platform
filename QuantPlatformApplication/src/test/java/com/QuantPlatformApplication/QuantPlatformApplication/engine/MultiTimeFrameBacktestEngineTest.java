package com.QuantPlatformApplication.QuantPlatformApplication.engine;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.CandleAggregator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.IndicatorCalculator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MultiTimeFrameBacktestEngineTest {

    private MultiTimeFrameBacktestEngine engine;
    private CandleAggregator aggregator;
    private IndicatorCalculator indicatorCalc;
    private TradeRiskEngine riskEngine;

    @BeforeEach
    void setUp() {
        aggregator = new CandleAggregator();
        indicatorCalc = new IndicatorCalculator();
        riskEngine = new TradeRiskEngine();
        engine = new MultiTimeFrameBacktestEngine(aggregator, indicatorCalc, riskEngine);
    }

    private List<Candle> generate15mCandles(int count, double startPrice) {
        List<Candle> candles = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        double price = startPrice;
        for (int i = 0; i < count; i++) {
            double change = (Math.random() - 0.48) * 50; // slight upward bias
            price = Math.max(100, price + change);
            candles.add(new Candle(base.plusSeconds(i * 900L),
                price, price + 20, price - 15, price + 5, 1000 + Math.random() * 500, TimeFrame.M15));
        }
        return candles;
    }

    @Test
    void backtestWithNoSignalsReturnsInitialCapital() {
        MultiTimeFrameStrategy noOpStrategy = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().initialCapital(500).build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOpStrategy), candles, config);

        assertEquals(500, result.getInitialCapital());
        assertEquals(500, result.getFinalCapital(), 0.01);
        assertEquals(0, result.getTotalTrades());
        assertEquals(0, result.getTotalFees(), 0.01);
    }

    @Test
    void backtestAppliesSlippageAndFees() {
        // Strategy that always buys with tight stop/TP that candle range can hit.
        // Using low price (~500) where candle range (±15) can trigger 1% stop in a few candles.
        MultiTimeFrameStrategy alwaysTrade = new MultiTimeFrameStrategy() {
            private boolean inPosition = false;
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) {
                double price = data.getCurrentPrice();
                if (!inPosition) {
                    inPosition = true;
                    // Stop 1% below, TP 1.5% above — candle swings of ±15 on ~500 price
                    // can hit these levels (5 pts stop = 1%, 7.5 pts TP = 1.5%)
                    return Optional.of(TradeSignal.builder()
                        .symbol("BTCUSD").action(Action.BUY)
                        .entryPrice(price).stopLossPrice(price * 0.99)
                        .takeProfitPrice(price * 1.015).confidence(0.8)
                        .strategyName("AlwaysTrade").biasExplanation("test")
                        .triggerExplanation("test").metadata(Map.of()).build());
                } else {
                    inPosition = false;
                    return Optional.of(TradeSignal.builder()
                        .symbol("BTCUSD").action(Action.SELL)
                        .entryPrice(price).stopLossPrice(price * 1.01)
                        .takeProfitPrice(price * 0.985).confidence(0.8)
                        .strategyName("AlwaysTrade").biasExplanation("test")
                        .triggerExplanation("test").metadata(Map.of()).build());
                }
            }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "AlwaysTrade"; }
        };

        // Use low price (~500) so that candle ranges (±15) represent ~3% swings,
        // enough to hit the 1% stop or 1.5% TP
        List<Candle> candles = generate15mCandles(500, 500);
        BacktestConfig config = BacktestConfig.builder().initialCapital(500).build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(alwaysTrade), candles, config);

        assertTrue(result.getTotalFees() > 0, "Should have accumulated fees");
        assertTrue(result.getTotalTrades() > 0, "Should have executed trades");
    }

    @Test
    void equityCurveHasDataPoints() {
        MultiTimeFrameStrategy noOp = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOp), candles, config);

        assertNotNull(result.getEquityCurve());
        assertFalse(result.getEquityCurve().isEmpty());
    }

    @Test
    void sharpeRatioCalculatedCorrectly() {
        MultiTimeFrameStrategy noOp = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOp), candles, config);

        // With no trades, Sharpe should be 0 (no returns)
        assertEquals(0, result.getSharpeRatio(), 0.01);
    }

    @Test
    void riskEngineRejectsOversizedTrades() {
        // Strategy tries to trade with entry and stop very close (fee impact too high)
        MultiTimeFrameStrategy tightStop = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) {
                double price = data.getCurrentPrice();
                return Optional.of(TradeSignal.builder()
                    .symbol("BTCUSD").action(Action.BUY)
                    .entryPrice(price).stopLossPrice(price * 0.9999) // tiny stop
                    .takeProfitPrice(price * 1.01).confidence(0.8)
                    .strategyName("TightStop").metadata(Map.of()).build());
            }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "TightStop"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().initialCapital(500).build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(tightStop), candles, config);

        // Risk engine should reject these trades (fee impact too high or stop too tight)
        assertEquals(0, result.getTotalTrades(), "Risk engine should reject all trades with tiny stops");
    }

    @Test
    void maxDrawdownCalculatedCorrectly() {
        MultiTimeFrameStrategy noOp = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOp), candles, config);

        assertEquals(0, result.getMaxDrawdownPct(), 0.01); // No trades = no drawdown
    }

    @Test
    void profitFactorCalculated() {
        MultiTimeFrameStrategy noOp = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "NoOp"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(noOp), candles, config);

        assertEquals(0, result.getProfitFactor(), 0.01); // No trades
    }

    @Test
    void multipleStrategiesRunIndependently() {
        MultiTimeFrameStrategy strat1 = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.TREND_CONTINUATION; }
            public String getName() { return "Strat1"; }
        };
        MultiTimeFrameStrategy strat2 = new MultiTimeFrameStrategy() {
            public Optional<TradeSignal> analyze(MultiTimeFrameData data) { return Optional.empty(); }
            public ModelType getModelType() { return ModelType.MEAN_REVERSION; }
            public String getName() { return "Strat2"; }
        };

        List<Candle> candles = generate15mCandles(500, 60000);
        BacktestConfig config = BacktestConfig.builder().build();

        MultiTimeFrameBacktestResult result = engine.run(List.of(strat1, strat2), candles, config);

        assertNotNull(result);
        assertNotNull(result.getPerStrategyWinRate());
    }
}

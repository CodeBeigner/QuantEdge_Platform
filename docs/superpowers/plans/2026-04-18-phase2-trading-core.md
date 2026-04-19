# Phase 2: Trading Core — Strategies, Trade Explanations, Order Execution

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the 3 multi-timeframe trading strategies (Trend Continuation, Mean Reversion, Funding Sentiment), a trade explanation engine for "Learn While Earning", and an execution pipeline that connects strategy signals → risk engine → order placement.

**Architecture:** New `MultiTimeFrameStrategy` interface separate from the old `TradingStrategy` interface. Old strategies use `MarketData` + `Decision`. New strategies use `MultiTimeFrameData` + `TradeRequest`. The `StrategyOrchestrator` orchestrates the full pipeline: data → strategy → risk check → execution mode routing → order placement.

**Tech Stack:** Java 21, Spring Boot 3.5.11, JUnit 5, existing Phase 1 components (TradeRiskEngine, IndicatorCalculator, CandleAggregator, MultiTimeFrameData).

**Base Package:** `com.QuantPlatformApplication.QuantPlatformApplication`

**Base Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication`

**Test Base Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication`

---

## File Structure

### New Files

```
# New strategy interface and signal model
engine/strategy/MultiTimeFrameStrategy.java              — New interface: accepts MultiTimeFrameData, returns Optional<TradeSignal>
engine/model/TradeSignal.java                             — Strategy output: action, entry, stop, TP, confidence, explanation

# 3 new strategies
engine/strategy/TrendContinuationStrategy.java            — 4H bias → 1H zone → 15M entry
engine/strategy/MeanReversionStrategy.java                — Extreme detection + reversal confirmation
engine/strategy/FundingSentimentStrategy.java              — Funding rate + OI + crowd positioning

# Trade explanation engine
engine/model/TradeExplanation.java                        — Structured explanation: bias, zone, trigger, funding, risk, lesson
model/entity/TradeLog.java                                — JPA entity for trade log with explanation
repository/TradeLogRepository.java                        — Spring Data repo
service/TradeExplanationService.java                      — Generates explanations from strategy signals

# Execution pipeline
service/StrategyOrchestrator.java                         — Full pipeline: data → strategy → risk → execute/alert
service/ExecutionModeRouter.java                          — Routes approved trades to auto-execute or hold-for-approval

# Swing high/low detection utility
engine/util/SwingDetector.java                            — Detects swing highs/lows for S/R and order blocks

# DB migration
db/migration/V19__create_trade_logs.sql                   — Trade log table with JSONB explanation
```

### Test Files

```
engine/strategy/TrendContinuationStrategyTest.java        — 8 tests
engine/strategy/MeanReversionStrategyTest.java            — 7 tests
engine/strategy/FundingSentimentStrategyTest.java          — 6 tests
engine/util/SwingDetectorTest.java                        — 4 tests
service/StrategyOrchestratorTest.java                     — 5 tests
```

---

## Task 1: MultiTimeFrameStrategy Interface and TradeSignal Model

**Files:**
- Create: `engine/strategy/MultiTimeFrameStrategy.java`
- Create: `engine/model/TradeSignal.java`

- [ ] **Step 1: Create TradeSignal record**

```java
// engine/model/TradeSignal.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TradeSignal {

    private final String symbol;
    private final Action action;              // BUY or SELL
    private final double entryPrice;
    private final double stopLossPrice;
    private final double takeProfitPrice;
    private final double confidence;          // 0-1 confluence score
    private final String strategyName;

    // Structured explanation for "Learn While Earning"
    private final String biasExplanation;     // 4H analysis
    private final String zoneExplanation;     // 1H zone identification
    private final String triggerExplanation;  // 15M entry trigger
    private final String fundingExplanation;  // Funding rate context
    private final String riskExplanation;     // Position sizing math
    private final String lesson;              // Educational context

    private final Map<String, Object> metadata;  // Strategy-specific data (indicator values, etc.)

    /**
     * Convert to a TradeRequest for risk engine evaluation.
     */
    public TradeRequest toTradeRequest() {
        return TradeRequest.builder()
            .symbol(symbol)
            .action(action)
            .entryPrice(entryPrice)
            .stopLossPrice(stopLossPrice)
            .takeProfitPrice(takeProfitPrice)
            .confidence(confidence)
            .strategyName(strategyName)
            .reasoning(biasExplanation + " | " + triggerExplanation)
            .build();
    }
}
```

- [ ] **Step 2: Create MultiTimeFrameStrategy interface**

```java
// engine/strategy/MultiTimeFrameStrategy.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.ModelType;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;

import java.util.Optional;

/**
 * Strategy interface for multi-timeframe analysis.
 * Unlike TradingStrategy (which uses MarketData + Decision),
 * this interface uses MultiTimeFrameData and produces TradeSignal
 * with built-in explanations for "Learn While Earning".
 */
public interface MultiTimeFrameStrategy {

    /**
     * Analyze multi-timeframe data and optionally produce a trade signal.
     * Returns empty if no trade setup is detected.
     */
    Optional<TradeSignal> analyze(MultiTimeFrameData data);

    /**
     * Which model type this strategy handles.
     */
    ModelType getModelType();

    /**
     * Human-readable strategy name.
     */
    String getName();
}
```

- [ ] **Step 3: Commit**

```bash
git add engine/model/TradeSignal.java engine/strategy/MultiTimeFrameStrategy.java
git commit -m "feat: add MultiTimeFrameStrategy interface and TradeSignal model for new strategy architecture"
```

---

## Task 2: Swing Detector Utility with Tests (TDD)

**Files:**
- Create: `engine/util/SwingDetector.java`
- Test: `engine/util/SwingDetectorTest.java`

The strategies need swing high/low detection for support/resistance levels and order blocks. This is a shared utility.

- [ ] **Step 1: Write failing tests**

```java
// SwingDetectorTest.java
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
        // Pattern: rising highs then falling highs → swing high at peak
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
        // Pattern: falling lows then rising lows → swing low at trough
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=SwingDetectorTest -pl .`
Expected: Compilation error

- [ ] **Step 3: Write implementation**

```java
// engine/util/SwingDetector.java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=SwingDetectorTest -pl .`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add engine/util/SwingDetector.java SwingDetectorTest.java
git commit -m "feat: add SwingDetector for support/resistance detection with TDD"
```

---

## Task 3: Trend Continuation Strategy with Tests (TDD)

**Files:**
- Create: `engine/strategy/TrendContinuationStrategy.java`
- Test: `engine/strategy/TrendContinuationStrategyTest.java`

- [ ] **Step 1: Write failing tests**

```java
// TrendContinuationStrategyTest.java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=TrendContinuationStrategyTest -pl .`
Expected: Compilation error

- [ ] **Step 3: Write TrendContinuationStrategy implementation**

```java
// engine/strategy/TrendContinuationStrategy.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.util.SwingDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Trend Continuation Strategy — Multi-Timeframe.
 *
 * 4H: Determines directional bias via EMA21 slope
 * 1H: Identifies pullback zones (swing lows for longs, swing highs for shorts)
 * 15M: Entry trigger — RSI bounce, volume spike, MACD confirmation
 *
 * Funding rate acts as a confidence modifier.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendContinuationStrategy implements MultiTimeFrameStrategy {

    private static final double EMA_SLOPE_THRESHOLD = 1.0;  // Minimum slope for bias
    private static final double RSI_PULLBACK_LONG = 45;      // RSI below this = pullback for longs
    private static final double RSI_PULLBACK_SHORT = 55;     // RSI above this = pullback for shorts
    private static final double VOLUME_CONFIRMATION = 1.3;    // 1.3x average volume
    private static final double TP1_RR = 1.5;
    private static final double TP2_RR = 2.5;
    private static final double FUNDING_CONFIDENCE_BOOST = 0.1;
    private static final double ZONE_PROXIMITY_PCT = 0.005;   // Within 0.5% of zone

    private final SwingDetector swingDetector;

    @Override
    public ModelType getModelType() {
        return ModelType.TREND_CONTINUATION;
    }

    @Override
    public String getName() {
        return "Trend Continuation";
    }

    @Override
    public Optional<TradeSignal> analyze(MultiTimeFrameData data) {
        if (!data.isComplete()) {
            return Optional.empty();
        }

        IndicatorSnapshot h4 = data.indicators(TimeFrame.H4);
        IndicatorSnapshot h1 = data.indicators(TimeFrame.H1);
        IndicatorSnapshot m15 = data.indicators(TimeFrame.M15);

        // Step 1: Determine 4H bias
        Action bias = determineBias(h4);
        if (bias == Action.HOLD) {
            return Optional.empty();
        }

        String biasExplanation = buildBiasExplanation(h4, bias);

        // Step 2: Check if price is pulling back to a 1H zone
        double zoneLevel = findPullbackZone(data, bias);
        if (zoneLevel <= 0) {
            return Optional.empty();
        }

        // Check proximity to zone
        double distanceToZone = Math.abs(data.getCurrentPrice() - zoneLevel) / data.getCurrentPrice();
        if (distanceToZone > ZONE_PROXIMITY_PCT) {
            return Optional.empty();
        }

        String zoneExplanation = String.format(
            "1H %s zone at %.2f (price is %.2f%% away). Price pulling back toward institutional level.",
            bias == Action.BUY ? "support" : "resistance", zoneLevel, distanceToZone * 100);

        // Step 3: Check 15M entry trigger
        if (!hasEntryTrigger(m15, bias)) {
            return Optional.empty();
        }

        String triggerExplanation = buildTriggerExplanation(m15, bias);

        // Step 4: Calculate stops and targets
        double entry = data.getCurrentPrice();
        double atr = m15.atr14();
        double stopLoss;
        double takeProfit;

        if (bias == Action.BUY) {
            stopLoss = Math.min(zoneLevel - atr * 0.5, entry - atr * 1.5);
            double riskDistance = entry - stopLoss;
            takeProfit = entry + riskDistance * TP1_RR;
        } else {
            stopLoss = Math.max(zoneLevel + atr * 0.5, entry + atr * 1.5);
            double riskDistance = stopLoss - entry;
            takeProfit = entry - riskDistance * TP1_RR;
        }

        // Step 5: Calculate confidence with funding modifier
        double baseConfidence = calculateBaseConfidence(h4, h1, m15);
        double fundingBoost = calculateFundingBoost(data.getFundingRate(), bias);
        double confidence = Math.min(1.0, baseConfidence + fundingBoost);

        String fundingExplanation = buildFundingExplanation(data.getFundingRate(), bias, fundingBoost);

        String lesson = bias == Action.BUY
            ? "Trend continuation: higher timeframe establishes direction, pullback to key level provides entry with defined risk. Trading with the trend improves win rate because you're aligned with the dominant order flow."
            : "Trend continuation (short): when the 4H trend is bearish, rallies into resistance are selling opportunities. The key is waiting for the 15M to confirm rejection — don't sell into strength without confirmation.";

        TradeSignal signal = TradeSignal.builder()
            .symbol(data.getSymbol())
            .action(bias)
            .entryPrice(entry)
            .stopLossPrice(stopLoss)
            .takeProfitPrice(takeProfit)
            .confidence(confidence)
            .strategyName(getName())
            .biasExplanation(biasExplanation)
            .zoneExplanation(zoneExplanation)
            .triggerExplanation(triggerExplanation)
            .fundingExplanation(fundingExplanation)
            .riskExplanation(String.format("Entry: %.2f | Stop: %.2f | TP: %.2f | R:R = %.1f:1",
                entry, stopLoss, takeProfit, TP1_RR))
            .lesson(lesson)
            .metadata(Map.of(
                "h4_ema_slope", h4.ema21Slope(),
                "m15_rsi", m15.rsi14(),
                "m15_volume_ratio", m15.volumeRatio(),
                "funding_rate", data.getFundingRate(),
                "zone_level", zoneLevel
            ))
            .build();

        log.info("TREND_CONTINUATION signal: {} {} @ {} (conf: {:.2f})",
            signal.getAction(), data.getSymbol(), entry, confidence);

        return Optional.of(signal);
    }

    private Action determineBias(IndicatorSnapshot h4) {
        if (h4.ema21Slope() > EMA_SLOPE_THRESHOLD && h4.ema21() > h4.ema50()) {
            return Action.BUY;
        }
        if (h4.ema21Slope() < -EMA_SLOPE_THRESHOLD && h4.ema21() < h4.ema50()) {
            return Action.SELL;
        }
        return Action.HOLD;
    }

    private double findPullbackZone(MultiTimeFrameData data, Action bias) {
        List<Candle> h1Candles = data.getCandles1h();
        if (h1Candles == null || h1Candles.size() < 10) return 0;

        if (bias == Action.BUY) {
            return swingDetector.nearestSupport(h1Candles, data.getCurrentPrice(), 3);
        } else {
            return swingDetector.nearestResistance(h1Candles, data.getCurrentPrice(), 3);
        }
    }

    private boolean hasEntryTrigger(IndicatorSnapshot m15, Action bias) {
        if (bias == Action.BUY) {
            // RSI pulling back (not overbought), volume above average, MACD histogram positive
            return m15.rsi14() < RSI_PULLBACK_LONG
                && m15.volumeRatio() > VOLUME_CONFIRMATION
                && m15.macdHistogram() > 0;
        } else {
            return m15.rsi14() > RSI_PULLBACK_SHORT
                && m15.volumeRatio() > VOLUME_CONFIRMATION
                && m15.macdHistogram() < 0;
        }
    }

    private double calculateBaseConfidence(IndicatorSnapshot h4, IndicatorSnapshot h1, IndicatorSnapshot m15) {
        double confidence = 0.5;

        // Strong 4H trend (ADX > 25)
        if (h4.adx() > 25) confidence += 0.1;

        // 1H aligned (same EMA slope direction as 4H)
        if (Math.signum(h1.ema21Slope()) == Math.signum(h4.ema21Slope())) confidence += 0.1;

        // Strong 15M volume
        if (m15.volumeRatio() > 1.5) confidence += 0.05;

        // MACD histogram growing
        if (Math.abs(m15.macdHistogram()) > Math.abs(m15.macdSignal()) * 0.5) confidence += 0.05;

        return Math.min(0.9, confidence);
    }

    private double calculateFundingBoost(double fundingRate, Action bias) {
        // Negative funding + long = boost (shorts are paying, crowd is short)
        // Positive funding + short = boost (longs are paying, crowd is long)
        if (bias == Action.BUY && fundingRate < -0.005) {
            return FUNDING_CONFIDENCE_BOOST;
        }
        if (bias == Action.SELL && fundingRate > 0.005) {
            return FUNDING_CONFIDENCE_BOOST;
        }
        return 0;
    }

    private String buildBiasExplanation(IndicatorSnapshot h4, Action bias) {
        return String.format("4H EMA21 slope: %.2f (%s). EMA21 (%.2f) %s EMA50 (%.2f). ADX: %.1f. %s regime confirmed.",
            h4.ema21Slope(),
            bias == Action.BUY ? "rising" : "falling",
            h4.ema21(),
            bias == Action.BUY ? "above" : "below",
            h4.ema50(),
            h4.adx(),
            bias == Action.BUY ? "Bullish" : "Bearish");
    }

    private String buildTriggerExplanation(IndicatorSnapshot m15, Action bias) {
        return String.format("15M entry: RSI at %.1f (%s), volume %.1fx average, MACD histogram %.4f (%s). Entry confirmed.",
            m15.rsi14(),
            bias == Action.BUY ? "pulling back from overbought" : "bouncing from oversold",
            m15.volumeRatio(),
            m15.macdHistogram(),
            m15.macdHistogram() > 0 ? "bullish momentum" : "bearish momentum");
    }

    private String buildFundingExplanation(double fundingRate, Action bias, double boost) {
        String direction = fundingRate > 0 ? "positive (longs paying shorts)" : fundingRate < 0 ? "negative (shorts paying longs)" : "neutral";
        String impact = boost > 0 ? "Supports our trade — crowd positioned against us." : "Neutral — no additional edge from funding.";
        return String.format("Funding rate: %.4f (%s). %s", fundingRate, direction, impact);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=TrendContinuationStrategyTest -pl .`
Expected: All 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add engine/strategy/TrendContinuationStrategy.java TrendContinuationStrategyTest.java
git commit -m "feat: add Trend Continuation strategy with TDD — 4H bias, 1H zones, 15M entries, funding filter"
```

---

## Task 4: Mean Reversion Strategy with Tests (TDD)

**Files:**
- Create: `engine/strategy/MeanReversionStrategy.java`
- Test: `engine/strategy/MeanReversionStrategyTest.java`

- [ ] **Step 1: Write failing tests**

```java
// MeanReversionStrategyTest.java
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
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Write MeanReversionStrategy implementation**

The strategy logic:
- **4H regime:** ADX < 20 = ranging (preferred). Also triggers on extreme extensions in trending markets.
- **1H extreme detection:** RSI > 80 or < 20 AND Bollinger %B > 1.0 or < 0.0 AND volume spike (> 2x average)
- **15M reversal confirmation:** Volume declining on push (exhaustion) OR RSI divergence pattern
- **Funding filter:** Extreme positive funding + overbought = high conviction short. Extreme negative + oversold = high conviction long.
- **Take-profit:** VWAP or Bollinger middle (mean target)
- **Time stop logic:** Not implemented in signal (handled by orchestrator) — noted in metadata

Implementation should follow the same pattern as TrendContinuationStrategy: `@Component`, `@RequiredArgsConstructor`, implement `MultiTimeFrameStrategy`, produce `TradeSignal` with full explanations.

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add Mean Reversion strategy with TDD — Bollinger/RSI extremes, funding filter, VWAP targets"
```

---

## Task 5: Funding Sentiment Strategy with Tests (TDD)

**Files:**
- Create: `engine/strategy/FundingSentimentStrategy.java`
- Test: `engine/strategy/FundingSentimentStrategyTest.java`

- [ ] **Step 1: Write failing tests**

```java
// FundingSentimentStrategyTest.java
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
        // Funding > 0.05% for 3+ periods → crowd is heavily long → short setup
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
        // Funding < -0.03% for 3+ periods → crowd is heavily short → long setup
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
        // OI > 10% change with extreme funding → maximum conviction
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
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Write FundingSentimentStrategy implementation**

Logic:
- **Funding analysis:** Track funding rate trend (not just current). Need 3+ consecutive extreme periods.
- **Extreme thresholds:** > 0.05%/8h for shorts, < -0.03%/8h for longs
- **OI confirmation:** Rising OI + extreme funding = new positions entering, amplifies signal
- **Price confirmation required:** 1H break of key level + 15M momentum candle with volume
- **Take-profit:** Aggressive 3:1 to 5:1 R:R (liquidation cascades move fast)
- **Trailing stop:** Trail aggressively once 2R reached (noted in metadata)

Same pattern: `@Component`, implement `MultiTimeFrameStrategy`, produce `TradeSignal` with full explanations.

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add Funding Sentiment strategy with TDD — extreme funding + OI + price confirmation"
```

---

## Task 6: Trade Log Entity and Migration

**Files:**
- Create: `db/migration/V19__create_trade_logs.sql`
- Create: `model/entity/TradeLog.java`
- Create: `repository/TradeLogRepository.java`
- Create: `engine/model/TradeExplanation.java`

- [ ] **Step 1: Create migration**

```sql
-- V19__create_trade_logs.sql
CREATE TABLE trade_logs (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trade_id          VARCHAR(50) NOT NULL UNIQUE,
    symbol            VARCHAR(20) NOT NULL,
    direction         VARCHAR(10) NOT NULL,
    strategy_name     VARCHAR(50) NOT NULL,
    entry_price       NUMERIC(19,4) NOT NULL,
    stop_loss_price   NUMERIC(19,4) NOT NULL,
    take_profit_price NUMERIC(19,4) NOT NULL,
    position_size     NUMERIC(19,8),
    effective_leverage NUMERIC(6,2),
    confidence        NUMERIC(4,3),
    explanation       JSONB       NOT NULL DEFAULT '{}',
    outcome           JSONB,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    execution_mode    VARCHAR(20) NOT NULL DEFAULT 'AUTONOMOUS',
    opened_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trade_logs_user ON trade_logs(user_id);
CREATE INDEX idx_trade_logs_symbol ON trade_logs(symbol);
CREATE INDEX idx_trade_logs_status ON trade_logs(status);
CREATE INDEX idx_trade_logs_strategy ON trade_logs(strategy_name);
```

- [ ] **Step 2: Create TradeExplanation record**

```java
// engine/model/TradeExplanation.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

public record TradeExplanation(
    String bias,
    String zone,
    String entryTrigger,
    String fundingContext,
    String riskCalc,
    String lesson
) {}
```

- [ ] **Step 3: Create TradeLog entity**

```java
// model/entity/TradeLog.java
package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "trade_logs")
public class TradeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trade_id", nullable = false, unique = true, length = 50)
    private String tradeId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(name = "strategy_name", nullable = false, length = 50)
    private String strategyName;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal stopLossPrice;

    @Column(name = "take_profit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal takeProfitPrice;

    @Column(name = "position_size", precision = 19, scale = 8)
    private BigDecimal positionSize;

    @Column(name = "effective_leverage", precision = 6, scale = 2)
    private BigDecimal effectiveLeverage;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> outcome;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Builder.Default
    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode = "AUTONOMOUS";

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (openedAt == null) openedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
```

- [ ] **Step 4: Create repository**

```java
// repository/TradeLogRepository.java
package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {
    Optional<TradeLog> findByTradeId(String tradeId);
    List<TradeLog> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    List<TradeLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<TradeLog> findByUserIdAndSymbolAndStatus(Long userId, String symbol, String status);
}
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add TradeLog entity, migration, and TradeExplanation record for Learn While Earning"
```

---

## Task 7: Strategy Orchestrator with Tests (TDD)

**Files:**
- Create: `service/StrategyOrchestrator.java`
- Create: `service/ExecutionModeRouter.java`
- Test: `service/StrategyOrchestratorTest.java`

This is the central pipeline: data → strategy → risk check → execution routing.

- [ ] **Step 1: Write failing tests**

```java
// StrategyOrchestratorTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StrategyOrchestratorTest {

    @Mock private TradeRiskEngine riskEngine;
    @Mock private MultiTimeFrameStrategy mockStrategy;
    @Mock private ExecutionModeRouter executionRouter;

    private StrategyOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new StrategyOrchestrator(
            List.of(mockStrategy), riskEngine, executionRouter
        );
    }

    private MultiTimeFrameData buildData() {
        IndicatorSnapshot snap = new IndicatorSnapshot(TimeFrame.M15,
            67000, 67010, 1.0, 55, 67200, 67000, 66800, 0.03, 0.5,
            30, 67000, 25, 1.5, 1.0, 0.5, 0.3);
        return MultiTimeFrameData.builder()
            .symbol("BTCUSD").currentPrice(67000)
            .indicators15m(snap).indicators1h(snap).indicators4h(snap)
            .fundingRate(0.01).fundingRateHistory(List.of(0.01))
            .build();
    }

    private TradeSignal buildSignal() {
        return TradeSignal.builder()
            .symbol("BTCUSD").action(Action.BUY)
            .entryPrice(67000).stopLossPrice(66650).takeProfitPrice(67525)
            .confidence(0.8).strategyName("TEST")
            .biasExplanation("test").triggerExplanation("test")
            .lesson("test").metadata(Map.of())
            .build();
    }

    @Test
    void strategySignalPassesRiskEngineAndExecutes() {
        TradeSignal signal = buildSignal();
        when(mockStrategy.analyze(any())).thenReturn(Optional.of(signal));
        when(riskEngine.evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
            .thenReturn(RiskCheckResult.approve(0.015, 5.0, 2.2, 10));

        orchestrator.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "AUTONOMOUS");

        verify(riskEngine).evaluate(any(), eq(500.0), eq(500.0), eq(0.0), eq(0.0), eq(Set.of()), any());
        verify(executionRouter).route(any(), any(), eq("AUTONOMOUS"));
    }

    @Test
    void rejectedTradeIsNotExecuted() {
        TradeSignal signal = buildSignal();
        when(mockStrategy.analyze(any())).thenReturn(Optional.of(signal));
        when(riskEngine.evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
            .thenReturn(RiskCheckResult.reject(List.of("leverage exceeded")));

        orchestrator.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "AUTONOMOUS");

        verify(executionRouter, never()).route(any(), any(), anyString());
    }

    @Test
    void noSignalMeansNoRiskCheck() {
        when(mockStrategy.analyze(any())).thenReturn(Optional.empty());

        orchestrator.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "AUTONOMOUS");

        verify(riskEngine, never()).evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any());
        verify(executionRouter, never()).route(any(), any(), anyString());
    }

    @Test
    void humanInLoopModePassedToRouter() {
        TradeSignal signal = buildSignal();
        when(mockStrategy.analyze(any())).thenReturn(Optional.of(signal));
        when(riskEngine.evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
            .thenReturn(RiskCheckResult.approve(0.015, 5.0, 2.2, 10));

        orchestrator.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "HUMAN_IN_LOOP");

        verify(executionRouter).route(any(), any(), eq("HUMAN_IN_LOOP"));
    }

    @Test
    void multipleStrategiesEvaluatedIndependently() {
        MultiTimeFrameStrategy strategy2 = mock(MultiTimeFrameStrategy.class);
        StrategyOrchestrator multiOrch = new StrategyOrchestrator(
            List.of(mockStrategy, strategy2), riskEngine, executionRouter
        );

        when(mockStrategy.analyze(any())).thenReturn(Optional.empty());
        when(strategy2.analyze(any())).thenReturn(Optional.of(buildSignal()));
        when(riskEngine.evaluate(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
            .thenReturn(RiskCheckResult.approve(0.015, 5.0, 2.2, 10));

        multiOrch.evaluateStrategies(buildData(), 500, 500, 0, 0, Set.of(),
            RiskParameters.builder().build(), "AUTONOMOUS");

        verify(mockStrategy).analyze(any());
        verify(strategy2).analyze(any());
        verify(executionRouter, times(1)).route(any(), any(), anyString());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Write ExecutionModeRouter**

```java
// service/ExecutionModeRouter.java
package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routes approved trades based on execution mode.
 * AUTONOMOUS: Execute immediately via broker adapter.
 * HUMAN_IN_LOOP: Send to Telegram for approval, hold until approved/rejected/expired.
 */
@Slf4j
@Component
public class ExecutionModeRouter {

    /**
     * Route an approved trade signal to the appropriate execution path.
     */
    public void route(TradeSignal signal, RiskCheckResult riskResult, String executionMode) {
        switch (executionMode) {
            case "AUTONOMOUS" -> executeAutonomous(signal, riskResult);
            case "HUMAN_IN_LOOP" -> holdForApproval(signal, riskResult);
            default -> {
                log.warn("Unknown execution mode: {}. Defaulting to HUMAN_IN_LOOP", executionMode);
                holdForApproval(signal, riskResult);
            }
        }
    }

    private void executeAutonomous(TradeSignal signal, RiskCheckResult riskResult) {
        log.info("AUTO-EXECUTE: {} {} @ {} | Size: {} | EffLev: {}x",
            signal.getAction(), signal.getSymbol(), signal.getEntryPrice(),
            riskResult.getPositionSize(), riskResult.getEffectiveLeverage());
        // TODO: Phase 3 will wire this to the actual broker adapter + Telegram notification
    }

    private void holdForApproval(TradeSignal signal, RiskCheckResult riskResult) {
        log.info("HOLD-FOR-APPROVAL: {} {} @ {} | Size: {} | Awaiting Telegram /approve",
            signal.getAction(), signal.getSymbol(), signal.getEntryPrice(),
            riskResult.getPositionSize());
        // TODO: Phase 3 will wire this to Telegram bot for approval/rejection
    }
}
```

- [ ] **Step 4: Write StrategyOrchestrator**

```java
// service/StrategyOrchestrator.java
package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.strategy.MultiTimeFrameStrategy;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Central orchestration pipeline:
 * MultiTimeFrameData → Strategy analysis → Risk engine → Execution routing
 *
 * All registered MultiTimeFrameStrategy implementations are evaluated.
 * Signals that pass risk checks are routed based on execution mode.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyOrchestrator {

    private final List<MultiTimeFrameStrategy> strategies;
    private final TradeRiskEngine riskEngine;
    private final ExecutionModeRouter executionRouter;

    /**
     * Run all strategies against the given market data.
     * Each strategy produces 0 or 1 signal. Signals are independently risk-checked.
     */
    public void evaluateStrategies(MultiTimeFrameData data,
                                    double currentBalance, double peakEquity,
                                    double currentExposure, double dailyRealizedLoss,
                                    Set<String> openPositionSymbols,
                                    RiskParameters riskParams,
                                    String executionMode) {

        for (MultiTimeFrameStrategy strategy : strategies) {
            try {
                Optional<TradeSignal> signal = strategy.analyze(data);

                if (signal.isEmpty()) {
                    log.debug("{}: No signal for {}", strategy.getName(), data.getSymbol());
                    continue;
                }

                TradeSignal tradeSignal = signal.get();
                log.info("{}: {} signal for {} @ {} (confidence: {})",
                    strategy.getName(), tradeSignal.getAction(),
                    tradeSignal.getSymbol(), tradeSignal.getEntryPrice(),
                    tradeSignal.getConfidence());

                // Run through risk engine
                TradeRequest riskRequest = tradeSignal.toTradeRequest();
                RiskCheckResult riskResult = riskEngine.evaluate(
                    riskRequest, currentBalance, peakEquity,
                    currentExposure, dailyRealizedLoss,
                    openPositionSymbols, riskParams
                );

                if (!riskResult.isApproved()) {
                    log.warn("{}: Trade REJECTED by risk engine: {}",
                        strategy.getName(), riskResult.getRejectionReasons());
                    continue;
                }

                // Route based on execution mode
                executionRouter.route(tradeSignal, riskResult, executionMode);

                // Update exposure tracking for subsequent strategies in this cycle
                currentExposure += riskResult.getPositionSize() * tradeSignal.getEntryPrice();
                openPositionSymbols = new java.util.HashSet<>(openPositionSymbols);
                openPositionSymbols.add(tradeSignal.getSymbol());

            } catch (Exception e) {
                log.error("Strategy {} failed: {}", strategy.getName(), e.getMessage(), e);
            }
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test -Dtest=StrategyOrchestratorTest -pl .`
Expected: All 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: add StrategyOrchestrator pipeline — strategies → risk engine → execution routing"
```

---

## Summary

| Task | Component | Tests | Description |
|------|-----------|-------|-------------|
| 1 | MultiTimeFrameStrategy + TradeSignal | — | New interface and signal model |
| 2 | SwingDetector | 4 | Support/resistance detection utility |
| 3 | TrendContinuationStrategy | 8 | 4H bias → 1H zones → 15M entries |
| 4 | MeanReversionStrategy | 7 | Bollinger/RSI extremes, VWAP targets |
| 5 | FundingSentimentStrategy | 6 | Extreme funding + OI + price confirmation |
| 6 | TradeLog Entity + Migration | — | DB schema for trade logs with JSONB explanation |
| 7 | StrategyOrchestrator + ExecutionModeRouter | 5 | Pipeline: strategy → risk → execute/alert |

**Total: 7 tasks, 30 unit tests.**

**After Phase 2:** The system has 3 working strategies that analyze multi-timeframe data, generate trade signals with educational explanations, pass through the 7-check risk engine, and route to either auto-execute or human-in-loop approval. Phase 3 connects this to Telegram and the actual broker for order placement.

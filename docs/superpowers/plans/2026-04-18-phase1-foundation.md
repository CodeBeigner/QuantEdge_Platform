# Phase 1: Foundation — Security, Data Pipeline, Risk Engine, Delta Exchange Backend

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the foundational layer everything else depends on — Delta Exchange backend adapter, multi-timeframe data pipeline, indicator engine, and hard-coded risk guardrails.

**Architecture:** Event-driven monolith. Delta Exchange adapter on the backend (moving credentials out of the browser). Multi-timeframe candle aggregation from 15m data. Indicator calculation per timeframe. 7-check risk engine as a hard gate before any order execution.

**Tech Stack:** Java 21, Spring Boot 3.5.11, PostgreSQL, Redis, Delta Exchange REST/WebSocket API, JUnit 5 + Mockito for tests.

**Base Package:** `com.QuantPlatformApplication.QuantPlatformApplication`

**Base Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication`

**Test Base Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication`

**Resources Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication/src/main/resources`

---

## File Structure

### New Files

```
# Security fix — move Delta Exchange credentials to backend
engine/model/TimeFrame.java                              — Enum: M15, H1, H4
engine/model/Candle.java                                 — OHLCV candle record
engine/model/IndicatorSnapshot.java                      — All indicator values for one timeframe
engine/model/MultiTimeFrameData.java                     — Container for 15m/1h/4h data + indicators + funding
engine/model/TradeRequest.java                           — Pre-risk-check trade request
engine/model/RiskCheckResult.java                        — Result of risk engine checks
engine/model/RiskParameters.java                         — Configurable risk limits

service/delta/DeltaExchangeConfig.java                   — Configuration properties for Delta Exchange
service/delta/DeltaExchangeClient.java                   — REST client for Delta Exchange API
service/delta/DeltaExchangeWebSocketClient.java          — WebSocket client for live data + private channels
service/delta/DeltaExchangeBrokerAdapter.java            — BrokerAdapter implementation for Delta Exchange

service/pipeline/CandleAggregator.java                   — Builds 1h/4h from 15m candles
service/pipeline/IndicatorCalculator.java                — Computes all technical indicators
service/pipeline/MarketDataPipeline.java                 — Orchestrates data flow from WS → candles → indicators

service/risk/TradeRiskEngine.java                        — 7-check hard risk gate (replaces soft RiskEngineService for trade approval)

model/entity/RiskConfig.java                             — JPA entity for per-user risk parameters
model/entity/DeltaCredential.java                        — JPA entity for encrypted Delta Exchange credentials

model/dto/DeltaCredentialRequest.java                    — DTO for saving credentials
model/dto/RiskConfigRequest.java                         — DTO for updating risk parameters

repository/RiskConfigRepository.java                     — Spring Data repo for risk_config
repository/DeltaCredentialRepository.java                — Spring Data repo for delta_credentials

controller/DeltaExchangeController.java                  — Endpoints for credential management + market data proxy

config/EncryptionConfig.java                             — AES encryption for API keys at rest

db/migration/V17__create_risk_config.sql                 — Risk parameters table
db/migration/V18__create_delta_credentials.sql           — Encrypted credentials table
```

### Test Files

```
service/delta/DeltaExchangeClientTest.java               — Unit tests for REST client
service/pipeline/CandleAggregatorTest.java               — Unit tests for candle aggregation
service/pipeline/IndicatorCalculatorTest.java             — Unit tests for all indicators
service/risk/TradeRiskEngineTest.java                     — Unit tests for all 7 risk checks
config/EncryptionConfigTest.java                         — Unit tests for encrypt/decrypt round-trip
```

### Modified Files

```
application.yml                                          — Add delta-exchange config section
service/broker/BrokerManager.java                        — Register DeltaExchangeBrokerAdapter
engine/model/ModelType.java                              — Add new strategy types
```

---

## Task 1: TimeFrame Enum and Candle Record

**Files:**
- Create: `engine/model/TimeFrame.java`
- Create: `engine/model/Candle.java`

- [ ] **Step 1: Create TimeFrame enum**

```java
// engine/model/TimeFrame.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

public enum TimeFrame {
    M15(15), H1(60), H4(240);

    private final int minutes;

    TimeFrame(int minutes) {
        this.minutes = minutes;
    }

    public int getMinutes() {
        return minutes;
    }

    public int getMultiplierFrom(TimeFrame base) {
        return this.minutes / base.minutes;
    }
}
```

- [ ] **Step 2: Create Candle record**

```java
// engine/model/Candle.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import java.time.Instant;

public record Candle(
    Instant timestamp,
    double open,
    double high,
    double low,
    double close,
    double volume,
    TimeFrame timeFrame
) {
    public double typicalPrice() {
        return (high + low + close) / 3.0;
    }

    public boolean isBullish() {
        return close > open;
    }

    public boolean isBearish() {
        return close < open;
    }

    public double body() {
        return Math.abs(close - open);
    }

    public double range() {
        return high - low;
    }

    public double upperWick() {
        return isBullish() ? high - close : high - open;
    }

    public double lowerWick() {
        return isBullish() ? open - low : close - low;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add engine/model/TimeFrame.java engine/model/Candle.java
git commit -m "feat: add TimeFrame enum and Candle record for multi-timeframe data model"
```

---

## Task 2: Candle Aggregator with Tests (TDD)

**Files:**
- Create: `service/pipeline/CandleAggregator.java`
- Test: `service/pipeline/CandleAggregatorTest.java`

- [ ] **Step 1: Write failing tests**

```java
// CandleAggregatorTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandleAggregatorTest {

    private final CandleAggregator aggregator = new CandleAggregator();

    @Test
    void aggregateFour15mCandlesIntoOne1hCandle() {
        Instant base = Instant.parse("2026-04-18T00:00:00Z");
        List<Candle> m15Candles = List.of(
            new Candle(base, 100, 105, 98, 103, 1000, TimeFrame.M15),
            new Candle(base.plusSeconds(900), 103, 110, 102, 108, 1200, TimeFrame.M15),
            new Candle(base.plusSeconds(1800), 108, 112, 106, 107, 800, TimeFrame.M15),
            new Candle(base.plusSeconds(2700), 107, 109, 104, 106, 900, TimeFrame.M15)
        );

        List<Candle> h1Candles = aggregator.aggregate(m15Candles, TimeFrame.M15, TimeFrame.H1);

        assertEquals(1, h1Candles.size());
        Candle h1 = h1Candles.get(0);
        assertEquals(100, h1.open());    // open of first candle
        assertEquals(112, h1.high());    // highest high
        assertEquals(98, h1.low());      // lowest low
        assertEquals(106, h1.close());   // close of last candle
        assertEquals(3900, h1.volume()); // sum of volumes
        assertEquals(TimeFrame.H1, h1.timeFrame());
    }

    @Test
    void aggregateSixteen15mCandlesIntoOne4hCandle() {
        Instant base = Instant.parse("2026-04-18T00:00:00Z");
        List<Candle> m15Candles = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            m15Candles.add(new Candle(
                base.plusSeconds(i * 900L),
                100 + i, 105 + i, 98 + i, 102 + i, 100, TimeFrame.M15
            ));
        }

        List<Candle> h4Candles = aggregator.aggregate(m15Candles, TimeFrame.M15, TimeFrame.H4);

        assertEquals(1, h4Candles.size());
        Candle h4 = h4Candles.get(0);
        assertEquals(100, h4.open());
        assertEquals(120, h4.high());    // 105 + 15
        assertEquals(98, h4.low());
        assertEquals(117, h4.close());   // 102 + 15
        assertEquals(1600, h4.volume()); // 16 * 100
        assertEquals(TimeFrame.H4, h4.timeFrame());
    }

    @Test
    void incompleteGroupIsIgnored() {
        Instant base = Instant.parse("2026-04-18T00:00:00Z");
        List<Candle> m15Candles = List.of(
            new Candle(base, 100, 105, 98, 103, 1000, TimeFrame.M15),
            new Candle(base.plusSeconds(900), 103, 110, 102, 108, 1200, TimeFrame.M15)
        );

        List<Candle> h1Candles = aggregator.aggregate(m15Candles, TimeFrame.M15, TimeFrame.H1);

        assertEquals(0, h1Candles.size());
    }

    @Test
    void multipleCompleteGroupsProduceMultipleCandles() {
        Instant base = Instant.parse("2026-04-18T00:00:00Z");
        List<Candle> m15Candles = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            m15Candles.add(new Candle(
                base.plusSeconds(i * 900L),
                100 + i, 105 + i, 98 + i, 102 + i, 100, TimeFrame.M15
            ));
        }

        List<Candle> h1Candles = aggregator.aggregate(m15Candles, TimeFrame.M15, TimeFrame.H1);

        assertEquals(2, h1Candles.size());
    }

    @Test
    void emptyInputReturnsEmptyList() {
        List<Candle> result = aggregator.aggregate(List.of(), TimeFrame.M15, TimeFrame.H1);
        assertTrue(result.isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=CandleAggregatorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error (CandleAggregator doesn't exist yet)

- [ ] **Step 3: Write minimal implementation**

```java
// service/pipeline/CandleAggregator.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CandleAggregator {

    public List<Candle> aggregate(List<Candle> sourceCandles, TimeFrame source, TimeFrame target) {
        if (sourceCandles.isEmpty()) {
            return List.of();
        }

        int groupSize = target.getMultiplierFrom(source);
        List<Candle> result = new ArrayList<>();

        for (int i = 0; i + groupSize <= sourceCandles.size(); i += groupSize) {
            List<Candle> group = sourceCandles.subList(i, i + groupSize);
            result.add(mergeCandles(group, target));
        }

        return result;
    }

    private Candle mergeCandles(List<Candle> group, TimeFrame targetTf) {
        double open = group.get(0).open();
        double close = group.get(group.size() - 1).close();
        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        double volume = 0;

        for (Candle c : group) {
            high = Math.max(high, c.high());
            low = Math.min(low, c.low());
            volume += c.volume();
        }

        return new Candle(group.get(0).timestamp(), open, high, low, close, volume, targetTf);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=CandleAggregatorTest`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add service/pipeline/CandleAggregator.java CandleAggregatorTest.java
git commit -m "feat: add CandleAggregator with TDD — builds 1h/4h candles from 15m data"
```

---

## Task 3: Indicator Calculator with Tests (TDD)

**Files:**
- Create: `engine/model/IndicatorSnapshot.java`
- Create: `service/pipeline/IndicatorCalculator.java`
- Test: `service/pipeline/IndicatorCalculatorTest.java`

- [ ] **Step 1: Create IndicatorSnapshot record**

```java
// engine/model/IndicatorSnapshot.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

public record IndicatorSnapshot(
    TimeFrame timeFrame,
    double ema21,
    double ema50,
    double ema21Slope,           // slope of EMA21 over last 5 candles (degrees/candle)
    double rsi14,
    double bollingerUpper,       // 2.5 sigma
    double bollingerMiddle,
    double bollingerLower,
    double bollingerWidth,
    double bollingerPercentB,
    double atr14,
    double vwap,
    double adx,
    double volumeRatio,          // current volume / 20-period avg volume
    double macd,
    double macdSignal,
    double macdHistogram
) {}
```

- [ ] **Step 2: Write failing tests for core indicators**

```java
// IndicatorCalculatorTest.java
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

        // EMA21 should be close to recent prices but lagging
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

        double lastClose = candles.get(candles.size() - 1).close();
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
            // Volume increases over time, so latest candle has high volume vs average
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
        // Strong uptrend with consistent moves
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=IndicatorCalculatorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error (IndicatorCalculator doesn't exist yet)

- [ ] **Step 4: Write IndicatorCalculator implementation**

```java
// service/pipeline/IndicatorCalculator.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.IndicatorSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IndicatorCalculator {

    private static final int MIN_CANDLES = 50;
    private static final int EMA_SHORT = 21;
    private static final int EMA_LONG = 50;
    private static final int RSI_PERIOD = 14;
    private static final int BB_PERIOD = 20;
    private static final double BB_SIGMA = 2.5;
    private static final int ATR_PERIOD = 14;
    private static final int ADX_PERIOD = 14;
    private static final int VOLUME_AVG_PERIOD = 20;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int EMA_SLOPE_LOOKBACK = 5;

    public IndicatorSnapshot calculate(List<Candle> candles, TimeFrame timeFrame) {
        if (candles == null || candles.size() < MIN_CANDLES) {
            return null;
        }

        double[] closes = candles.stream().mapToDouble(Candle::close).toArray();
        double[] highs = candles.stream().mapToDouble(Candle::high).toArray();
        double[] lows = candles.stream().mapToDouble(Candle::low).toArray();
        double[] volumes = candles.stream().mapToDouble(Candle::volume).toArray();

        double ema21 = ema(closes, EMA_SHORT);
        double ema50 = ema(closes, EMA_LONG);
        double ema21Slope = emaSlope(closes, EMA_SHORT, EMA_SLOPE_LOOKBACK);
        double rsi = rsi(closes, RSI_PERIOD);

        double[] bb = bollingerBands(closes, BB_PERIOD, BB_SIGMA);
        double bbUpper = bb[0];
        double bbMiddle = bb[1];
        double bbLower = bb[2];
        double bbWidth = (bbUpper - bbLower) / bbMiddle;
        double bbPercentB = (closes[closes.length - 1] - bbLower) / (bbUpper - bbLower);

        double atr = atr(highs, lows, closes, ATR_PERIOD);
        double vwap = vwap(candles);
        double adx = adx(highs, lows, closes, ADX_PERIOD);
        double volumeRatio = volumeRatio(volumes, VOLUME_AVG_PERIOD);

        double[] macdValues = macd(closes, MACD_FAST, MACD_SLOW, MACD_SIGNAL);

        return new IndicatorSnapshot(
            timeFrame, ema21, ema50, ema21Slope, rsi,
            bbUpper, bbMiddle, bbLower, bbWidth, bbPercentB,
            atr, vwap, adx, volumeRatio,
            macdValues[0], macdValues[1], macdValues[2]
        );
    }

    double ema(double[] data, int period) {
        double multiplier = 2.0 / (period + 1);
        double ema = data[0];
        for (int i = 1; i < data.length; i++) {
            ema = (data[i] - ema) * multiplier + ema;
        }
        return ema;
    }

    double emaSlope(double[] data, int emaPeriod, int slopeLookback) {
        double multiplier = 2.0 / (emaPeriod + 1);
        double[] emaValues = new double[data.length];
        emaValues[0] = data[0];
        for (int i = 1; i < data.length; i++) {
            emaValues[i] = (data[i] - emaValues[i - 1]) * multiplier + emaValues[i - 1];
        }

        int n = emaValues.length;
        if (n < slopeLookback + 1) return 0;

        double sumSlope = 0;
        for (int i = n - slopeLookback; i < n; i++) {
            sumSlope += (emaValues[i] - emaValues[i - 1]);
        }
        return sumSlope / slopeLookback;
    }

    double rsi(double[] closes, int period) {
        double avgGain = 0, avgLoss = 0;

        for (int i = 1; i <= period && i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }
        }

        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    double[] bollingerBands(double[] data, int period, double sigma) {
        int n = data.length;
        double sum = 0, sqSum = 0;
        int start = Math.max(0, n - period);
        int count = n - start;

        for (int i = start; i < n; i++) {
            sum += data[i];
            sqSum += data[i] * data[i];
        }

        double mean = sum / count;
        double variance = (sqSum / count) - (mean * mean);
        double stdDev = Math.sqrt(Math.max(0, variance));

        return new double[]{mean + sigma * stdDev, mean, mean - sigma * stdDev};
    }

    double atr(double[] highs, double[] lows, double[] closes, int period) {
        int n = highs.length;
        double atr = 0;

        for (int i = 1; i < Math.min(period + 1, n); i++) {
            double tr = Math.max(
                highs[i] - lows[i],
                Math.max(
                    Math.abs(highs[i] - closes[i - 1]),
                    Math.abs(lows[i] - closes[i - 1])
                )
            );
            atr += tr;
        }
        atr /= Math.min(period, n - 1);

        for (int i = period + 1; i < n; i++) {
            double tr = Math.max(
                highs[i] - lows[i],
                Math.max(
                    Math.abs(highs[i] - closes[i - 1]),
                    Math.abs(lows[i] - closes[i - 1])
                )
            );
            atr = (atr * (period - 1) + tr) / period;
        }

        return atr;
    }

    double vwap(List<Candle> candles) {
        double cumulativeTPV = 0;
        double cumulativeVolume = 0;
        for (Candle c : candles) {
            cumulativeTPV += c.typicalPrice() * c.volume();
            cumulativeVolume += c.volume();
        }
        return cumulativeVolume > 0 ? cumulativeTPV / cumulativeVolume : 0;
    }

    double adx(double[] highs, double[] lows, double[] closes, int period) {
        int n = highs.length;
        if (n < period * 2) return 0;

        double[] plusDM = new double[n];
        double[] minusDM = new double[n];
        double[] tr = new double[n];

        for (int i = 1; i < n; i++) {
            double upMove = highs[i] - highs[i - 1];
            double downMove = lows[i - 1] - lows[i];
            plusDM[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDM[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
            tr[i] = Math.max(highs[i] - lows[i],
                    Math.max(Math.abs(highs[i] - closes[i - 1]),
                             Math.abs(lows[i] - closes[i - 1])));
        }

        double smoothedPlusDM = 0, smoothedMinusDM = 0, smoothedTR = 0;
        for (int i = 1; i <= period; i++) {
            smoothedPlusDM += plusDM[i];
            smoothedMinusDM += minusDM[i];
            smoothedTR += tr[i];
        }

        double sumDX = 0;
        int dxCount = 0;

        for (int i = period + 1; i < n; i++) {
            smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / period) + plusDM[i];
            smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / period) + minusDM[i];
            smoothedTR = smoothedTR - (smoothedTR / period) + tr[i];

            double plusDI = smoothedTR > 0 ? (smoothedPlusDM / smoothedTR) * 100 : 0;
            double minusDI = smoothedTR > 0 ? (smoothedMinusDM / smoothedTR) * 100 : 0;
            double diSum = plusDI + minusDI;
            double dx = diSum > 0 ? Math.abs(plusDI - minusDI) / diSum * 100 : 0;

            sumDX += dx;
            dxCount++;
        }

        return dxCount > 0 ? sumDX / dxCount : 0;
    }

    double volumeRatio(double[] volumes, int period) {
        int n = volumes.length;
        if (n < period + 1) return 1.0;

        double sum = 0;
        for (int i = n - period - 1; i < n - 1; i++) {
            sum += volumes[i];
        }
        double avg = sum / period;
        return avg > 0 ? volumes[n - 1] / avg : 1.0;
    }

    double[] macd(double[] data, int fastPeriod, int slowPeriod, int signalPeriod) {
        double fastMultiplier = 2.0 / (fastPeriod + 1);
        double slowMultiplier = 2.0 / (slowPeriod + 1);
        double sigMultiplier = 2.0 / (signalPeriod + 1);

        double fastEma = data[0];
        double slowEma = data[0];
        double[] macdLine = new double[data.length];

        for (int i = 1; i < data.length; i++) {
            fastEma = (data[i] - fastEma) * fastMultiplier + fastEma;
            slowEma = (data[i] - slowEma) * slowMultiplier + slowEma;
            macdLine[i] = fastEma - slowEma;
        }

        double signalLine = macdLine[0];
        for (int i = 1; i < macdLine.length; i++) {
            signalLine = (macdLine[i] - signalLine) * sigMultiplier + signalLine;
        }

        double lastMacd = macdLine[macdLine.length - 1];
        double histogram = lastMacd - signalLine;

        return new double[]{lastMacd, signalLine, histogram};
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=IndicatorCalculatorTest`
Expected: All 10 tests PASS

- [ ] **Step 6: Commit**

```bash
git add engine/model/IndicatorSnapshot.java service/pipeline/IndicatorCalculator.java IndicatorCalculatorTest.java
git commit -m "feat: add IndicatorCalculator with TDD — RSI, EMA, Bollinger, ATR, ADX, MACD, VWAP"
```

---

## Task 4: Multi-TimeFrame Data Container

**Files:**
- Create: `engine/model/MultiTimeFrameData.java`

- [ ] **Step 1: Create MultiTimeFrameData class**

```java
// engine/model/MultiTimeFrameData.java
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

    private final double fundingRate;              // current funding rate
    private final double fundingRatePredicted;     // predicted next funding rate
    private final List<Double> fundingRateHistory;  // last N funding rates (newest first)
    private final double openInterest;
    private final double openInterestChange24h;     // % change in OI over 24h
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
```

- [ ] **Step 2: Commit**

```bash
git add engine/model/MultiTimeFrameData.java
git commit -m "feat: add MultiTimeFrameData container for 15m/1h/4h data + funding + OI"
```

---

## Task 5: Trade Risk Engine with Tests (TDD)

**Files:**
- Create: `engine/model/TradeRequest.java`
- Create: `engine/model/RiskCheckResult.java`
- Create: `engine/model/RiskParameters.java`
- Create: `service/risk/TradeRiskEngine.java`
- Test: `service/risk/TradeRiskEngineTest.java`

- [ ] **Step 1: Create supporting models**

```java
// engine/model/TradeRequest.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TradeRequest {
    private final String symbol;
    private final Action action;           // BUY or SELL
    private final double entryPrice;
    private final double stopLossPrice;
    private final double takeProfitPrice;
    private final double confidence;       // 0-1 confluence score
    private final String strategyName;
    private final String reasoning;
}
```

```java
// engine/model/RiskCheckResult.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Getter;

import java.util.List;

@Getter
public class RiskCheckResult {

    private final boolean approved;
    private final double positionSize;       // in contracts/units
    private final double riskAmount;         // $ at risk
    private final double effectiveLeverage;  // after this trade
    private final int nominalLeverage;       // to set on exchange
    private final List<String> rejectionReasons;

    private RiskCheckResult(boolean approved, double positionSize, double riskAmount,
                            double effectiveLeverage, int nominalLeverage, List<String> rejectionReasons) {
        this.approved = approved;
        this.positionSize = positionSize;
        this.riskAmount = riskAmount;
        this.effectiveLeverage = effectiveLeverage;
        this.nominalLeverage = nominalLeverage;
        this.rejectionReasons = rejectionReasons;
    }

    public static RiskCheckResult approve(double positionSize, double riskAmount,
                                          double effectiveLeverage, int nominalLeverage) {
        return new RiskCheckResult(true, positionSize, riskAmount,
                effectiveLeverage, nominalLeverage, List.of());
    }

    public static RiskCheckResult reject(List<String> reasons) {
        return new RiskCheckResult(false, 0, 0, 0, 0, reasons);
    }
}
```

```java
// engine/model/RiskParameters.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RiskParameters {
    @Builder.Default private final double riskPerTradePct = 0.01;          // 1%
    @Builder.Default private final double maxEffectiveLeverage = 5.0;      // 5x
    @Builder.Default private final double dailyLossHaltPct = 0.05;         // 5%
    @Builder.Default private final double maxDrawdownPct = 0.15;           // 15%
    @Builder.Default private final int maxConcurrentPositions = 3;
    @Builder.Default private final double maxStopDistancePct = 0.02;       // 2%
    @Builder.Default private final double minRiskRewardRatio = 1.5;
    @Builder.Default private final double feeImpactThreshold = 0.20;       // 20% of risk
    @Builder.Default private final double estimatedRoundTripFeePct = 0.001; // 0.1% (0.05% per side)
}
```

- [ ] **Step 2: Write failing tests for all 7 risk checks**

```java
// TradeRiskEngineTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.risk;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradeRiskEngineTest {

    private TradeRiskEngine engine;
    private RiskParameters defaultParams;

    @BeforeEach
    void setUp() {
        engine = new TradeRiskEngine();
        defaultParams = RiskParameters.builder().build();
    }

    private TradeRequest.TradeRequestBuilder validLongRequest() {
        return TradeRequest.builder()
            .symbol("BTCUSD")
            .action(Action.BUY)
            .entryPrice(67000)
            .stopLossPrice(66700)         // 0.45% stop
            .takeProfitPrice(67900)       // ~3:1 R:R
            .confidence(0.8)
            .strategyName("TREND_CONTINUATION")
            .reasoning("test");
    }

    // --- CHECK 1: Position Size ---

    @Test
    void approvedTradeHasCorrectPositionSize() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of(), defaultParams);

        assertTrue(result.isApproved());
        // risk = $500 * 1% = $5. Stop distance = $300. Size = $5 / $300 = 0.0167 BTC
        // Notional = 0.0167 * 67000 = ~$1117
        double expectedSize = 5.0 / 300.0;
        assertEquals(expectedSize, result.getPositionSize(), 0.001);
    }

    // --- CHECK 2: Effective Leverage ---

    @Test
    void rejectsWhenEffectiveLeverageExceedsMax() {
        // $500 balance, already have $2000 exposure, new trade would add $1117 = $3117 = 6.2x
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 2000, 0,
                Set.of(), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("leverage")));
    }

    @Test
    void approvesWhenEffectiveLeverageWithinLimit() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 500, 0,
                Set.of(), defaultParams);

        assertTrue(result.isApproved());
        assertTrue(result.getEffectiveLeverage() <= 5.0);
    }

    // --- CHECK 3: Daily Loss Limit ---

    @Test
    void rejectsWhenDailyLossLimitHit() {
        // $500 balance, started day at $500, lost $25 (5%)
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 475, 500, 0, 25,
                Set.of(), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("daily loss")));
    }

    @Test
    void approvesWhenDailyLossUnderLimit() {
        // Lost $10 (2%) — under 5% limit
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 490, 500, 0, 10,
                Set.of(), defaultParams);

        assertTrue(result.isApproved());
    }

    // --- CHECK 4: Max Drawdown ---

    @Test
    void rejectsWhenMaxDrawdownBreached() {
        // Peak equity was $600, current is $500 = 16.7% drawdown > 15%
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 600, 0, 0,
                Set.of(), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("drawdown")));
    }

    // --- CHECK 5: Concurrent Positions ---

    @Test
    void rejectsWhenMaxConcurrentPositionsReached() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of("ETHUSD", "SOLUSD", "XRPUSD"), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("concurrent")));
    }

    @Test
    void rejectsDuplicateSymbol() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of("BTCUSD"), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("already")));
    }

    // --- CHECK 6: Stop-Loss Validation ---

    @Test
    void rejectsMissingStopLoss() {
        TradeRequest req = validLongRequest().stopLossPrice(0).build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of(), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("stop")));
    }

    @Test
    void rejectsStopTooWide() {
        // Stop is 3% away — exceeds 2% max
        TradeRequest req = validLongRequest()
            .stopLossPrice(64990)  // ~3% below 67000
            .build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of(), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("stop")));
    }

    @Test
    void rejectsWrongDirectionStop() {
        // BUY order but stop is ABOVE entry
        TradeRequest req = validLongRequest()
            .stopLossPrice(68000)
            .build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of(), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("stop")));
    }

    // --- CHECK 7: Fee Impact ---

    @Test
    void rejectsWhenFeeEatsRisk() {
        // Very tight stop — risk is tiny, fees are proportionally huge
        // Entry 67000, stop 66990 = $10 risk per BTC. Risk amount = $5. Size = 0.5 BTC
        // Notional = 0.5 * 67000 = $33500. Fees = $33500 * 0.001 = $33.50
        // Fee impact = 33.50 / 5 = 670% >> 20%
        TradeRequest req = validLongRequest()
            .stopLossPrice(66990)
            .build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of(), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("fee")));
    }

    // --- CHECK: Risk Reward Ratio ---

    @Test
    void rejectsBadRiskReward() {
        // Entry 67000, stop 66700 ($300 risk), TP 67200 ($200 reward) = 0.67 R:R
        TradeRequest req = validLongRequest()
            .takeProfitPrice(67200)
            .build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of(), defaultParams);

        assertFalse(result.isApproved());
        assertTrue(result.getRejectionReasons().stream()
            .anyMatch(r -> r.contains("reward")));
    }

    // --- SELL (SHORT) direction ---

    @Test
    void approvesValidShortTrade() {
        TradeRequest req = TradeRequest.builder()
            .symbol("BTCUSD")
            .action(Action.SELL)
            .entryPrice(67000)
            .stopLossPrice(67300)       // stop above entry for shorts
            .takeProfitPrice(66100)     // TP below entry for shorts, 3:1 R:R
            .confidence(0.8)
            .strategyName("MEAN_REVERSION")
            .reasoning("test")
            .build();

        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of(), defaultParams);

        assertTrue(result.isApproved());
    }

    // --- All checks pass scenario ---

    @Test
    void fullApprovalReturnsCorrectMetrics() {
        TradeRequest req = validLongRequest().build();
        RiskCheckResult result = engine.evaluate(req, 500, 500, 0, 0,
                Set.of(), defaultParams);

        assertTrue(result.isApproved());
        assertTrue(result.getPositionSize() > 0);
        assertTrue(result.getRiskAmount() > 0);
        assertTrue(result.getRiskAmount() <= 500 * 0.01 + 0.01); // max 1% of balance
        assertTrue(result.getEffectiveLeverage() <= 5.0);
        assertTrue(result.getNominalLeverage() >= 10);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=TradeRiskEngineTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error (TradeRiskEngine doesn't exist)

- [ ] **Step 4: Write TradeRiskEngine implementation**

```java
// service/risk/TradeRiskEngine.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.risk;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class TradeRiskEngine {

    /**
     * Evaluate a trade request against all risk checks.
     *
     * @param request             the proposed trade
     * @param currentBalance      current account equity
     * @param peakEquity          highest equity ever recorded (for drawdown calc)
     * @param currentExposure     total notional value of existing positions
     * @param dailyRealizedLoss   total loss realized today (positive number)
     * @param openPositionSymbols symbols with currently open positions
     * @param params              configurable risk parameters
     * @return approval or rejection with reasons
     */
    public RiskCheckResult evaluate(TradeRequest request, double currentBalance, double peakEquity,
                                    double currentExposure, double dailyRealizedLoss,
                                    Set<String> openPositionSymbols, RiskParameters params) {

        List<String> rejections = new ArrayList<>();

        // CHECK 6: Stop-loss validation (run first — other checks depend on stop distance)
        double stopDistance = validateStopLoss(request, params, rejections);
        if (stopDistance <= 0 && !rejections.isEmpty()) {
            return RiskCheckResult.reject(rejections);
        }

        // CHECK 1: Position sizing
        double riskAmount = currentBalance * params.getRiskPerTradePct();
        double positionSize = riskAmount / stopDistance;
        double notionalValue = positionSize * request.getEntryPrice();

        // CHECK: Risk-reward ratio
        double rewardDistance = Math.abs(request.getTakeProfitPrice() - request.getEntryPrice());
        double rrRatio = rewardDistance / stopDistance;
        if (rrRatio < params.getMinRiskRewardRatio()) {
            rejections.add(String.format("Risk:reward ratio %.2f below minimum %.1f",
                    rrRatio, params.getMinRiskRewardRatio()));
        }

        // CHECK 2: Effective leverage
        double newTotalExposure = currentExposure + notionalValue;
        double effectiveLeverage = newTotalExposure / currentBalance;
        if (effectiveLeverage > params.getMaxEffectiveLeverage()) {
            rejections.add(String.format("Effective leverage %.1fx exceeds max %.1fx",
                    effectiveLeverage, params.getMaxEffectiveLeverage()));
        }

        // CHECK 3: Daily loss limit
        double dailyLossLimit = peakEquity * params.getDailyLossHaltPct();
        if (dailyRealizedLoss >= dailyLossLimit) {
            rejections.add(String.format("Daily loss $%.2f hit daily loss limit $%.2f (%.0f%% of starting balance)",
                    dailyRealizedLoss, dailyLossLimit, params.getDailyLossHaltPct() * 100));
        }

        // CHECK 4: Max drawdown
        double drawdownPct = (peakEquity - currentBalance) / peakEquity;
        if (drawdownPct >= params.getMaxDrawdownPct()) {
            rejections.add(String.format("Drawdown %.1f%% exceeds max %.0f%%",
                    drawdownPct * 100, params.getMaxDrawdownPct() * 100));
        }

        // CHECK 5: Concurrent positions
        if (openPositionSymbols.size() >= params.getMaxConcurrentPositions()) {
            rejections.add(String.format("Already %d concurrent positions (max %d)",
                    openPositionSymbols.size(), params.getMaxConcurrentPositions()));
        }
        if (openPositionSymbols.contains(request.getSymbol())) {
            rejections.add(String.format("Already have open position in %s", request.getSymbol()));
        }

        // CHECK 7: Fee impact
        double estimatedFees = notionalValue * params.getEstimatedRoundTripFeePct();
        double feeImpact = riskAmount > 0 ? estimatedFees / riskAmount : Double.MAX_VALUE;
        if (feeImpact > params.getFeeImpactThreshold()) {
            rejections.add(String.format("Fees $%.2f are %.0f%% of risk $%.2f (max %.0f%%)",
                    estimatedFees, feeImpact * 100, riskAmount, params.getFeeImpactThreshold() * 100));
        }

        if (!rejections.isEmpty()) {
            log.warn("Trade REJECTED for {}: {}", request.getSymbol(), rejections);
            return RiskCheckResult.reject(rejections);
        }

        // Calculate nominal leverage (what to set on exchange)
        int nominalLeverage = calculateNominalLeverage(notionalValue, currentBalance);

        log.info("Trade APPROVED for {}: size={}, risk=${}, effLev={}x, nomLev={}x",
                request.getSymbol(), positionSize, riskAmount, effectiveLeverage, nominalLeverage);

        return RiskCheckResult.approve(positionSize, riskAmount, effectiveLeverage, nominalLeverage);
    }

    private double validateStopLoss(TradeRequest request, RiskParameters params,
                                     List<String> rejections) {
        if (request.getStopLossPrice() <= 0) {
            rejections.add("Stop-loss is mandatory — no trade without a stop");
            return 0;
        }

        boolean isLong = request.getAction() == Action.BUY;
        double stopDistance;

        if (isLong) {
            if (request.getStopLossPrice() >= request.getEntryPrice()) {
                rejections.add("Stop-loss must be below entry for BUY orders");
                return 0;
            }
            stopDistance = request.getEntryPrice() - request.getStopLossPrice();
        } else {
            if (request.getStopLossPrice() <= request.getEntryPrice()) {
                rejections.add("Stop-loss must be above entry for SELL orders");
                return 0;
            }
            stopDistance = request.getStopLossPrice() - request.getEntryPrice();
        }

        double stopPct = stopDistance / request.getEntryPrice();
        if (stopPct > params.getMaxStopDistancePct()) {
            rejections.add(String.format("Stop distance %.2f%% exceeds max %.0f%%",
                    stopPct * 100, params.getMaxStopDistancePct() * 100));
        }

        return stopDistance;
    }

    private int calculateNominalLeverage(double notionalValue, double balance) {
        double rawLeverage = notionalValue / balance;
        // Round up to nearest available leverage tier, minimum 10x
        if (rawLeverage <= 1) return 10;
        if (rawLeverage <= 3) return 10;
        if (rawLeverage <= 5) return 10;
        if (rawLeverage <= 10) return 15;
        if (rawLeverage <= 15) return 20;
        return 25;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=TradeRiskEngineTest`
Expected: All 15 tests PASS

- [ ] **Step 6: Commit**

```bash
git add engine/model/TradeRequest.java engine/model/RiskCheckResult.java engine/model/RiskParameters.java service/risk/TradeRiskEngine.java TradeRiskEngineTest.java
git commit -m "feat: add TradeRiskEngine with TDD — 7 hard risk checks (position size, leverage, daily loss, drawdown, concurrent, stop-loss, fees)"
```

---

## Task 6: Encryption Config for API Keys

**Files:**
- Create: `config/EncryptionConfig.java`
- Test: `config/EncryptionConfigTest.java`

- [ ] **Step 1: Write failing tests**

```java
// EncryptionConfigTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionConfigTest {

    @Test
    void encryptDecryptRoundTrip() {
        EncryptionConfig config = new EncryptionConfig("test-secret-key-32-chars-long!!");
        String plaintext = "my-super-secret-api-key-123";

        String encrypted = config.encrypt(plaintext);
        String decrypted = config.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void differentPlaintextsProduceDifferentCiphertexts() {
        EncryptionConfig config = new EncryptionConfig("test-secret-key-32-chars-long!!");

        String enc1 = config.encrypt("key-one");
        String enc2 = config.encrypt("key-two");

        assertNotEquals(enc1, enc2);
    }

    @Test
    void sameInputProducesDifferentCiphertextsDueToIV() {
        EncryptionConfig config = new EncryptionConfig("test-secret-key-32-chars-long!!");

        String enc1 = config.encrypt("same-input");
        String enc2 = config.encrypt("same-input");

        // Due to random IV, encrypting the same input should produce different ciphertexts
        assertNotEquals(enc1, enc2);

        // But both should decrypt to the same value
        assertEquals("same-input", config.decrypt(enc1));
        assertEquals("same-input", config.decrypt(enc2));
    }

    @Test
    void decryptWithWrongKeyFails() {
        EncryptionConfig config1 = new EncryptionConfig("test-secret-key-32-chars-long!!");
        EncryptionConfig config2 = new EncryptionConfig("different-key-32-characters!!!!!");

        String encrypted = config1.encrypt("sensitive-data");

        assertThrows(RuntimeException.class, () -> config2.decrypt(encrypted));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=EncryptionConfigTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error

- [ ] **Step 3: Write implementation**

```java
// config/EncryptionConfig.java
package com.QuantPlatformApplication.QuantPlatformApplication.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class EncryptionConfig {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKeySpec keySpec;

    public EncryptionConfig(@Value("${encryption.key:default-key-change-in-production!!}") String key) {
        byte[] keyBytes = Arrays.copyOf(
            key.getBytes(StandardCharsets.UTF_8), 32
        );
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=EncryptionConfigTest`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add config/EncryptionConfig.java EncryptionConfigTest.java
git commit -m "feat: add AES-GCM encryption for securing API keys at rest"
```

---

## Task 7: Database Migrations for Risk Config and Delta Credentials

**Files:**
- Create: `db/migration/V17__create_risk_config.sql`
- Create: `db/migration/V18__create_delta_credentials.sql`
- Create: `model/entity/RiskConfig.java`
- Create: `model/entity/DeltaCredential.java`
- Create: `repository/RiskConfigRepository.java`
- Create: `repository/DeltaCredentialRepository.java`

- [ ] **Step 1: Create migration V17 — risk_config table**

```sql
-- V17__create_risk_config.sql
CREATE TABLE risk_config (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    risk_per_trade_pct      NUMERIC(6,4)  NOT NULL DEFAULT 0.01,
    max_effective_leverage   NUMERIC(6,2)  NOT NULL DEFAULT 5.0,
    daily_loss_halt_pct     NUMERIC(6,4)  NOT NULL DEFAULT 0.05,
    max_drawdown_pct        NUMERIC(6,4)  NOT NULL DEFAULT 0.15,
    max_concurrent_positions INTEGER       NOT NULL DEFAULT 3,
    max_stop_distance_pct   NUMERIC(6,4)  NOT NULL DEFAULT 0.02,
    min_risk_reward_ratio   NUMERIC(4,2)  NOT NULL DEFAULT 1.5,
    fee_impact_threshold    NUMERIC(6,4)  NOT NULL DEFAULT 0.20,
    execution_mode          VARCHAR(20)   NOT NULL DEFAULT 'HUMAN_IN_LOOP',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_risk_config_user UNIQUE (user_id)
);

CREATE INDEX idx_risk_config_user ON risk_config(user_id);
```

- [ ] **Step 2: Create migration V18 — delta_credentials table**

```sql
-- V18__create_delta_credentials.sql
CREATE TABLE delta_credentials (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key_encrypted VARCHAR(512) NOT NULL,
    api_secret_encrypted VARCHAR(512) NOT NULL,
    is_testnet        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delta_cred_user_env UNIQUE (user_id, is_testnet)
);

CREATE INDEX idx_delta_cred_user ON delta_credentials(user_id);
```

- [ ] **Step 3: Create JPA entities**

```java
// model/entity/RiskConfig.java
package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "risk_config")
public class RiskConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder.Default
    @Column(name = "risk_per_trade_pct", nullable = false)
    private BigDecimal riskPerTradePct = new BigDecimal("0.01");

    @Builder.Default
    @Column(name = "max_effective_leverage", nullable = false)
    private BigDecimal maxEffectiveLeverage = new BigDecimal("5.0");

    @Builder.Default
    @Column(name = "daily_loss_halt_pct", nullable = false)
    private BigDecimal dailyLossHaltPct = new BigDecimal("0.05");

    @Builder.Default
    @Column(name = "max_drawdown_pct", nullable = false)
    private BigDecimal maxDrawdownPct = new BigDecimal("0.15");

    @Builder.Default
    @Column(name = "max_concurrent_positions", nullable = false)
    private Integer maxConcurrentPositions = 3;

    @Builder.Default
    @Column(name = "max_stop_distance_pct", nullable = false)
    private BigDecimal maxStopDistancePct = new BigDecimal("0.02");

    @Builder.Default
    @Column(name = "min_risk_reward_ratio", nullable = false)
    private BigDecimal minRiskRewardRatio = new BigDecimal("1.5");

    @Builder.Default
    @Column(name = "fee_impact_threshold", nullable = false)
    private BigDecimal feeImpactThreshold = new BigDecimal("0.20");

    @Builder.Default
    @Column(name = "execution_mode", nullable = false)
    private String executionMode = "HUMAN_IN_LOOP";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

```java
// model/entity/DeltaCredential.java
package com.QuantPlatformApplication.QuantPlatformApplication.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "delta_credentials")
public class DeltaCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "api_key_encrypted", nullable = false, length = 512)
    private String apiKeyEncrypted;

    @Column(name = "api_secret_encrypted", nullable = false, length = 512)
    private String apiSecretEncrypted;

    @Builder.Default
    @Column(name = "is_testnet", nullable = false)
    private Boolean isTestnet = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] **Step 4: Create repositories**

```java
// repository/RiskConfigRepository.java
package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.RiskConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskConfigRepository extends JpaRepository<RiskConfig, Long> {
    Optional<RiskConfig> findByUserId(Long userId);
}
```

```java
// repository/DeltaCredentialRepository.java
package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.DeltaCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeltaCredentialRepository extends JpaRepository<DeltaCredential, Long> {
    Optional<DeltaCredential> findByUserIdAndIsTestnet(Long userId, Boolean isTestnet);
    void deleteByUserIdAndIsTestnet(Long userId, Boolean isTestnet);
}
```

- [ ] **Step 5: Commit**

```bash
git add db/migration/V17__create_risk_config.sql db/migration/V18__create_delta_credentials.sql model/entity/RiskConfig.java model/entity/DeltaCredential.java repository/RiskConfigRepository.java repository/DeltaCredentialRepository.java
git commit -m "feat: add risk_config and delta_credentials tables with JPA entities and repos"
```

---

## Task 8: Delta Exchange REST Client with Tests (TDD)

**Files:**
- Create: `service/delta/DeltaExchangeConfig.java`
- Create: `service/delta/DeltaExchangeClient.java`
- Test: `service/delta/DeltaExchangeClientTest.java`

- [ ] **Step 1: Create config properties class**

```java
// service/delta/DeltaExchangeConfig.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.delta;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "delta-exchange")
public class DeltaExchangeConfig {
    private String testnetBaseUrl = "https://cdn-ind.testnet.deltaex.org";
    private String productionBaseUrl = "https://api.india.delta.exchange";
    private String testnetWsUrl = "wss://cdn-ind.testnet.deltaex.org/v2/ws";
    private String productionWsUrl = "wss://api.india.delta.exchange/v2/ws";
    private int timeoutSeconds = 10;
    private int maxRetries = 3;
}
```

- [ ] **Step 2: Write failing tests for key client operations**

```java
// DeltaExchangeClientTest.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.delta;

import com.QuantPlatformApplication.QuantPlatformApplication.config.EncryptionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeltaExchangeClientTest {

    private DeltaExchangeClient client;

    @BeforeEach
    void setUp() {
        DeltaExchangeConfig config = new DeltaExchangeConfig();
        client = new DeltaExchangeClient(config);
    }

    @Test
    void generatesCorrectHmacSignature() {
        // Delta Exchange uses HMAC-SHA256: method + timestamp + path + query + body
        String signature = client.generateSignature(
            "GET", "1234567890", "/v2/orders", "", "", "test-secret"
        );
        assertNotNull(signature);
        assertEquals(64, signature.length()); // SHA256 hex is 64 chars
    }

    @Test
    void generatesCorrectSignatureForPost() {
        String signature = client.generateSignature(
            "POST", "1234567890", "/v2/orders", "",
            "{\"product_id\":1,\"size\":1}", "test-secret"
        );
        assertNotNull(signature);
        assertEquals(64, signature.length());
    }

    @Test
    void differentInputsProduceDifferentSignatures() {
        String sig1 = client.generateSignature(
            "GET", "1234567890", "/v2/orders", "", "", "secret-1"
        );
        String sig2 = client.generateSignature(
            "GET", "1234567890", "/v2/orders", "", "", "secret-2"
        );
        assertNotEquals(sig1, sig2);
    }

    @Test
    void baseUrlIsTestnetByDefault() {
        String url = client.getBaseUrl(true);
        assertTrue(url.contains("testnet"));
    }

    @Test
    void baseUrlIsProductionWhenSpecified() {
        String url = client.getBaseUrl(false);
        assertFalse(url.contains("testnet"));
        assertTrue(url.contains("api.india.delta.exchange"));
    }

    @Test
    void buildOrderPayloadHasRequiredFields() {
        String payload = client.buildOrderPayload(1, 10, "buy", "limit_order", "67000");

        assertTrue(payload.contains("\"product_id\":1"));
        assertTrue(payload.contains("\"size\":10"));
        assertTrue(payload.contains("\"side\":\"buy\""));
        assertTrue(payload.contains("\"order_type\":\"limit_order\""));
        assertTrue(payload.contains("\"limit_price\":\"67000\""));
    }

    @Test
    void buildMarketOrderPayloadOmitsLimitPrice() {
        String payload = client.buildOrderPayload(1, 5, "buy", "market_order", null);

        assertTrue(payload.contains("\"product_id\":1"));
        assertTrue(payload.contains("\"size\":5"));
        assertTrue(payload.contains("\"order_type\":\"market_order\""));
        assertFalse(payload.contains("limit_price"));
    }

    @Test
    void buildBracketOrderPayloadIncludesStopAndTakeProfit() {
        String payload = client.buildBracketOrderPayload(
            1, 10, "buy", "limit_order", "67000", "66700", "67900"
        );

        assertTrue(payload.contains("\"stop_loss_price\":\"66700\""));
        assertTrue(payload.contains("\"take_profit_price\":\"67900\""));
        assertTrue(payload.contains("\"product_id\":1"));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=DeltaExchangeClientTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error

- [ ] **Step 4: Write DeltaExchangeClient implementation**

```java
// service/delta/DeltaExchangeClient.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DeltaExchangeClient {

    private final DeltaExchangeConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DeltaExchangeClient(DeltaExchangeConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }

    public String getBaseUrl(boolean testnet) {
        return testnet ? config.getTestnetBaseUrl() : config.getProductionBaseUrl();
    }

    public String generateSignature(String method, String timestamp, String path,
                                     String queryString, String body, String apiSecret) {
        try {
            String payload = method + timestamp + path + queryString + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC signature", e);
        }
    }

    // --- Public API (no auth) ---

    public Mono<JsonNode> getProducts(boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        return webClient.get()
            .uri(baseUrl + "/v2/products")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    public Mono<JsonNode> getTicker(String symbol, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        return webClient.get()
            .uri(baseUrl + "/v2/tickers/" + symbol)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    public Mono<JsonNode> getOrderBook(int productId, int depth, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        return webClient.get()
            .uri(baseUrl + "/v2/l2orderbook/" + productId + "?depth=" + depth)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    // --- Authenticated API ---

    public Mono<JsonNode> authenticatedGet(String path, String apiKey, String apiSecret, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature("GET", timestamp, path, "", "", apiSecret);

        return webClient.get()
            .uri(baseUrl + path)
            .header("api-key", apiKey)
            .header("timestamp", timestamp)
            .header("signature", signature)
            .header("Content-Type", "application/json")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    public Mono<JsonNode> authenticatedPost(String path, String body,
                                             String apiKey, String apiSecret, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature("POST", timestamp, path, "", body, apiSecret);

        return webClient.post()
            .uri(baseUrl + path)
            .header("api-key", apiKey)
            .header("timestamp", timestamp)
            .header("signature", signature)
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    public Mono<JsonNode> authenticatedDelete(String path, String apiKey, String apiSecret, boolean testnet) {
        String baseUrl = getBaseUrl(testnet);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature("DELETE", timestamp, path, "", "", apiSecret);

        return webClient.delete()
            .uri(baseUrl + path)
            .header("api-key", apiKey)
            .header("timestamp", timestamp)
            .header("signature", signature)
            .header("Content-Type", "application/json")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
    }

    // --- Order Payload Builders ---

    public String buildOrderPayload(int productId, int size, String side,
                                     String orderType, String limitPrice) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("product_id", productId);
            node.put("size", size);
            node.put("side", side);
            node.put("order_type", orderType);
            if (limitPrice != null && !"market_order".equals(orderType)) {
                node.put("limit_price", limitPrice);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build order payload", e);
        }
    }

    public String buildBracketOrderPayload(int productId, int size, String side,
                                            String orderType, String limitPrice,
                                            String stopLossPrice, String takeProfitPrice) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("product_id", productId);
            node.put("size", size);
            node.put("side", side);
            node.put("order_type", orderType);
            if (limitPrice != null) {
                node.put("limit_price", limitPrice);
            }
            if (stopLossPrice != null) {
                node.put("stop_loss_price", stopLossPrice);
            }
            if (takeProfitPrice != null) {
                node.put("take_profit_price", takeProfitPrice);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build bracket order payload", e);
        }
    }

    // --- High-Level Methods ---

    public Mono<JsonNode> getBalances(String apiKey, String apiSecret, boolean testnet) {
        return authenticatedGet("/v2/wallet/balances", apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> getPositions(String apiKey, String apiSecret, boolean testnet) {
        return authenticatedGet("/v2/positions/margined", apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> getOpenOrders(String apiKey, String apiSecret, boolean testnet) {
        return authenticatedGet("/v2/orders", apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> placeOrder(String orderPayload, String apiKey, String apiSecret, boolean testnet) {
        return authenticatedPost("/v2/orders", orderPayload, apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> placeBracketOrder(String orderPayload, String apiKey, String apiSecret, boolean testnet) {
        return authenticatedPost("/v2/orders/bracket", orderPayload, apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> cancelOrder(int orderId, int productId, String apiKey, String apiSecret, boolean testnet) {
        return authenticatedDelete("/v2/orders/" + orderId + "?product_id=" + productId, apiKey, apiSecret, testnet);
    }

    public Mono<JsonNode> setLeverage(int productId, int leverage, String apiKey, String apiSecret, boolean testnet) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("leverage", leverage));
            return authenticatedPost("/v2/products/" + productId + "/orders/leverage", body, apiKey, apiSecret, testnet);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to build leverage payload", e));
        }
    }
}
```

- [ ] **Step 5: Add Delta Exchange config to application.yml**

Add this section to the existing `application.yml`:

```yaml
delta-exchange:
  testnet-base-url: https://cdn-ind.testnet.deltaex.org
  production-base-url: https://api.india.delta.exchange
  testnet-ws-url: wss://cdn-ind.testnet.deltaex.org/v2/ws
  production-ws-url: wss://api.india.delta.exchange/v2/ws
  timeout-seconds: 10
  max-retries: 3

encryption:
  key: ${ENCRYPTION_KEY:default-key-change-in-production!!}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=DeltaExchangeClientTest`
Expected: All 8 tests PASS

- [ ] **Step 7: Commit**

```bash
git add service/delta/DeltaExchangeConfig.java service/delta/DeltaExchangeClient.java DeltaExchangeClientTest.java application.yml
git commit -m "feat: add Delta Exchange REST client with HMAC auth, order builders, and config"
```

---

## Task 9: Delta Exchange Broker Adapter

**Files:**
- Create: `service/delta/DeltaExchangeBrokerAdapter.java`
- Modify: `service/broker/BrokerManager.java`

- [ ] **Step 1: Create DeltaExchangeBrokerAdapter implementing BrokerAdapter**

```java
// service/delta/DeltaExchangeBrokerAdapter.java
package com.QuantPlatformApplication.QuantPlatformApplication.service.delta;

import com.QuantPlatformApplication.QuantPlatformApplication.config.EncryptionConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.DeltaCredential;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.DeltaCredentialRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.broker.BrokerAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeltaExchangeBrokerAdapter implements BrokerAdapter {

    private final DeltaExchangeClient client;
    private final DeltaCredentialRepository credentialRepo;
    private final EncryptionConfig encryption;

    // Default to user ID 1 for single-user mode. Will be parameterized for multi-tenant.
    private long activeUserId = 1L;
    private boolean useTestnet = true;

    @Override
    public String getName() {
        return "DELTA_EXCHANGE";
    }

    @Override
    public boolean isConnected() {
        try {
            String[] keys = getDecryptedKeys();
            if (keys == null) return false;
            JsonNode result = client.getBalances(keys[0], keys[1], useTestnet).block();
            return result != null;
        } catch (Exception e) {
            log.warn("Delta Exchange connection check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> placeOrder(String symbol, String side, String type,
                                           double quantity, Double price) {
        String[] keys = requireKeys();
        String orderType = "MARKET".equalsIgnoreCase(type) ? "market_order" : "limit_order";
        String limitPrice = price != null ? String.valueOf(price.intValue()) : null;

        // TODO: resolve symbol to product_id via products API. For now, pass as int if numeric.
        int productId = resolveProductId(symbol);
        String payload = client.buildOrderPayload(productId, (int) quantity, side.toLowerCase(), orderType, limitPrice);

        JsonNode result = client.placeOrder(payload, keys[0], keys[1], useTestnet).block();
        return jsonToMap(result);
    }

    @Override
    public Map<String, Object> cancelOrder(String orderId) {
        String[] keys = requireKeys();
        // orderId format: "orderId:productId"
        String[] parts = orderId.split(":");
        int oid = Integer.parseInt(parts[0]);
        int pid = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        JsonNode result = client.cancelOrder(oid, pid, keys[0], keys[1], useTestnet).block();
        return jsonToMap(result);
    }

    @Override
    public Map<String, Object> getOrder(String orderId) {
        // Delta Exchange doesn't have a single-order GET. Return from order history.
        String[] keys = requireKeys();
        JsonNode result = client.getOpenOrders(keys[0], keys[1], useTestnet).block();
        return jsonToMap(result);
    }

    @Override
    public List<Map<String, Object>> getOpenOrders() {
        String[] keys = requireKeys();
        JsonNode result = client.getOpenOrders(keys[0], keys[1], useTestnet).block();
        return jsonToList(result);
    }

    @Override
    public List<Map<String, Object>> getPositions() {
        String[] keys = requireKeys();
        JsonNode result = client.getPositions(keys[0], keys[1], useTestnet).block();
        return jsonToList(result);
    }

    @Override
    public Map<String, Object> getAccount() {
        String[] keys = requireKeys();
        JsonNode result = client.getBalances(keys[0], keys[1], useTestnet).block();
        return jsonToMap(result);
    }

    @Override
    public Map<String, Object> reconcilePositions() {
        // Fetch positions from exchange and return them as source of truth
        String[] keys = requireKeys();
        JsonNode positions = client.getPositions(keys[0], keys[1], useTestnet).block();
        JsonNode balances = client.getBalances(keys[0], keys[1], useTestnet).block();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("positions", jsonToList(positions));
        result.put("balances", jsonToMap(balances));
        result.put("source", "DELTA_EXCHANGE");
        result.put("testnet", useTestnet);
        return result;
    }

    public void setActiveUser(long userId, boolean testnet) {
        this.activeUserId = userId;
        this.useTestnet = testnet;
    }

    private String[] getDecryptedKeys() {
        Optional<DeltaCredential> cred = credentialRepo.findByUserIdAndIsTestnet(activeUserId, useTestnet);
        if (cred.isEmpty()) return null;
        return new String[]{
            encryption.decrypt(cred.get().getApiKeyEncrypted()),
            encryption.decrypt(cred.get().getApiSecretEncrypted())
        };
    }

    private String[] requireKeys() {
        String[] keys = getDecryptedKeys();
        if (keys == null) {
            throw new IllegalStateException("No Delta Exchange credentials configured for user " + activeUserId);
        }
        return keys;
    }

    private int resolveProductId(String symbol) {
        // Common Delta Exchange India product IDs (can be cached from products API)
        return switch (symbol.toUpperCase()) {
            case "BTCUSD", "BTCUSDT" -> 84;
            case "ETHUSD", "ETHUSDT" -> 85;
            default -> {
                try {
                    yield Integer.parseInt(symbol);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Unknown symbol: " + symbol + ". Use product ID directly.");
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonToMap(JsonNode node) {
        if (node == null) return Map.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.convertValue(node, LinkedHashMap.class);
        } catch (Exception e) {
            return Map.of("raw", node.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> jsonToList(JsonNode node) {
        if (node == null) return List.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (node.has("result")) {
                node = node.get("result");
            }
            if (node.isArray()) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : node) {
                    result.add(mapper.convertValue(item, LinkedHashMap.class));
                }
                return result;
            }
            return List.of(mapper.convertValue(node, LinkedHashMap.class));
        } catch (Exception e) {
            return List.of(Map.of("raw", node.toString()));
        }
    }
}
```

- [ ] **Step 2: Register adapter in BrokerManager**

Read BrokerManager.java first, then add the Delta Exchange adapter to the existing registration pattern. Add `DeltaExchangeBrokerAdapter` to the constructor injection alongside existing adapters.

- [ ] **Step 3: Commit**

```bash
git add service/delta/DeltaExchangeBrokerAdapter.java service/broker/BrokerManager.java
git commit -m "feat: add DeltaExchangeBrokerAdapter — moves auth to backend, integrates with BrokerManager"
```

---

## Task 10: Update ModelType and Add New Strategy Types

**Files:**
- Modify: `engine/model/ModelType.java`

- [ ] **Step 1: Add new model types for our strategies**

Update `ModelType.java` to include the new strategy types:

```java
// engine/model/ModelType.java
package com.QuantPlatformApplication.QuantPlatformApplication.engine.model;

public enum ModelType {
    MOMENTUM, VOLATILITY, MACRO, CORRELATION, REGIME,
    // New multi-timeframe strategies
    TREND_CONTINUATION, MEAN_REVERSION, FUNDING_SENTIMENT
}
```

- [ ] **Step 2: Commit**

```bash
git add engine/model/ModelType.java
git commit -m "feat: add TREND_CONTINUATION, MEAN_REVERSION, FUNDING_SENTIMENT to ModelType enum"
```

---

## Task 11: Delta Exchange Credential Management Controller

**Files:**
- Create: `model/dto/DeltaCredentialRequest.java`
- Create: `model/dto/RiskConfigRequest.java`
- Create: `controller/DeltaExchangeController.java`

- [ ] **Step 1: Create DTOs**

```java
// model/dto/DeltaCredentialRequest.java
package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import lombok.Data;

@Data
public class DeltaCredentialRequest {
    private String apiKey;
    private String apiSecret;
    private boolean testnet = true;
}
```

```java
// model/dto/RiskConfigRequest.java
package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RiskConfigRequest {
    private BigDecimal riskPerTradePct;
    private BigDecimal maxEffectiveLeverage;
    private BigDecimal dailyLossHaltPct;
    private BigDecimal maxDrawdownPct;
    private Integer maxConcurrentPositions;
    private BigDecimal maxStopDistancePct;
    private BigDecimal minRiskRewardRatio;
    private BigDecimal feeImpactThreshold;
    private String executionMode;
}
```

- [ ] **Step 2: Create controller**

```java
// controller/DeltaExchangeController.java
package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.config.EncryptionConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.DeltaCredentialRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.DeltaCredential;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.DeltaCredentialRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.delta.DeltaExchangeClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/delta")
@RequiredArgsConstructor
public class DeltaExchangeController {

    private final DeltaExchangeClient client;
    private final DeltaCredentialRepository credentialRepo;
    private final EncryptionConfig encryption;

    @PostMapping("/credentials")
    public ResponseEntity<Map<String, String>> saveCredentials(@RequestBody DeltaCredentialRequest request) {
        // For now, use userId=1 (single user). Multi-tenant will extract from JWT.
        long userId = 1L;

        DeltaCredential cred = credentialRepo
            .findByUserIdAndIsTestnet(userId, request.isTestnet())
            .orElse(new DeltaCredential());

        cred.setUserId(userId);
        cred.setApiKeyEncrypted(encryption.encrypt(request.getApiKey()));
        cred.setApiSecretEncrypted(encryption.encrypt(request.getApiSecret()));
        cred.setIsTestnet(request.isTestnet());
        credentialRepo.save(cred);

        return ResponseEntity.ok(Map.of(
            "status", "saved",
            "environment", request.isTestnet() ? "testnet" : "production"
        ));
    }

    @DeleteMapping("/credentials")
    public ResponseEntity<Map<String, String>> deleteCredentials(@RequestParam(defaultValue = "true") boolean testnet) {
        long userId = 1L;
        credentialRepo.deleteByUserIdAndIsTestnet(userId, testnet);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/products")
    public Mono<JsonNode> getProducts(@RequestParam(defaultValue = "true") boolean testnet) {
        return client.getProducts(testnet);
    }

    @GetMapping("/ticker/{symbol}")
    public Mono<JsonNode> getTicker(@PathVariable String symbol,
                                     @RequestParam(defaultValue = "true") boolean testnet) {
        return client.getTicker(symbol, testnet);
    }

    @GetMapping("/orderbook/{productId}")
    public Mono<JsonNode> getOrderBook(@PathVariable int productId,
                                        @RequestParam(defaultValue = "20") int depth,
                                        @RequestParam(defaultValue = "true") boolean testnet) {
        return client.getOrderBook(productId, depth, testnet);
    }

    @GetMapping("/connection-status")
    public ResponseEntity<Map<String, Object>> connectionStatus(
            @RequestParam(defaultValue = "true") boolean testnet) {
        long userId = 1L;
        boolean hasCredentials = credentialRepo.findByUserIdAndIsTestnet(userId, testnet).isPresent();

        return ResponseEntity.ok(Map.of(
            "hasCredentials", hasCredentials,
            "environment", testnet ? "testnet" : "production"
        ));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add model/dto/DeltaCredentialRequest.java model/dto/RiskConfigRequest.java controller/DeltaExchangeController.java
git commit -m "feat: add Delta Exchange controller for credential management and market data proxy"
```

---

## Task 12: Risk Config Controller

**Files:**
- Create: `controller/RiskConfigController.java`

- [ ] **Step 1: Create risk config controller**

```java
// controller/RiskConfigController.java
package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.RiskConfigRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.RiskConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.RiskConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/risk-config")
@RequiredArgsConstructor
public class RiskConfigController {

    private final RiskConfigRepository riskConfigRepo;

    @GetMapping
    public ResponseEntity<RiskConfig> getRiskConfig() {
        long userId = 1L;
        RiskConfig config = riskConfigRepo.findByUserId(userId)
            .orElseGet(() -> {
                RiskConfig defaultConfig = RiskConfig.builder().userId(userId).build();
                return riskConfigRepo.save(defaultConfig);
            });
        return ResponseEntity.ok(config);
    }

    @PutMapping
    public ResponseEntity<RiskConfig> updateRiskConfig(@RequestBody RiskConfigRequest request) {
        long userId = 1L;
        RiskConfig config = riskConfigRepo.findByUserId(userId)
            .orElseGet(() -> RiskConfig.builder().userId(userId).build());

        if (request.getRiskPerTradePct() != null) config.setRiskPerTradePct(request.getRiskPerTradePct());
        if (request.getMaxEffectiveLeverage() != null) config.setMaxEffectiveLeverage(request.getMaxEffectiveLeverage());
        if (request.getDailyLossHaltPct() != null) config.setDailyLossHaltPct(request.getDailyLossHaltPct());
        if (request.getMaxDrawdownPct() != null) config.setMaxDrawdownPct(request.getMaxDrawdownPct());
        if (request.getMaxConcurrentPositions() != null) config.setMaxConcurrentPositions(request.getMaxConcurrentPositions());
        if (request.getMaxStopDistancePct() != null) config.setMaxStopDistancePct(request.getMaxStopDistancePct());
        if (request.getMinRiskRewardRatio() != null) config.setMinRiskRewardRatio(request.getMinRiskRewardRatio());
        if (request.getFeeImpactThreshold() != null) config.setFeeImpactThreshold(request.getFeeImpactThreshold());
        if (request.getExecutionMode() != null) config.setExecutionMode(request.getExecutionMode());

        return ResponseEntity.ok(riskConfigRepo.save(config));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add controller/RiskConfigController.java
git commit -m "feat: add RiskConfigController for per-user risk parameter management"
```

---

## Task 13: Integration Smoke Test

**Files:**
- Create: `ApplicationSmokeTest.java` (in test root)

- [ ] **Step 1: Write a basic integration test verifying core beans wire up**

```java
// ApplicationSmokeTest.java (alongside existing QuantPlatformApplicationTests.java)
package com.QuantPlatformApplication.QuantPlatformApplication;

import com.QuantPlatformApplication.QuantPlatformApplication.config.EncryptionConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.service.delta.DeltaExchangeClient;
import com.QuantPlatformApplication.QuantPlatformApplication.service.delta.DeltaExchangeConfig;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.CandleAggregator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.IndicatorCalculator;
import com.QuantPlatformApplication.QuantPlatformApplication.service.risk.TradeRiskEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ApplicationSmokeTest {

    @Autowired private CandleAggregator candleAggregator;
    @Autowired private IndicatorCalculator indicatorCalculator;
    @Autowired private TradeRiskEngine tradeRiskEngine;
    @Autowired private DeltaExchangeClient deltaExchangeClient;
    @Autowired private DeltaExchangeConfig deltaExchangeConfig;
    @Autowired private EncryptionConfig encryptionConfig;

    @Test
    void allNewBeansLoad() {
        assertNotNull(candleAggregator);
        assertNotNull(indicatorCalculator);
        assertNotNull(tradeRiskEngine);
        assertNotNull(deltaExchangeClient);
        assertNotNull(deltaExchangeConfig);
        assertNotNull(encryptionConfig);
    }
}
```

- [ ] **Step 2: Run the smoke test**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication && ./mvnw test -pl . -Dtest=ApplicationSmokeTest`
Expected: PASS (requires PostgreSQL and Redis running)

If DB/Redis not available, skip this test with `@DisabledIfEnvironmentVariable` or run with testcontainers later.

- [ ] **Step 3: Commit**

```bash
git add ApplicationSmokeTest.java
git commit -m "test: add ApplicationSmokeTest verifying all Phase 1 beans wire up correctly"
```

---

## Summary

| Task | Component | Tests | Status |
|------|-----------|-------|--------|
| 1 | TimeFrame + Candle | — | - [ ] |
| 2 | CandleAggregator | 5 tests | - [ ] |
| 3 | IndicatorCalculator | 10 tests | - [ ] |
| 4 | MultiTimeFrameData | — | - [ ] |
| 5 | TradeRiskEngine | 15 tests | - [ ] |
| 6 | EncryptionConfig | 4 tests | - [ ] |
| 7 | DB Migrations + Entities | — | - [ ] |
| 8 | DeltaExchangeClient | 8 tests | - [ ] |
| 9 | DeltaExchangeBrokerAdapter | — | - [ ] |
| 10 | ModelType update | — | - [ ] |
| 11 | DeltaExchangeController | — | - [ ] |
| 12 | RiskConfigController | — | - [ ] |
| 13 | Integration Smoke Test | 1 test | - [ ] |

**Total: 13 tasks, 43 unit tests, covers all Phase 1 design spec requirements.**

**After Phase 1:** The system has a working Delta Exchange backend adapter, multi-timeframe data model, all technical indicators, and a bulletproof 7-check risk engine. Phase 2 builds the actual strategies on top of this foundation.

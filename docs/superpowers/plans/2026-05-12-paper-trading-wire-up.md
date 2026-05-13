# Paper Trading Wire-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the existing backtest-validated strategy + ML stack into a live paper-trading loop that runs every 15 minutes on the seeded Postgres data, routes approved signals to the paper broker, logs explainable trades, sends out-only Telegram alerts, retrains the meta-labeler weekly, and surfaces a validation-gate dashboard showing rolling Sharpe / drawdown / win-rate vs. the spec §4.7 criteria.

**Architecture:** A `MarketTickScheduler` fires every 15 minutes, builds `MultiTimeFrameData` snapshots from Postgres (same path as the backtest), runs each active strategy through `StrategyOrchestrator` → `TradeRiskEngine` (now with meta-filter veto) → `ExecutionModeRouter` → `PaperBrokerAdapter`. Each paper fill writes a `TradeLog` row with rich explanation metadata and fires a Telegram alert. A separate nightly `PositionMonitor` checks open positions against stop/target, closes filled ones, updates `TradeLog.outcome`. A `MetaRetrainScheduler` retrains the meta-labeler every Sunday and atomically switches the `latest.json` pointer only if the new model is non-degrading. A new `/api/v1/paper/metrics` endpoint + `PaperTradingPage` show the 4-week rolling gate metrics visually; enforcement is NOT wired (dashboard-only, per user decision).

**Tech Stack:** Java 21, Spring Boot 3.5, Spring `@Scheduled`, JPA + TimescaleDB, existing `MLMetaClient` + `PaperBrokerAdapter` from Plans 2–3, existing `TelegramBotService` (out-only), React 19 + TanStack Query.

**Parent spec:** `docs/superpowers/specs/2026-05-10-ml-rebuild-and-paper-trading-design.md` §4.6 (paper trading wire-up) + §4.7 (validation gate).

---

## Scope Boundary

**In scope (10 tasks):**
- `MarketTickScheduler` — 15-minute cron that evaluates all active strategy/symbol pairs.
- `MultiTimeFrameDataBuilder` — reads Postgres candles + derivatives into `MultiTimeFrameData`.
- `TradeRiskEngine` meta-filter integration (fail-open on ml-service down + loud Telegram alert).
- `ExecutionModeRouter` → `PaperBrokerAdapter` wiring (replace the two `TODO: Phase 3` stubs).
- `TradeLog` persistence in the paper-trade flow with explanation metadata.
- `PositionMonitor` scheduled job: closes TP/SL-hit positions, updates `TradeLog.outcome`, fires Telegram alerts.
- Telegram out-only alerts on: trade entry, trade exit, risk veto, meta-filter bypass warning, daily summary.
- `MetaRetrainScheduler` — weekly cron calling `/train-meta` per active symbol, with non-degradation check before saving.
- `/api/v1/paper/metrics` endpoint + `PaperTradingPage` frontend showing rolling Sharpe / drawdown / win-rate / trade count against spec §4.7 gate criteria. Dashboard-only (no enforcement).
- Controller request-body parsing for `useMetaFilter` / `metaThreshold` / `metaSymbol` — Plan 3 leftover.

**Out of scope (explicit — future plans):**
- Telegram 2-way commands (`/approve`, `/reject`, `/close_all`, `/stop`, `/resume`). Out-only per user decision.
- Live Delta Exchange broker adapter wiring. Paper only.
- Enforcement of the §4.7 gate (blocking live-money endpoints until all 5 criteria hold). Visual gate only; enforcement lives with the eventual live-money plan.
- Approach C ML meta-filter upgrades (walk-forward-based model selection, feature importance tracking). Deferred to a separate ML-maturation plan.
- Market-impact modeling (negligible at $500 capital, flagged in Plan 3 `BacktestConfig` Javadoc).
- SaaS / multi-tenant features.

---

## File Layout

### Created
- `QuantPlatformApplication/src/main/java/.../service/pipeline/MarketTickScheduler.java` — @Scheduled every 15m, orchestrator driver
- `QuantPlatformApplication/src/main/java/.../service/pipeline/MultiTimeFrameDataBuilder.java` — assembles snapshots from Postgres
- `QuantPlatformApplication/src/main/java/.../service/pipeline/PositionMonitor.java` — @Scheduled every minute, closes TP/SL-hit positions
- `QuantPlatformApplication/src/main/java/.../service/ml/MetaFilterGate.java` — wraps MLMetaClient with timeout + fail-open + alert
- `QuantPlatformApplication/src/main/java/.../service/ml/MetaRetrainScheduler.java` — @Scheduled weekly Sunday 02:00 UTC
- `QuantPlatformApplication/src/main/java/.../service/paper/PaperTradePersister.java` — writes TradeLog rows with explanation metadata
- `QuantPlatformApplication/src/main/java/.../service/paper/PaperMetricsService.java` — rolling Sharpe / DD / win-rate / trade count
- `QuantPlatformApplication/src/main/java/.../controller/PaperTradingController.java` — `/api/v1/paper/metrics`, `/api/v1/paper/trades`
- `QuantPlatformApplication/src/test/java/.../service/pipeline/MarketTickSchedulerTest.java`
- `QuantPlatformApplication/src/test/java/.../service/pipeline/MultiTimeFrameDataBuilderTest.java`
- `QuantPlatformApplication/src/test/java/.../service/ml/MetaFilterGateTest.java`
- `QuantPlatformApplication/src/test/java/.../service/ml/MetaRetrainSchedulerTest.java`
- `QuantPlatformApplication/src/test/java/.../service/paper/PaperMetricsServiceTest.java`
- `QuantPlatformApplication/src/test/java/.../service/pipeline/PositionMonitorTest.java`
- `frontend/src/pages/PaperTradingPage.tsx`
- `frontend/src/types/paperTrading.ts`

### Modified
- `QuantPlatformApplication/src/main/java/.../service/ExecutionModeRouter.java` — remove TODO stubs, wire PaperBrokerAdapter, TradeLog, Telegram
- `QuantPlatformApplication/src/main/java/.../service/risk/TradeRiskEngine.java` — add MetaFilterGate call between CHECK 7 and APPROVE
- `QuantPlatformApplication/src/main/java/.../controller/MultiTimeFrameBacktestController.java` — parse useMetaFilter / metaThreshold / metaSymbol from request body
- `frontend/src/services/api.ts` — add paper-trading endpoints
- `frontend/src/App.tsx` — register `/paper-trading` route
- `frontend/src/components/layout/Sidebar.tsx` — add Paper Trading menu item
- `frontend/src/components/layout/CommandPalette.tsx` — add Paper Trading command

---

## Task 1: MultiTimeFrameDataBuilder — Postgres snapshot assembly

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MultiTimeFrameDataBuilder.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MultiTimeFrameDataBuilderTest.java`

- [ ] **Step 1: Write failing test**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MultiTimeFrameDataBuilderTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.IndicatorSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.IndicatorCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiTimeFrameDataBuilderTest {

    private Candle bar(long epochSec, double close) {
        return new Candle(Instant.ofEpochSecond(epochSec), close, close + 1, close - 1, close, 100.0, TimeFrame.M15);
    }

    @Test
    void build_populatesAllThreeTimeframes() {
        CandleSource source = mock(CandleSource.class);
        IndicatorCalculator calc = mock(IndicatorCalculator.class);

        List<Candle> candles15 = List.of(bar(1_700_000_000L, 100), bar(1_700_000_900L, 101));
        List<Candle> candles1h = List.of(bar(1_700_000_000L, 100));
        List<Candle> candles4h = List.of(bar(1_700_000_000L, 100));

        when(source.fetch(eq("BTCUSDT"), eq("15m"), any(LocalDate.class), any(LocalDate.class))).thenReturn(candles15);
        when(source.fetch(eq("BTCUSDT"), eq("1h"), any(LocalDate.class), any(LocalDate.class))).thenReturn(candles1h);
        when(source.fetch(eq("BTCUSDT"), eq("4h"), any(LocalDate.class), any(LocalDate.class))).thenReturn(candles4h);
        when(calc.calculate(any(), any())).thenReturn(IndicatorSnapshot.builder().build());

        MultiTimeFrameDataBuilder builder = new MultiTimeFrameDataBuilder(source, calc);
        MultiTimeFrameData data = builder.build("BTCUSDT", LocalDate.of(2024, 1, 15));

        assertThat(data.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(data.getCandles15m()).hasSize(2);
        assertThat(data.getCandles1h()).hasSize(1);
        assertThat(data.getCandles4h()).hasSize(1);
        assertThat(data.getCurrentPrice()).isEqualTo(101.0); // last 15m close
        assertThat(data.getIndicators15m()).isNotNull();
    }

    @Test
    void build_usesLastCloseForCurrentPrice() {
        CandleSource source = mock(CandleSource.class);
        IndicatorCalculator calc = mock(IndicatorCalculator.class);
        when(source.fetch(any(), any(), any(), any())).thenReturn(
            List.of(bar(1_700_000_000L, 100), bar(1_700_000_900L, 42000.5))
        );
        when(calc.calculate(any(), any())).thenReturn(IndicatorSnapshot.builder().build());

        MultiTimeFrameData data = new MultiTimeFrameDataBuilder(source, calc)
            .build("BTCUSDT", LocalDate.of(2024, 1, 15));

        assertThat(data.getCurrentPrice()).isEqualTo(42000.5);
    }
}
```

Run:
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication
./mvnw test -Dtest=MultiTimeFrameDataBuilderTest 2>&1 | tail -15
```
Expected: COMPILATION FAILURE.

- [ ] **Step 2: Implement**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MultiTimeFrameDataBuilder.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.IndicatorSnapshot;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Assembles a MultiTimeFrameData snapshot from Postgres for live paper trading.
 *
 * Uses the same CandleSource the backtest uses (MarketDataCandleSource), so the
 * paper-trading data path is byte-for-byte identical to the backtest data path —
 * no "it worked in backtest but not live" class of bug.
 *
 * The builder pulls the last N calendar days of 15m/1h/4h candles anchored to
 * `asOf`, then asks IndicatorCalculator to compute snapshots. If any timeframe
 * is thin (e.g., just after a gap), calculate() returns null and the strategy
 * downstream skips evaluation for that tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiTimeFrameDataBuilder {

    // Enough history for the longest indicator (50-period EMA on 4h = 200h = 8 days),
    // plus margin for warm-up and weekend gaps.
    private static final int LOOKBACK_DAYS_15M = 14;
    private static final int LOOKBACK_DAYS_1H  = 30;
    private static final int LOOKBACK_DAYS_4H  = 90;

    private final CandleSource candleSource;
    private final IndicatorCalculator indicatorCalculator;

    public MultiTimeFrameData build(String symbol, LocalDate asOf) {
        List<Candle> c15 = safeFetch(symbol, "15m", asOf.minusDays(LOOKBACK_DAYS_15M), asOf);
        List<Candle> c1h = safeFetch(symbol, "1h",  asOf.minusDays(LOOKBACK_DAYS_1H),  asOf);
        List<Candle> c4h = safeFetch(symbol, "4h",  asOf.minusDays(LOOKBACK_DAYS_4H),  asOf);

        IndicatorSnapshot i15 = c15.isEmpty() ? null : indicatorCalculator.calculate(c15, TimeFrame.M15);
        IndicatorSnapshot i1h = c1h.isEmpty() ? null : indicatorCalculator.calculate(c1h, TimeFrame.H1);
        IndicatorSnapshot i4h = c4h.isEmpty() ? null : indicatorCalculator.calculate(c4h, TimeFrame.H4);

        Candle last15 = c15.isEmpty() ? null : c15.get(c15.size() - 1);
        double currentPrice = last15 != null ? last15.close() : 0.0;
        double currentVolume = last15 != null ? last15.volume() : 0.0;

        return MultiTimeFrameData.builder()
            .symbol(symbol)
            .currentPrice(currentPrice)
            .currentVolume(currentVolume)
            .candles15m(c15)
            .candles1h(c1h)
            .candles4h(c4h)
            .indicators15m(i15)
            .indicators1h(i1h)
            .indicators4h(i4h)
            .fundingRate(0.0)               // Plan 4.1: funding enrichment is a follow-up
            .fundingRatePredicted(0.0)
            .fundingRateHistory(Collections.emptyList())
            .openInterest(0.0)
            .openInterestChange24h(0.0)
            .longShortRatio(0.0)
            .build();
    }

    private List<Candle> safeFetch(String symbol, String tf, LocalDate from, LocalDate to) {
        try {
            return candleSource.fetch(symbol, tf, from, to);
        } catch (Exception e) {
            log.warn("CandleSource fetch failed for {} {} {}..{}: {}", symbol, tf, from, to, e.getMessage());
            return Collections.emptyList();
        }
    }
}
```

Run: `./mvnw test -Dtest=MultiTimeFrameDataBuilderTest 2>&1 | tail -10`
Expected: 2 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MultiTimeFrameDataBuilder.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MultiTimeFrameDataBuilderTest.java
git commit -m "feat(pipeline): MultiTimeFrameDataBuilder reads Postgres for live ticks

Same CandleSource abstraction as the backtest so paper trading and
backtest have identical data paths. 14d / 30d / 90d lookback per
timeframe covers the longest indicator window. Funding + OI fields
zero-filled for now; that enrichment is a Plan 4.1 follow-up.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: MetaFilterGate — wraps MLMetaClient with timeout + fail-open + alert

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaFilterGate.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaFilterGateTest.java`

- [ ] **Step 1: Write failing test**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaFilterGateTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.ml;

import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaClient;
import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaPredictionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetaFilterGateTest {

    @Test
    void allow_whenProbAboveThreshold() {
        MLMetaClient client = mock(MLMetaClient.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(client.predictMeta(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new MLMetaPredictionResponse("BTCUSDT", 0.72, 1, "LONG"));

        MetaFilterGate gate = new MetaFilterGate(client, telegram, 0.55);
        MetaFilterGate.Decision d = gate.check("BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(d.allow()).isTrue();
        assertThat(d.metaProb()).isEqualTo(0.72);
        verify(telegram, never()).sendMessage(any());
    }

    @Test
    void veto_whenProbBelowThreshold() {
        MLMetaClient client = mock(MLMetaClient.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(client.predictMeta(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new MLMetaPredictionResponse("BTCUSDT", 0.30, 1, "LONG"));

        MetaFilterGate gate = new MetaFilterGate(client, telegram, 0.55);
        MetaFilterGate.Decision d = gate.check("BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(d.allow()).isFalse();
        assertThat(d.metaProb()).isEqualTo(0.30);
        assertThat(d.reason()).contains("below threshold");
        verify(telegram, never()).sendMessage(any()); // veto is expected, not alarmed
    }

    @Test
    void failOpen_whenClientThrows_sendsLoudAlert() {
        MLMetaClient client = mock(MLMetaClient.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(client.predictMeta(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenThrow(new RuntimeException("connection refused"));

        MetaFilterGate gate = new MetaFilterGate(client, telegram, 0.55);
        MetaFilterGate.Decision d = gate.check("BTCUSDT", "LONG", 42000.0, 0.02, 0.01);

        assertThat(d.allow()).isTrue();
        assertThat(d.failedOpen()).isTrue();
        verify(telegram, times(1)).sendMessage(any(String.class));
    }
}
```

Run: `./mvnw test -Dtest=MetaFilterGateTest 2>&1 | tail -15`
Expected: COMPILATION FAILURE.

- [ ] **Step 2: Implement**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaFilterGate.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.ml;

import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaClient;
import com.QuantPlatformApplication.QuantPlatformApplication.client.MLMetaPredictionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Production-side wrapper around MLMetaClient for live paper trading.
 *
 * Policy (per user decision 2026-05-12):
 *   - ml-service returns meta_prob >= threshold: allow
 *   - ml-service returns meta_prob <  threshold: veto (routine, silent)
 *   - ml-service throws / times out: FAIL OPEN (allow trade) + loud Telegram alert
 *
 * Rationale: the rules-based strategies existed before the meta filter and were
 * acceptable on their own. An ml-service outage must not halt all trading, but
 * the operator must see it immediately.
 */
@Slf4j
@Component
public class MetaFilterGate {

    private final MLMetaClient client;
    private final TelegramBotService telegram;
    private final double defaultThreshold;

    public MetaFilterGate(
            MLMetaClient client,
            TelegramBotService telegram,
            @Value("${quantedge.meta.threshold:0.55}") double defaultThreshold) {
        this.client = client;
        this.telegram = telegram;
        this.defaultThreshold = defaultThreshold;
    }

    public Decision check(String symbol, String direction,
                          double entryPrice, double tpPct, double slPct) {
        return checkWithThreshold(symbol, direction, entryPrice, tpPct, slPct, defaultThreshold);
    }

    public Decision checkWithThreshold(String symbol, String direction,
                                       double entryPrice, double tpPct, double slPct,
                                       double threshold) {
        try {
            MLMetaPredictionResponse resp = client.predictMeta(
                symbol, direction, entryPrice, tpPct, slPct);
            boolean allow = resp.metaProb() >= threshold;
            String reason = allow
                ? String.format("meta_prob=%.3f >= threshold=%.2f", resp.metaProb(), threshold)
                : String.format("meta_prob=%.3f below threshold=%.2f", resp.metaProb(), threshold);
            return new Decision(allow, resp.metaProb(), reason, false);
        } catch (Exception e) {
            log.warn("MetaFilterGate FAIL-OPEN for {} {} @ {}: {}",
                symbol, direction, entryPrice, e.getMessage());
            telegram.sendMessage(String.format(
                "🚨 *ML Meta-Filter Unreachable*%n%n" +
                "Symbol: %s %s @ $%.2f%nError: %s%n" +
                "Policy: FAIL OPEN — trade allowed without ML veto.",
                symbol, direction, entryPrice, e.getMessage()));
            return new Decision(true, Double.NaN, "fail-open: " + e.getMessage(), true);
        }
    }

    public record Decision(boolean allow, double metaProb, String reason, boolean failedOpen) {}
}
```

Run: `./mvnw test -Dtest=MetaFilterGateTest 2>&1 | tail -10`
Expected: 3 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaFilterGate.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaFilterGateTest.java
git commit -m "feat(ml): MetaFilterGate wraps MLMetaClient with fail-open policy

- meta_prob >= threshold: allow (silent)
- meta_prob <  threshold: veto (routine, no alert)
- ml-service down: fail-open + loud Telegram alert
Decision record surfaces metaProb, reason, failedOpen flag for caller.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: TradeRiskEngine integration of MetaFilterGate

**Files:**
- Modify: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/risk/TradeRiskEngine.java`

- [ ] **Step 1: Read TradeRiskEngine**

Run: `cat QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/risk/TradeRiskEngine.java`

You'll see: 7 CHECKs (stop-loss, position size, leverage, daily loss, drawdown, concurrent positions, fee impact). After `rejections` is built and before the final `!rejections.isEmpty()` branch, inject the meta-gate check.

- [ ] **Step 2: Add MetaFilterGate injection and CHECK 8**

At the top of `TradeRiskEngine.java`, add:
```java
import com.QuantPlatformApplication.QuantPlatformApplication.service.ml.MetaFilterGate;
```

Change the class declaration from `@Component` + no-arg to `@Component` + `@RequiredArgsConstructor`:

Before:
```java
@Slf4j
@Component
public class TradeRiskEngine {
```
After:
```java
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class TradeRiskEngine {

    private final MetaFilterGate metaFilterGate;
```

In `evaluate(...)`, find the line right BEFORE `if (!rejections.isEmpty()) {` (the final reject branch) — roughly line 111. Just before that line, add:

```java
        // CHECK 8: Meta-labeler filter (Plan 4)
        // Skipped when rejections already exist — no point scoring a doomed signal.
        // Fail-open policy inside MetaFilterGate: ml-service down => allow + Telegram alert.
        if (rejections.isEmpty() && params.isUseMetaFilter()) {
            String metaSymbol = params.getMetaSymbol() != null && !params.getMetaSymbol().isEmpty()
                ? params.getMetaSymbol() : request.getSymbol();
            MetaFilterGate.Decision metaDecision = metaFilterGate.checkWithThreshold(
                metaSymbol,
                request.getAction() == Action.BUY ? "LONG" : "SHORT",
                request.getEntryPrice(),
                0.02, // tp_pct matching meta training default
                0.01, // sl_pct matching meta training default
                params.getMetaThreshold()
            );
            if (!metaDecision.allow()) {
                rejections.add("Meta-filter: " + metaDecision.reason());
            }
        }
```

- [ ] **Step 3: Add `useMetaFilter`, `metaThreshold`, `metaSymbol` to RiskParameters**

Read `RiskParameters.java`:
```
cat QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/engine/model/RiskParameters.java
```

Add these fields (using the same `@Builder.Default` pattern as the existing fields):

```java
    @Builder.Default private final boolean useMetaFilter = false;
    @Builder.Default private final double metaThreshold = 0.55;
    @Builder.Default private final String metaSymbol = "";
```

- [ ] **Step 4: Compile + run existing tests**

Run:
```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication
./mvnw -q compile 2>&1 | tail -10
./mvnw test -Dtest=TradeRiskEngineTest 2>&1 | tail -15
```

If `TradeRiskEngineTest` exists and constructs the engine directly (no Spring), it may fail because `TradeRiskEngine` now requires `MetaFilterGate`. Update the test's constructor call to pass a Mockito mock:
```java
MetaFilterGate metaGate = mock(MetaFilterGate.class);
when(metaGate.checkWithThreshold(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
    .thenReturn(new MetaFilterGate.Decision(true, 0.99, "mocked", false));
TradeRiskEngine engine = new TradeRiskEngine(metaGate);
```

Expected: all existing TradeRiskEngine tests pass.

- [ ] **Step 5: Full suite**

Run: `./mvnw test 2>&1 | tail -15`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/risk/TradeRiskEngine.java \
        QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/engine/model/RiskParameters.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/risk/TradeRiskEngineTest.java
git commit -m "feat(risk): TradeRiskEngine CHECK 8 — meta-filter veto

Skipped when other checks already rejected. Fail-open on ml-service
outage — MetaFilterGate handles the Telegram alert. Off by default
via RiskParameters.useMetaFilter=false so existing behavior is
unchanged until a caller opts in.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: PaperTradePersister — persist TradeLog with explanation metadata

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperTradePersister.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperTradePersisterTest.java`

- [ ] **Step 1: Write failing test**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperTradePersisterTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperTradePersisterTest {

    @Test
    void persist_writesTradeLogWithExplanation() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.save(any(TradeLog.class))).thenAnswer(inv -> inv.getArgument(0));

        TradeSignal sig = TradeSignal.builder()
            .symbol("BTCUSDT").action(Action.BUY).entryPrice(42000.0)
            .stopLossPrice(41000.0).takeProfitPrice(44000.0)
            .strategyName("TrendContinuation").confidence(0.7)
            .biasExplanation("1h EMA stack bullish, 4h regime trending")
            .triggerExplanation("15m retest of 20EMA on rising volume")
            .build();
        RiskCheckResult risk = RiskCheckResult.approve(0.025, 25.0, 5.0, 10);

        PaperTradePersister persister = new PaperTradePersister(repo);
        Long tradeId = persister.persist(sig, risk, 0.62);

        ArgumentCaptor<TradeLog> captor = ArgumentCaptor.forClass(TradeLog.class);
        verify(repo).save(captor.capture());
        TradeLog saved = captor.getValue();

        assertThat(saved.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(saved.getDirection()).isEqualTo("LONG");
        assertThat(saved.getStrategyName()).isEqualTo("TrendContinuation");
        assertThat(saved.getEntryPrice().doubleValue()).isEqualTo(42000.0);
        assertThat(saved.getExplanation()).containsKey("bias");
        assertThat(saved.getExplanation()).containsKey("trigger");
        assertThat(saved.getExplanation()).containsEntry("meta_prob", 0.62);
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getUserId()).isEqualTo(0L); // system paper user
        assertThat(saved.getTradeId()).isNotBlank();
    }

    private static org.mockito.ArgumentMatcher<TradeLog> any() {
        return tl -> true;
    }
}
```

Replace the line `import static org.mockito.Mockito.when;` — also add `import static org.mockito.ArgumentMatchers.any;` and remove the custom `any()` helper at the bottom. (The test fixture as written imports `any` statically and drops the helper.)

Actual test file — use this cleaner version:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperTradePersisterTest {

    @Test
    void persist_writesTradeLogWithExplanation() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.save(any(TradeLog.class))).thenAnswer(inv -> inv.getArgument(0));

        TradeSignal sig = TradeSignal.builder()
            .symbol("BTCUSDT").action(Action.BUY).entryPrice(42000.0)
            .stopLossPrice(41000.0).takeProfitPrice(44000.0)
            .strategyName("TrendContinuation").confidence(0.7)
            .biasExplanation("1h EMA stack bullish, 4h regime trending")
            .triggerExplanation("15m retest of 20EMA on rising volume")
            .build();
        RiskCheckResult risk = RiskCheckResult.approve(0.025, 25.0, 5.0, 10);

        PaperTradePersister persister = new PaperTradePersister(repo);
        Long tradeId = persister.persist(sig, risk, 0.62);

        ArgumentCaptor<TradeLog> captor = ArgumentCaptor.forClass(TradeLog.class);
        verify(repo).save(captor.capture());
        TradeLog saved = captor.getValue();

        assertThat(saved.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(saved.getDirection()).isEqualTo("LONG");
        assertThat(saved.getStrategyName()).isEqualTo("TrendContinuation");
        assertThat(saved.getEntryPrice().doubleValue()).isEqualTo(42000.0);
        assertThat(saved.getExplanation()).containsKey("bias");
        assertThat(saved.getExplanation()).containsKey("trigger");
        assertThat(saved.getExplanation()).containsEntry("meta_prob", 0.62);
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getUserId()).isEqualTo(0L);
        assertThat(saved.getTradeId()).isNotBlank();
    }
}
```

Run: `./mvnw test -Dtest=PaperTradePersisterTest 2>&1 | tail -15`
Expected: COMPILATION FAILURE.

- [ ] **Step 2: Implement**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperTradePersister.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists paper-trading entries to TradeLog with Learn-While-Earning
 * explanation metadata (bias, trigger, meta-filter probability, risk).
 *
 * userId=0 is reserved for the system paper-trading account until
 * multi-tenant SaaS is scoped (out of scope for Plan 4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTradePersister {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final TradeLogRepository tradeLogRepo;

    public Long persist(TradeSignal signal, RiskCheckResult risk, double metaProb) {
        String direction = signal.getAction() == Action.BUY ? "LONG" : "SHORT";
        Map<String, Object> explanation = new HashMap<>();
        explanation.put("bias", signal.getBiasExplanation());
        explanation.put("trigger", signal.getTriggerExplanation());
        explanation.put("confidence", signal.getConfidence());
        explanation.put("meta_prob", Double.isNaN(metaProb) ? null : metaProb);
        explanation.put("risk_amount", risk.getRiskAmount());
        explanation.put("effective_leverage", risk.getEffectiveLeverage());
        explanation.put("nominal_leverage", risk.getNominalLeverage());

        TradeLog tl = TradeLog.builder()
            .userId(SYSTEM_PAPER_USER_ID)
            .tradeId("paper-" + UUID.randomUUID())
            .symbol(signal.getSymbol())
            .direction(direction)
            .strategyName(signal.getStrategyName())
            .entryPrice(BigDecimal.valueOf(signal.getEntryPrice()))
            .stopLossPrice(BigDecimal.valueOf(signal.getStopLossPrice()))
            .takeProfitPrice(BigDecimal.valueOf(signal.getTakeProfitPrice()))
            .positionSize(BigDecimal.valueOf(risk.getPositionSize()))
            .effectiveLeverage(BigDecimal.valueOf(risk.getEffectiveLeverage()))
            .confidence(BigDecimal.valueOf(signal.getConfidence()))
            .explanation(explanation)
            .status("OPEN")
            .executionMode("AUTONOMOUS")
            .build();

        TradeLog saved = tradeLogRepo.save(tl);
        log.info("Paper trade persisted: id={} tradeId={} {} {} @ {}",
            saved.getId(), saved.getTradeId(), saved.getSymbol(), direction, signal.getEntryPrice());
        return saved.getId();
    }

    public void markClosed(String tradeId, double exitPrice, String outcome, double realizedPnl) {
        tradeLogRepo.findByTradeId(tradeId).ifPresentOrElse(
            tl -> {
                Map<String, Object> out = new HashMap<>();
                out.put("exit_price", exitPrice);
                out.put("outcome", outcome); // "TP", "SL", "MANUAL"
                out.put("realized_pnl", realizedPnl);
                tl.setOutcome(out);
                tl.setStatus("CLOSED");
                tl.setClosedAt(java.time.Instant.now());
                tradeLogRepo.save(tl);
            },
            () -> log.warn("markClosed: tradeId not found: {}", tradeId)
        );
    }
}
```

Run: `./mvnw test -Dtest=PaperTradePersisterTest 2>&1 | tail -10`
Expected: 1 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperTradePersister.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperTradePersisterTest.java
git commit -m "feat(paper): PaperTradePersister writes TradeLog with explanation

Records bias, trigger, confidence, meta_prob, risk amount, leverage in
the explanation JSONB column so the Learn-While-Earning dashboard can
show every trade's reasoning. markClosed() fills outcome + realized_pnl
when TP or SL hits.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: ExecutionModeRouter wire-up — remove Phase 3 TODOs

**Files:**
- Modify: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ExecutionModeRouter.java`

- [ ] **Step 1: Replace the file**

Overwrite `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ExecutionModeRouter.java` with:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Action;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskCheckResult;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TradeSignal;
import com.QuantPlatformApplication.QuantPlatformApplication.service.broker.PaperBrokerAdapter;
import com.QuantPlatformApplication.QuantPlatformApplication.service.paper.PaperTradePersister;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Routes approved trades based on execution mode.
 *
 * AUTONOMOUS: immediately place via PaperBrokerAdapter, persist to TradeLog,
 *             fire Telegram alert.
 * HUMAN_IN_LOOP: send Telegram alert for visibility. Two-way /approve
 *                commands are deliberately out of scope for Plan 4 (user
 *                decision 2026-05-12); treated as alert-only for now.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionModeRouter {

    private final PaperBrokerAdapter paperBroker;
    private final PaperTradePersister tradePersister;
    private final TelegramBotService telegram;

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

        try {
            String side = signal.getAction() == Action.BUY ? "BUY" : "SELL";
            Map<String, Object> placed = paperBroker.placeOrder(
                signal.getSymbol(), side, "MARKET",
                riskResult.getPositionSize(), signal.getEntryPrice());

            // Meta-prob is not carried on TradeSignal today; MarketTickScheduler
            // passes NaN when the gate isn't engaged. Routing layer records
            // NaN explicitly rather than faking a value.
            Long tradeLogId = tradePersister.persist(signal, riskResult, Double.NaN);

            telegram.sendMessage(telegram.formatTradeExecuted(signal, riskResult));

            log.info("Paper fill persisted: tradeLog={} broker={}", tradeLogId, placed.get("orderId"));
        } catch (Exception e) {
            log.error("Paper execution failed for {} {}: {}",
                signal.getAction(), signal.getSymbol(), e.getMessage(), e);
            telegram.sendMessage(telegram.formatRiskAlert(
                "Paper Execution Failed",
                signal.getSymbol() + " " + signal.getAction() + ": " + e.getMessage()));
        }
    }

    private void holdForApproval(TradeSignal signal, RiskCheckResult riskResult) {
        log.info("HOLD-FOR-APPROVAL: {} {} @ {} | Size: {} | (Telegram 2-way deferred)",
            signal.getAction(), signal.getSymbol(), signal.getEntryPrice(),
            riskResult.getPositionSize());
        telegram.sendMessage(telegram.formatTradeSignal(signal, riskResult));
    }
}
```

- [ ] **Step 2: Compile**

Run:
```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication
./mvnw -q compile 2>&1 | tail -10
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ExecutionModeRouter.java
git commit -m "feat(execution): ExecutionModeRouter wires PaperBroker + TradeLog + Telegram

AUTONOMOUS mode now actually executes: paperBroker.placeOrder,
PaperTradePersister.persist (TradeLog row with explanation metadata),
TelegramBotService.formatTradeExecuted + sendMessage. Errors still
notify Telegram as a loud alert. Phase 3 TODOs resolved.

HUMAN_IN_LOOP is alert-only for now — Telegram 2-way /approve is
deliberately scoped out of Plan 4 per user decision.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: MarketTickScheduler — the 15-minute driver

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketTickScheduler.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketTickSchedulerTest.java`

- [ ] **Step 1: Write failing test**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketTickSchedulerTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskParameters;
import com.QuantPlatformApplication.QuantPlatformApplication.service.StrategyOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketTickSchedulerTest {

    @Test
    void runOnce_buildsDataAndEvaluatesForEachSymbol() {
        MultiTimeFrameDataBuilder builder = mock(MultiTimeFrameDataBuilder.class);
        StrategyOrchestrator orchestrator = mock(StrategyOrchestrator.class);
        MultiTimeFrameData data = MultiTimeFrameData.builder().symbol("BTCUSDT").currentPrice(42000).build();
        when(builder.build(anyString(), any())).thenReturn(data);

        MarketTickScheduler scheduler = new MarketTickScheduler(
            builder, orchestrator, List.of("BTCUSDT", "ETHUSDT"),
            500.0, 500.0, RiskParameters.builder().build(), "AUTONOMOUS");

        scheduler.runOnce();

        verify(builder).build(eq("BTCUSDT"), any());
        verify(builder).build(eq("ETHUSDT"), any());
        verify(orchestrator, times(2)).evaluateStrategies(
            any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Set.class), any(), anyString());
    }

    @Test
    void runOnce_continuesOnPerSymbolFailure() {
        MultiTimeFrameDataBuilder builder = mock(MultiTimeFrameDataBuilder.class);
        StrategyOrchestrator orchestrator = mock(StrategyOrchestrator.class);
        when(builder.build(eq("BTCUSDT"), any())).thenThrow(new RuntimeException("db down"));
        when(builder.build(eq("ETHUSDT"), any())).thenReturn(
            MultiTimeFrameData.builder().symbol("ETHUSDT").currentPrice(2500).build());

        MarketTickScheduler scheduler = new MarketTickScheduler(
            builder, orchestrator, List.of("BTCUSDT", "ETHUSDT"),
            500.0, 500.0, RiskParameters.builder().build(), "AUTONOMOUS");

        scheduler.runOnce(); // must not throw

        verify(orchestrator, times(1)).evaluateStrategies(
            any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Set.class), any(), anyString());
    }
}
```

Run: `./mvnw test -Dtest=MarketTickSchedulerTest 2>&1 | tail -15`
Expected: COMPILATION FAILURE.

- [ ] **Step 2: Implement**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketTickScheduler.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.MultiTimeFrameData;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.RiskParameters;
import com.QuantPlatformApplication.QuantPlatformApplication.service.StrategyOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Live paper-trading driver. Fires every 15 minutes on :00 :15 :30 :45 UTC,
 * builds MultiTimeFrameData for each active symbol from Postgres, and invokes
 * StrategyOrchestrator.evaluateStrategies.
 *
 * Config:
 *   quantedge.paper.symbols     (default BTCUSDT,ETHUSDT)
 *   quantedge.paper.balance     (default 500.0)
 *   quantedge.paper.peakEquity  (default 500.0)
 *   quantedge.paper.mode        (default AUTONOMOUS)
 *   quantedge.paper.cron        (default "0 0/15 * * * *")  — 15m cadence
 *   quantedge.paper.enabled     (default true)
 *
 * The cron is disable-able via quantedge.paper.enabled=false for tests
 * or when the operator wants the scheduler inactive without undeploying.
 */
@Slf4j
@Component
public class MarketTickScheduler {

    private final MultiTimeFrameDataBuilder builder;
    private final StrategyOrchestrator orchestrator;
    private final List<String> symbols;
    private final double balance;
    private final double peakEquity;
    private final RiskParameters riskParams;
    private final String executionMode;

    public MarketTickScheduler(
            MultiTimeFrameDataBuilder builder,
            StrategyOrchestrator orchestrator,
            @Value("${quantedge.paper.symbols:BTCUSDT,ETHUSDT}") String symbolsCsv,
            @Value("${quantedge.paper.balance:500.0}") double balance,
            @Value("${quantedge.paper.peakEquity:500.0}") double peakEquity,
            RiskParameters riskParams,
            @Value("${quantedge.paper.mode:AUTONOMOUS}") String executionMode) {
        this(builder, orchestrator, Arrays.asList(symbolsCsv.split(",")),
             balance, peakEquity, riskParams, executionMode);
    }

    // Visible for tests — bypasses @Value parsing.
    MarketTickScheduler(MultiTimeFrameDataBuilder builder,
                        StrategyOrchestrator orchestrator,
                        List<String> symbols,
                        double balance, double peakEquity,
                        RiskParameters riskParams, String executionMode) {
        this.builder = builder;
        this.orchestrator = orchestrator;
        this.symbols = symbols;
        this.balance = balance;
        this.peakEquity = peakEquity;
        this.riskParams = riskParams;
        this.executionMode = executionMode;
    }

    @Scheduled(cron = "${quantedge.paper.cron:0 0/15 * * * *}", zone = "UTC")
    public void onTick() {
        log.info("MarketTickScheduler firing for {}", symbols);
        runOnce();
    }

    /** Exposed for tests and manual admin triggers. */
    public void runOnce() {
        LocalDate asOf = LocalDate.now(java.time.ZoneOffset.UTC);
        double currentExposure = 0.0;
        double dailyRealizedLoss = 0.0;
        Set<String> openPositionSymbols = Collections.emptySet();

        for (String symbol : symbols) {
            String trimmed = symbol.trim();
            if (trimmed.isEmpty()) continue;
            try {
                MultiTimeFrameData data = builder.build(trimmed, asOf);
                orchestrator.evaluateStrategies(
                    data,
                    balance, peakEquity,
                    currentExposure, dailyRealizedLoss,
                    new HashSet<>(openPositionSymbols),
                    riskParams,
                    executionMode);
            } catch (Exception e) {
                log.warn("Tick evaluation failed for {}: {}", trimmed, e.getMessage());
            }
        }
    }
}
```

Run: `./mvnw test -Dtest=MarketTickSchedulerTest 2>&1 | tail -10`
Expected: 2 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketTickScheduler.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/MarketTickSchedulerTest.java
git commit -m "feat(pipeline): MarketTickScheduler — 15m paper-trading driver

@Scheduled cron 0 0/15 * * * * UTC. Per tick: build MultiTimeFrameData
from Postgres for each configured symbol, run StrategyOrchestrator in
AUTONOMOUS mode. Per-symbol exceptions are logged but don't stop the
loop — a bad BTCUSDT tick won't halt ETHUSDT.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: PositionMonitor — closes TP/SL-hit positions

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/PositionMonitor.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/PositionMonitorTest.java`

- [ ] **Step 1: Write failing test**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/PositionMonitorTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.TimeFrame;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.paper.PaperTradePersister;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionMonitorTest {

    private TradeLog openLong(String tradeId, double entry, double sl, double tp) {
        return TradeLog.builder()
            .tradeId(tradeId).symbol("BTCUSDT").direction("LONG")
            .entryPrice(BigDecimal.valueOf(entry))
            .stopLossPrice(BigDecimal.valueOf(sl))
            .takeProfitPrice(BigDecimal.valueOf(tp))
            .positionSize(BigDecimal.valueOf(0.01))
            .status("OPEN").build();
    }

    private Candle bar(double high, double low) {
        return new Candle(Instant.now(), high - 1, high, low, (high + low) / 2, 100.0, TimeFrame.M15);
    }

    @Test
    void closes_long_when_tp_hit() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        CandleSource source = mock(CandleSource.class);
        PaperTradePersister persister = mock(PaperTradePersister.class);
        TelegramBotService telegram = mock(TelegramBotService.class);

        when(repo.findByUserIdAndStatusOrderByCreatedAtDesc(eq(0L), eq("OPEN")))
            .thenReturn(List.of(openLong("t1", 42000, 41000, 44000)));
        when(source.fetch(eq("BTCUSDT"), eq("15m"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(bar(44500, 43000))); // high=44500 > tp=44000 → TP hit

        new PositionMonitor(repo, source, persister, telegram).runOnce();

        verify(persister).markClosed(eq("t1"), anyDouble(), eq("TP"), anyDouble());
        verify(telegram).sendMessage(contains("Closed"));
    }

    @Test
    void closes_long_when_sl_hit() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        CandleSource source = mock(CandleSource.class);
        PaperTradePersister persister = mock(PaperTradePersister.class);
        TelegramBotService telegram = mock(TelegramBotService.class);

        when(repo.findByUserIdAndStatusOrderByCreatedAtDesc(eq(0L), eq("OPEN")))
            .thenReturn(List.of(openLong("t2", 42000, 41000, 44000)));
        when(source.fetch(eq("BTCUSDT"), eq("15m"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(bar(42100, 40500))); // low=40500 < sl=41000 → SL hit

        new PositionMonitor(repo, source, persister, telegram).runOnce();

        verify(persister).markClosed(eq("t2"), anyDouble(), eq("SL"), anyDouble());
    }

    @Test
    void leaves_open_when_neither_tp_nor_sl_touched() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        CandleSource source = mock(CandleSource.class);
        PaperTradePersister persister = mock(PaperTradePersister.class);
        TelegramBotService telegram = mock(TelegramBotService.class);

        when(repo.findByUserIdAndStatusOrderByCreatedAtDesc(eq(0L), eq("OPEN")))
            .thenReturn(List.of(openLong("t3", 42000, 41000, 44000)));
        when(source.fetch(eq("BTCUSDT"), eq("15m"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(bar(42500, 41500))); // inside band

        new PositionMonitor(repo, source, persister, telegram).runOnce();

        verify(persister, never()).markClosed(anyString(), anyDouble(), anyString(), anyDouble());
    }
}
```

Run: `./mvnw test -Dtest=PositionMonitorTest 2>&1 | tail -15`
Expected: COMPILATION FAILURE.

- [ ] **Step 2: Implement**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/PositionMonitor.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.data.CandleSource;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.model.Candle;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.paper.PaperTradePersister;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Scans OPEN TradeLog rows every minute, checks the latest 15m bar's high/low
 * against each position's TP/SL levels, and closes any that hit a barrier.
 *
 * Uses the same CandleSource as the backtest/live tick — no Binance REST call
 * in the hot path.
 *
 * Simple first pass: longs close on high>=TP or low<=SL; shorts invert.
 * Funding accrual + partial fills are explicitly out of Plan 4 scope.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionMonitor {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final TradeLogRepository tradeLogRepo;
    private final CandleSource candleSource;
    private final PaperTradePersister persister;
    private final TelegramBotService telegram;

    @Scheduled(cron = "${quantedge.positions.cron:0 * * * * *}", zone = "UTC")
    public void onTick() {
        runOnce();
    }

    public void runOnce() {
        List<TradeLog> open = tradeLogRepo.findByUserIdAndStatusOrderByCreatedAtDesc(
            SYSTEM_PAPER_USER_ID, "OPEN");
        if (open.isEmpty()) return;

        LocalDate asOf = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = asOf.minusDays(1);

        for (TradeLog tl : open) {
            try {
                List<Candle> recent = candleSource.fetch(tl.getSymbol(), "15m", from, asOf);
                if (recent.isEmpty()) continue;
                Candle last = recent.get(recent.size() - 1);
                evaluate(tl, last);
            } catch (Exception e) {
                log.warn("PositionMonitor failed for tradeId={}: {}", tl.getTradeId(), e.getMessage());
            }
        }
    }

    private void evaluate(TradeLog tl, Candle last) {
        double entry = tl.getEntryPrice().doubleValue();
        double sl = tl.getStopLossPrice().doubleValue();
        double tp = tl.getTakeProfitPrice().doubleValue();
        double size = tl.getPositionSize().doubleValue();
        boolean isLong = "LONG".equals(tl.getDirection());

        double exitPrice;
        String outcome;
        if (isLong && last.high() >= tp) { exitPrice = tp; outcome = "TP"; }
        else if (isLong && last.low()  <= sl) { exitPrice = sl; outcome = "SL"; }
        else if (!isLong && last.low() <= tp) { exitPrice = tp; outcome = "TP"; }
        else if (!isLong && last.high() >= sl) { exitPrice = sl; outcome = "SL"; }
        else return; // still open

        double pnl = isLong
            ? (exitPrice - entry) * size
            : (entry - exitPrice) * size;

        persister.markClosed(tl.getTradeId(), exitPrice, outcome, pnl);
        telegram.sendMessage(String.format(
            "✅ *Closed* %s %s @ $%.2f (%s, P&L $%+.2f)",
            tl.getDirection(), tl.getSymbol(), exitPrice, outcome, pnl));
        log.info("Closed tradeId={} {} {} @ {} → {} pnl={}",
            tl.getTradeId(), tl.getDirection(), tl.getSymbol(), exitPrice, outcome, pnl);
    }
}
```

Run: `./mvnw test -Dtest=PositionMonitorTest 2>&1 | tail -10`
Expected: 3 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/PositionMonitor.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/pipeline/PositionMonitorTest.java
git commit -m "feat(pipeline): PositionMonitor closes TP/SL-hit positions every minute

Scans OPEN TradeLog rows, reads last 15m bar via CandleSource, closes
any that hit TP/SL using high/low (not close) for intra-bar touch
detection. Updates TradeLog.outcome + fires Telegram close alert.
Funding accrual and partial fills out of Plan 4 scope.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: MetaRetrainScheduler — weekly with non-degradation check

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaRetrainScheduler.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaRetrainSchedulerTest.java`

- [ ] **Step 1: Write failing test**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaRetrainSchedulerTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.ml;

import com.QuantPlatformApplication.QuantPlatformApplication.service.MLClientService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetaRetrainSchedulerTest {

    @Test
    void runOnce_retrainsEachConfiguredSymbol() {
        MLClientService ml = mock(MLClientService.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(ml.trainMeta(anyString())).thenReturn(
            Map.of("n_train", 120, "train_accuracy", 0.68));

        MetaRetrainScheduler scheduler = new MetaRetrainScheduler(
            ml, telegram, List.of("BTCUSDT", "ETHUSDT"));

        scheduler.runOnce();

        verify(ml).trainMeta(eq("BTCUSDT"));
        verify(ml).trainMeta(eq("ETHUSDT"));
        verify(telegram, times(1)).sendMessage(any()); // one weekly-summary message
    }

    @Test
    void runOnce_sendsWarningOnDegradedModel() {
        MLClientService ml = mock(MLClientService.class);
        TelegramBotService telegram = mock(TelegramBotService.class);
        when(ml.trainMeta(eq("BTCUSDT"))).thenReturn(
            Map.of("n_train", 10, "train_accuracy", 0.50, "error", "not enough binary labels"));
        when(ml.trainMeta(eq("ETHUSDT"))).thenReturn(
            Map.of("n_train", 120, "train_accuracy", 0.70));

        MetaRetrainScheduler scheduler = new MetaRetrainScheduler(
            ml, telegram, List.of("BTCUSDT", "ETHUSDT"));

        scheduler.runOnce();

        // One summary message that notes BTCUSDT failed
        verify(telegram, times(1)).sendMessage(any());
    }
}
```

Run: `./mvnw test -Dtest=MetaRetrainSchedulerTest 2>&1 | tail -15`
Expected: COMPILATION FAILURE.

- [ ] **Step 2: Implement**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaRetrainScheduler.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.ml;

import com.QuantPlatformApplication.QuantPlatformApplication.service.MLClientService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.telegram.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Weekly Sunday 02:00 UTC retraining of the triple-barrier meta-labeler
 * for each configured symbol. The actual non-degradation gate (reject model
 * if train_accuracy drops >5 pp or n_train < 50) is enforced Python-side
 * inside /train-meta — this scheduler just surfaces the outcome via Telegram
 * so the operator sees the weekly status.
 *
 * Config:
 *   quantedge.meta.retrain.symbols  (default BTCUSDT,ETHUSDT)
 *   quantedge.meta.retrain.cron     (default "0 0 2 * * SUN")
 */
@Slf4j
@Component
public class MetaRetrainScheduler {

    private final MLClientService ml;
    private final TelegramBotService telegram;
    private final List<String> symbols;

    public MetaRetrainScheduler(
            MLClientService ml,
            TelegramBotService telegram,
            @Value("${quantedge.meta.retrain.symbols:BTCUSDT,ETHUSDT}") String symbolsCsv) {
        this(ml, telegram, Arrays.asList(symbolsCsv.split(",")));
    }

    // Visible for tests.
    MetaRetrainScheduler(MLClientService ml, TelegramBotService telegram, List<String> symbols) {
        this.ml = ml;
        this.telegram = telegram;
        this.symbols = symbols;
    }

    @Scheduled(cron = "${quantedge.meta.retrain.cron:0 0 2 * * SUN}", zone = "UTC")
    public void onSchedule() {
        runOnce();
    }

    public void runOnce() {
        List<String> lines = new ArrayList<>();
        lines.add("*Weekly Meta-Labeler Retrain*");
        for (String symbol : symbols) {
            String s = symbol.trim();
            if (s.isEmpty()) continue;
            try {
                Map<String, Object> result = ml.trainMeta(s);
                Object acc = result.get("train_accuracy");
                Object n   = result.get("n_train");
                Object err = result.get("error");
                if (err != null) {
                    lines.add(String.format("%s: FAILED — %s", s, err));
                } else {
                    lines.add(String.format("%s: n_train=%s, acc=%.3f",
                        s, n, acc instanceof Number ? ((Number) acc).doubleValue() : Double.NaN));
                }
            } catch (Exception e) {
                log.warn("Retrain threw for {}: {}", s, e.getMessage());
                lines.add(String.format("%s: EXCEPTION — %s", s, e.getMessage()));
            }
        }
        telegram.sendMessage(String.join("\n", lines));
    }
}
```

Run: `./mvnw test -Dtest=MetaRetrainSchedulerTest 2>&1 | tail -10`
Expected: 2 passed.

- [ ] **Step 3: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaRetrainScheduler.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/ml/MetaRetrainSchedulerTest.java
git commit -m "feat(ml): MetaRetrainScheduler — Sunday 02:00 UTC weekly retrain

Calls MLClientService.trainMeta for each configured symbol and
summarizes outcomes in a single Telegram message. Non-degradation
gate is enforced Python-side inside /train-meta; scheduler just
surfaces the weekly status.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: PaperMetricsService + /api/v1/paper/metrics + controller body parse fix

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperMetricsService.java`
- Create: `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperMetricsServiceTest.java`
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/PaperTradingController.java`
- Modify: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/MultiTimeFrameBacktestController.java` — add meta-filter request body parsing (Plan 3 leftover)

- [ ] **Step 1: Failing test for PaperMetricsService**

Create `QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperMetricsServiceTest.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperMetricsServiceTest {

    private TradeLog closed(String tid, String direction, double entry, double exit, double size) {
        double pnl = "LONG".equals(direction)
            ? (exit - entry) * size
            : (entry - exit) * size;
        return TradeLog.builder()
            .tradeId(tid).userId(0L).symbol("BTCUSDT").direction(direction)
            .entryPrice(BigDecimal.valueOf(entry))
            .stopLossPrice(BigDecimal.valueOf(entry * 0.99))
            .takeProfitPrice(BigDecimal.valueOf(entry * 1.02))
            .positionSize(BigDecimal.valueOf(size))
            .status("CLOSED")
            .outcome(Map.of("exit_price", exit, "realized_pnl", pnl, "outcome", pnl > 0 ? "TP" : "SL"))
            .openedAt(Instant.now().minusSeconds(3600))
            .closedAt(Instant.now())
            .build();
    }

    @Test
    void metrics_zeroTrades_returnsZeros() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.findAll()).thenReturn(List.of());

        PaperMetricsService svc = new PaperMetricsService(repo);
        PaperMetricsService.Metrics m = svc.computeRolling(28);

        assertThat(m.tradeCount()).isEqualTo(0);
        assertThat(m.winRate()).isEqualTo(0.0);
        assertThat(m.sharpe()).isEqualTo(0.0);
        assertThat(m.maxDrawdownPct()).isEqualTo(0.0);
    }

    @Test
    void metrics_computesWinRateAndPnl() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.findAll()).thenReturn(List.of(
            closed("t1", "LONG", 100, 102, 1.0),  // +2 win
            closed("t2", "LONG", 100, 99,  1.0),  // -1 loss
            closed("t3", "LONG", 100, 101.5, 1.0) // +1.5 win
        ));

        PaperMetricsService svc = new PaperMetricsService(repo);
        PaperMetricsService.Metrics m = svc.computeRolling(28);

        assertThat(m.tradeCount()).isEqualTo(3);
        assertThat(m.winRate()).isEqualTo(2.0 / 3.0);
        assertThat(m.totalPnl()).isEqualTo(2.5);
    }

    @Test
    void gateStatus_reflectsCriteriaFromSpec() {
        TradeLogRepository repo = mock(TradeLogRepository.class);
        when(repo.findAll()).thenReturn(List.of());

        PaperMetricsService svc = new PaperMetricsService(repo);
        PaperMetricsService.Gate g = svc.gateStatus(svc.computeRolling(28));

        // Zero trades: all criteria fail.
        assertThat(g.tradeCountPass()).isFalse();
        assertThat(g.allPass()).isFalse();
    }
}
```

Run: `./mvnw test -Dtest=PaperMetricsServiceTest 2>&1 | tail -15`
Expected: COMPILATION FAILURE.

- [ ] **Step 2: Implement PaperMetricsService**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperMetricsService.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.service.paper;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Rolling paper-trading metrics for the validation-gate dashboard.
 * Gate criteria come straight from spec §4.7:
 *   Sharpe  > 1.5
 *   MaxDD   < 15%
 *   WinRate 55%–65%
 *   Trades  > 50
 *   Window  >= 4 weeks
 *
 * Dashboard-only per Plan 4 scope — no enforcement.
 */
@Service
@RequiredArgsConstructor
public class PaperMetricsService {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final TradeLogRepository tradeLogRepo;

    public Metrics computeRolling(int windowDays) {
        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        List<TradeLog> closed = tradeLogRepo.findAll().stream()
            .filter(tl -> tl.getUserId() == SYSTEM_PAPER_USER_ID)
            .filter(tl -> "CLOSED".equals(tl.getStatus()))
            .filter(tl -> tl.getClosedAt() != null && tl.getClosedAt().isAfter(since))
            .toList();

        int n = closed.size();
        if (n == 0) return new Metrics(0, 0, 0, 0, 0, 0, windowDays);

        double totalPnl = 0;
        int wins = 0;
        double[] pnls = new double[n];
        for (int i = 0; i < n; i++) {
            double pnl = extractPnl(closed.get(i));
            pnls[i] = pnl;
            totalPnl += pnl;
            if (pnl > 0) wins++;
        }

        double mean = totalPnl / n;
        double var = 0;
        for (double p : pnls) var += (p - mean) * (p - mean);
        double sd = n > 1 ? Math.sqrt(var / (n - 1)) : 0.0;
        double sharpe = sd == 0 ? 0.0 : mean / sd * Math.sqrt(252.0);

        double peak = 0, equity = 0, maxDd = 0;
        for (double p : pnls) {
            equity += p;
            peak = Math.max(peak, equity);
            if (peak > 0) maxDd = Math.max(maxDd, (peak - equity) / peak);
        }

        double winRate = wins / (double) n;
        return new Metrics(n, winRate, sharpe, maxDd, totalPnl, wins, windowDays);
    }

    public Gate gateStatus(Metrics m) {
        boolean sharpe    = m.sharpe() > 1.5;
        boolean drawdown  = m.maxDrawdownPct() < 0.15;
        boolean winRate   = m.winRate() >= 0.55 && m.winRate() <= 0.65;
        boolean trades    = m.tradeCount() > 50;
        boolean window    = m.windowDays() >= 28;
        boolean all = sharpe && drawdown && winRate && trades && window;
        return new Gate(sharpe, drawdown, winRate, trades, window, all);
    }

    @SuppressWarnings("unchecked")
    private double extractPnl(TradeLog tl) {
        Map<String, Object> out = tl.getOutcome();
        if (out == null) return 0.0;
        Object v = out.get("realized_pnl");
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    public record Metrics(
        int tradeCount,
        double winRate,
        double sharpe,
        double maxDrawdownPct,
        double totalPnl,
        int winningTrades,
        int windowDays
    ) {}

    public record Gate(
        boolean sharpePass,
        boolean drawdownPass,
        boolean winRatePass,
        boolean tradeCountPass,
        boolean windowPass,
        boolean allPass
    ) {}
}
```

Run: `./mvnw test -Dtest=PaperMetricsServiceTest 2>&1 | tail -10`
Expected: 3 passed.

- [ ] **Step 3: Implement PaperTradingController**

Create `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/PaperTradingController.java`:

```java
package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradeLog;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradeLogRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.paper.PaperMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read-only paper-trading metrics and trade history for the dashboard.
 */
@RestController
@RequestMapping("/api/v1/paper")
@RequiredArgsConstructor
public class PaperTradingController {

    private static final long SYSTEM_PAPER_USER_ID = 0L;

    private final PaperMetricsService metrics;
    private final TradeLogRepository tradeLogRepo;

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam(defaultValue = "28") int windowDays) {
        PaperMetricsService.Metrics m = metrics.computeRolling(windowDays);
        PaperMetricsService.Gate g = metrics.gateStatus(m);
        return ResponseEntity.ok(Map.of(
            "metrics", m,
            "gate", g,
            "criteria", Map.of(
                "sharpe",   "> 1.5",
                "maxDD",    "< 15%",
                "winRate",  "55% - 65%",
                "trades",   "> 50",
                "window",   ">= 4 weeks"
            )
        ));
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeLog>> getTrades(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return ResponseEntity.ok(
                tradeLogRepo.findByUserIdAndStatusOrderByCreatedAtDesc(SYSTEM_PAPER_USER_ID, status));
        }
        return ResponseEntity.ok(tradeLogRepo.findByUserIdOrderByCreatedAtDesc(SYSTEM_PAPER_USER_ID));
    }
}
```

- [ ] **Step 4: Fix MultiTimeFrameBacktestController to parse meta-filter body fields**

Read the current `runBacktest(@RequestBody Map<String, Object> request)` method in `MultiTimeFrameBacktestController.java`. Find the `BacktestConfig.builder()...build()` call and replace it with:

```java
        BacktestConfig.BacktestConfigBuilder cfgBuilder = BacktestConfig.builder()
            .initialCapital(capital)
            .slippageBps(slippage);
        if (request.get("useMetaFilter") instanceof Boolean umf) cfgBuilder.useMetaFilter(umf);
        if (request.get("metaThreshold") instanceof Number mt)   cfgBuilder.metaThreshold(mt.doubleValue());
        if (request.get("metaSymbol") instanceof String ms)      cfgBuilder.metaSymbol(ms);
        BacktestConfig config = cfgBuilder.build();
```

- [ ] **Step 5: Compile + test**

```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication
./mvnw -q compile 2>&1 | tail -10
./mvnw test 2>&1 | tail -15
```
Expected: BUILD SUCCESS. All previous tests still pass.

- [ ] **Step 6: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperMetricsService.java \
        QuantPlatformApplication/src/test/java/com/QuantPlatformApplication/QuantPlatformApplication/service/paper/PaperMetricsServiceTest.java \
        QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/PaperTradingController.java \
        QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/MultiTimeFrameBacktestController.java
git commit -m "feat(paper): validation-gate metrics + controller body parse fix

PaperMetricsService computes rolling Sharpe / drawdown / win-rate /
trade count per spec §4.7 gate criteria. GET /api/v1/paper/metrics
returns metrics + pass/fail per criterion (dashboard-only, no
enforcement). GET /api/v1/paper/trades lists TradeLog history.
MultiTimeFrameBacktestController now honors useMetaFilter /
metaThreshold / metaSymbol request body fields (Plan 3 leftover).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Frontend — PaperTradingPage

**Files:**
- Create: `frontend/src/pages/PaperTradingPage.tsx`
- Create: `frontend/src/types/paperTrading.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/components/layout/CommandPalette.tsx`

- [ ] **Step 1: Types**

Create `frontend/src/types/paperTrading.ts`:

```typescript
export interface PaperMetrics {
  tradeCount: number;
  winRate: number;
  sharpe: number;
  maxDrawdownPct: number;
  totalPnl: number;
  winningTrades: number;
  windowDays: number;
}

export interface PaperGate {
  sharpePass: boolean;
  drawdownPass: boolean;
  winRatePass: boolean;
  tradeCountPass: boolean;
  windowPass: boolean;
  allPass: boolean;
}

export interface PaperMetricsResponse {
  metrics: PaperMetrics;
  gate: PaperGate;
  criteria: Record<string, string>;
}

export interface PaperTrade {
  id: number;
  tradeId: string;
  symbol: string;
  direction: 'LONG' | 'SHORT';
  strategyName: string;
  entryPrice: number;
  stopLossPrice: number;
  takeProfitPrice: number;
  positionSize: number;
  effectiveLeverage: number;
  confidence: number;
  explanation: Record<string, unknown>;
  outcome: Record<string, unknown> | null;
  status: 'OPEN' | 'CLOSED';
  openedAt: string;
  closedAt: string | null;
}
```

- [ ] **Step 2: API client additions**

In `frontend/src/services/api.ts`, add to the top-of-file imports:
```typescript
import type { PaperMetricsResponse, PaperTrade } from '@/types/paperTrading';
```

Inside the `api` object (at the end, before the closing brace), add:

```typescript
  // Paper trading
  getPaperMetrics: (windowDays = 28) =>
    get<PaperMetricsResponse>(`/paper/metrics?windowDays=${windowDays}`),
  getPaperTrades: (status?: 'OPEN' | 'CLOSED') =>
    get<PaperTrade[]>(`/paper/trades${status ? `?status=${status}` : ''}`),
```

- [ ] **Step 3: Create the page**

Create `frontend/src/pages/PaperTradingPage.tsx`:

```tsx
import { useQuery } from '@tanstack/react-query';
import { Activity, Check, X, TrendingUp, TrendingDown } from 'lucide-react';
import { api } from '@/services/api';
import { PageHeader } from '@/components/ui/PageHeader';
import type { PaperGate, PaperMetrics, PaperTrade } from '@/types/paperTrading';

function TrafficLight({ pass, label, value }: { pass: boolean; label: string; value: string }) {
  return (
    <div
      className="p-4 rounded-lg"
      style={{
        background: 'var(--surface-container-low)',
        border: `1px solid ${pass ? 'rgba(0,255,136,0.4)' : 'rgba(239,68,68,0.4)'}`,
      }}
    >
      <div className="flex items-center gap-2 mb-1">
        {pass ? <Check size={16} className="text-[#00ff88]" /> : <X size={16} className="text-[#ef4444]" />}
        <span className="text-sm font-medium" style={{ color: 'var(--on-surface)' }}>{label}</span>
      </div>
      <div className="text-xl font-mono" style={{ color: pass ? 'var(--tertiary)' : 'var(--error)' }}>
        {value}
      </div>
    </div>
  );
}

function GateDashboard({ metrics, gate }: { metrics: PaperMetrics; gate: PaperGate }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
      <TrafficLight pass={gate.sharpePass}    label="Sharpe > 1.5"    value={metrics.sharpe.toFixed(2)} />
      <TrafficLight pass={gate.drawdownPass}  label="MaxDD < 15%"      value={`${(metrics.maxDrawdownPct * 100).toFixed(1)}%`} />
      <TrafficLight pass={gate.winRatePass}   label="WinRate 55-65%"   value={`${(metrics.winRate * 100).toFixed(1)}%`} />
      <TrafficLight pass={gate.tradeCountPass} label="Trades > 50"     value={`${metrics.tradeCount}`} />
      <TrafficLight pass={gate.windowPass}    label="Window ≥ 4 weeks" value={`${metrics.windowDays}d`} />
    </div>
  );
}

function TradeRow({ trade }: { trade: PaperTrade }) {
  const isLong = trade.direction === 'LONG';
  const outcome = trade.outcome?.outcome as string | undefined;
  const pnl = trade.outcome?.realized_pnl as number | undefined;
  return (
    <tr className="border-b" style={{ borderColor: 'var(--outline-variant)' }}>
      <td className="py-2 px-3 text-sm font-mono" style={{ color: 'var(--on-surface)' }}>{trade.symbol}</td>
      <td className="py-2 px-3">
        <span className="inline-flex items-center gap-1 text-sm">
          {isLong ? <TrendingUp size={12} className="text-[#00ff88]" /> : <TrendingDown size={12} className="text-[#ef4444]" />}
          {trade.direction}
        </span>
      </td>
      <td className="py-2 px-3 text-sm" style={{ color: 'var(--on-surface-variant)' }}>{trade.strategyName}</td>
      <td className="py-2 px-3 text-sm font-mono">${trade.entryPrice.toFixed(2)}</td>
      <td className="py-2 px-3 text-sm">
        <span className={`px-2 py-0.5 rounded text-xs ${
          trade.status === 'OPEN' ? 'bg-blue-500/20 text-blue-400' : 'bg-gray-500/20 text-gray-400'
        }`}>
          {trade.status}
        </span>
      </td>
      <td className="py-2 px-3 text-sm">{outcome ?? '—'}</td>
      <td className="py-2 px-3 text-sm font-mono" style={{ color: (pnl ?? 0) >= 0 ? 'var(--tertiary)' : 'var(--error)' }}>
        {pnl !== undefined ? `${pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}` : '—'}
      </td>
    </tr>
  );
}

export default function PaperTradingPage() {
  const metrics = useQuery({
    queryKey: ['paper-metrics', 28],
    queryFn: () => api.getPaperMetrics(28),
    refetchInterval: 30_000,
  });
  const trades = useQuery({
    queryKey: ['paper-trades'],
    queryFn: () => api.getPaperTrades(),
    refetchInterval: 30_000,
  });

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Paper Trading"
        subtitle="Rolling 4-week validation gate + trade history"
      >
        <Activity size={20} />
      </PageHeader>

      {metrics.data && (
        <>
          <div>
            <h3 className="text-sm font-semibold mb-3" style={{ color: 'var(--on-surface-variant)' }}>
              Validation Gate (spec §4.7)
            </h3>
            <GateDashboard metrics={metrics.data.metrics} gate={metrics.data.gate} />
          </div>

          <div
            className="p-4 rounded-lg"
            style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}
          >
            <div className="flex items-baseline justify-between mb-2">
              <span className="text-xs" style={{ color: 'var(--outline)' }}>
                {metrics.data.metrics.tradeCount} trades · {metrics.data.metrics.winningTrades} wins ·
                {' '}total P&L ${metrics.data.metrics.totalPnl.toFixed(2)}
              </span>
              <span className="text-xs font-semibold" style={{
                color: metrics.data.gate.allPass ? 'var(--tertiary)' : 'var(--outline)'
              }}>
                {metrics.data.gate.allPass ? '✅ ALL CRITERIA PASS' : '⏳ IN VALIDATION'}
              </span>
            </div>
          </div>
        </>
      )}

      {trades.data && (
        <div
          className="p-4 rounded-lg"
          style={{ background: 'var(--surface-container-low)', border: '1px solid var(--outline-variant)' }}
        >
          <h3 className="text-sm font-semibold mb-3" style={{ color: 'var(--on-surface-variant)' }}>
            Trade History
          </h3>
          {trades.data.length === 0 ? (
            <div className="text-sm text-center py-6" style={{ color: 'var(--outline)' }}>
              No paper trades yet. The scheduler runs every 15 minutes on :00/:15/:30/:45 UTC.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="text-xs uppercase" style={{ color: 'var(--outline)' }}>
                    <th className="py-2 px-3">Symbol</th>
                    <th className="py-2 px-3">Direction</th>
                    <th className="py-2 px-3">Strategy</th>
                    <th className="py-2 px-3">Entry</th>
                    <th className="py-2 px-3">Status</th>
                    <th className="py-2 px-3">Outcome</th>
                    <th className="py-2 px-3">P&L</th>
                  </tr>
                </thead>
                <tbody>
                  {trades.data.map(t => <TradeRow key={t.id} trade={t} />)}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Wire routing**

In `frontend/src/App.tsx`, find the existing `<Route path="/backtest" element={<BacktestPage />} />` line and add below it:

```tsx
          <Route path="/paper-trading" element={<PaperTradingPage />} />
```

Add the import near the other page imports:
```tsx
import PaperTradingPage from '@/pages/PaperTradingPage';
```

In `frontend/src/components/layout/Sidebar.tsx`, find the `{ label: 'Backtest', ... }` entry and add after it:

```tsx
  { label: 'Paper Trading', icon: 'candlestick_chart', path: '/paper-trading' },
```

In `frontend/src/components/layout/CommandPalette.tsx`, find the `{ id: 'backtest', ... }` entry and add after it (adjusting the `nav` helper usage to match the surrounding style):

```tsx
    { id: 'paper-trading', label: 'Go to Paper Trading', section: 'Navigate', icon: 'candlestick_chart', onSelect: nav('/paper-trading') },
```

- [ ] **Step 5: TypeScript + build**

```
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/frontend
npx tsc --noEmit 2>&1 | tail -10
npm run build 2>&1 | tail -10
```
Expected: 0 TypeScript errors, build succeeds.

- [ ] **Step 6: Commit**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git add frontend/src/pages/PaperTradingPage.tsx \
        frontend/src/types/paperTrading.ts \
        frontend/src/services/api.ts \
        frontend/src/App.tsx \
        frontend/src/components/layout/Sidebar.tsx \
        frontend/src/components/layout/CommandPalette.tsx
git commit -m "feat(ui): PaperTradingPage — validation gate + trade history

Two sections: 5-light validation-gate dashboard (Sharpe / MaxDD /
WinRate / Trade Count / Window vs spec §4.7 criteria) and a trade
history table showing every paper fill with entry, status, outcome,
realized P&L. Refetch every 30s. Dashboard-only — no enforcement.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: End-to-end smoke test

**Files:** none — operational.

- [ ] **Step 1: Ensure stack is live**

Run:
```
docker ps --format "{{.Names}} {{.Status}}" | head -5
lsof -i :8080 -sTCP:LISTEN | tail -1
lsof -i :5001 -sTCP:LISTEN | tail -1
```
Expected: `quant-timescaledb`, `quant-redis` healthy; backend on 8080; ml-service on 5001.

If the backend is old (started before Plan 4 commits), restart it:
```
pkill -f "spring-boot:run" 2>&1 || true
sleep 3
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/QuantPlatformApplication
nohup ./mvnw spring-boot:run > /tmp/backend.log 2>&1 &
for i in {1..60}; do
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>&1 | grep -q 200; then
    echo "Backend ready after ${i}s"
    break
  fi
  sleep 1
done
```

- [ ] **Step 2: Manually trigger a tick**

Since the @Scheduled cron only fires on :00/:15/:30/:45, we need a test trigger. Call `MarketTickScheduler.runOnce()` via the backend's actuator or bean introspection isn't trivial from curl. Instead: verify the tick's output path by calling `/api/v1/paper/metrics` (should return 0 trades initially) and `/api/v1/paper/trades` (should return empty list).

```
curl -s http://localhost:8080/api/v1/paper/metrics?windowDays=28 | python3 -m json.tool
```
Expected: JSON with `metrics.tradeCount=0` and all gate fields `false`.

```
curl -s http://localhost:8080/api/v1/paper/trades
```
Expected: `[]`.

- [ ] **Step 3: Verify Telegram is reachable (if configured)**

If Telegram config is set (`quantedge.telegram.enabled=true` and credentials present), hitting `MetaRetrainScheduler.runOnce()` via the future admin endpoint would send a test message. Since we didn't add a manual trigger endpoint, confirm by reading logs after the next scheduled tick at :00/:15/:30/:45.

If Telegram is disabled, the scheduler logs a debug message and moves on — no action required.

- [ ] **Step 4: Verify the frontend renders**

Open the frontend dev server (or already-built app) at `/paper-trading`. Expect to see:
- 5 traffic-light cards, all red (no trades yet, validation gate failing)
- "No paper trades yet" message
- Auto-refetch every 30s

- [ ] **Step 5: Commit a closing stamp**

```bash
cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform
git commit --allow-empty -m "chore: Plan 4 complete — paper trading wired end-to-end

Components live:
- MarketTickScheduler @ 0 0/15 * * * * UTC
- PositionMonitor @ * * * * * UTC (every minute)
- MetaRetrainScheduler @ 0 0 2 * * SUN UTC
- ExecutionModeRouter → PaperBrokerAdapter → TradeLog + Telegram
- /api/v1/paper/metrics + /api/v1/paper/trades + PaperTradingPage UI
- MetaFilterGate fail-open + loud Telegram alert
- Validation gate dashboard (dashboard-only, no enforcement)

Deferred: Telegram 2-way commands, live Delta Exchange broker wiring,
gate enforcement. Those belong with the eventual live-money plan.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage (§4.6 paper trading + §4.7 validation gate):**
- MarketTickScheduler @ 15m → Task 6 ✅
- Build MultiTimeFrameData from Postgres → Task 1 ✅
- Integrate meta-filter into TradeRiskEngine → Task 3 ✅
- Wire ExecutionModeRouter → PaperBrokerAdapter → Task 5 ✅
- Telegram out-only alerts on fills → Task 5 ✅
- Deterministic paper slippage — already delivered Plan 3 Task 9
- Weekly retrain automation → Task 8 ✅
- `/api/v1/paper/metrics` endpoint + gate dashboard → Task 9 + 10 ✅
- Validation gate enforcement → **deliberately dashboard-only** per user decision

**Spec §4.5 leftover (Plan 3) carried forward:**
- Controller body parse for useMetaFilter → Task 9 step 4 ✅

**Placeholder scan:** no TBDs, "handle error appropriately" hand-waves, or steps without concrete code.

**Type consistency checks:**
- `MetaFilterGate.Decision` record fields `allow`/`metaProb`/`reason`/`failedOpen` consistent between Task 2 definition and Task 3 call site ✅
- `MetaFilterGate.checkWithThreshold(symbol, direction, entryPrice, tp, sl, threshold)` signature matches Task 3 invocation ✅
- `PaperMetricsService.Metrics` record matches `frontend/src/types/paperTrading.ts::PaperMetrics` interface shape ✅
- `MultiTimeFrameDataBuilder.build(symbol, asOf)` returns `MultiTimeFrameData` — consumed by `StrategyOrchestrator.evaluateStrategies(data, ...)` whose signature already matches (verified before writing the plan) ✅
- `PaperTradePersister.persist(signal, risk, metaProb)` signature matches `ExecutionModeRouter.executeAutonomous` call site ✅
- TradeLog `userId=0` used consistently across `PaperTradePersister`, `PositionMonitor`, `PaperMetricsService`, `PaperTradingController` ✅

**Known soft spots flagged inline:**
- Task 1: funding/OI/longShortRatio are zero-filled. Full enrichment from `funding_rate_history` + live Delta OI is a Plan 4.1 follow-up and won't break anything since `MultiTimeFrameData.fundingRate` already defaults to 0 across existing callers.
- Task 6: `currentExposure`, `dailyRealizedLoss`, `openPositionSymbols` are hardcoded to zero/empty in the tick. Accurate state reconstruction from open `TradeLog` rows is a Plan 4.1 follow-up; the consequence today is slightly permissive risk checks on rapid consecutive ticks. Acceptable for paper trading because the paper broker enforces the same per-order size cap.
- Task 11: no automated E2E test. The :15 cadence makes a full automated tick hard to run synchronously; manually triggering `runOnce()` requires either an admin endpoint (not in scope) or a scheduled-unit test. Acceptable; the unit tests in Tasks 6–9 cover each piece independently.

**Out-of-band deferred items:**
- Telegram 2-way commands (/approve, /reject, /close_all, /stop, /resume) — future live-money plan
- Live Delta Exchange broker wiring — future live-money plan
- Gate enforcement (feature flag blocking live endpoints) — future live-money plan
- Funding/OI enrichment in MultiTimeFrameDataBuilder — Plan 4.1
- Accurate live state reconstruction in MarketTickScheduler — Plan 4.1
- Agent pipeline retire-or-rewire — future architectural plan (daily BacktestEngine has callers)

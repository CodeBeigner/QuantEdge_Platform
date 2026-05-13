# ML Rebuild, Historical Data Pipeline, Backtest Consolidation, and Paper Trading Wire-Up

**Date:** 2026-05-10
**Status:** Design — awaiting user review
**Supersedes:** Nothing yet; this is the first concrete plan for the ML/paper-trading foundation.

---

## 1. Problem Statement

Four tightly-coupled issues are blocking the QuantEdge platform from running a meaningful paper trade:

1. **ML service has look-ahead bias.** `ml-service/feature_engine.py:79` sets the target as `close.shift(-1) > close`. The model trains on tomorrow's close but cannot see it at prediction time. Every accuracy, IC, and walk-forward number the service reports is inflated by leakage. The XGBoost + LSTM + ensemble endpoints look functional but produce meaningless signals.
2. **ML is not wired into trading.** `StrategyOrchestrator` iterates `MultiTimeFrameStrategy` implementations and runs them through `TradeRiskEngine`. It never calls any `/predict*` endpoint. ML is consultative only.
3. **Historical data is not seeded.** Postgres `market_data` is empty. `BinanceHistoricalClient` fetches on demand into memory. When unreachable, `MultiTimeFrameBacktestController` silently falls back to synthetic candles — "backtests" can run on fake data without any warning.
4. **Paper trading is not wired end-to-end.** `ExecutionModeRouter` is a `TODO: Phase 3` stub. No scheduled job runs strategies. `StrategyExecutor.scheduleAtFixedRate` exists but is never called. Two backtest engines (`BacktestEngine` daily, `MultiTimeFrameBacktestEngine` 15m) have inconsistent slippage/fee models.

The put-call-parity options gate brainstorm (started earlier in the same session) is explicitly deferred until the four issues above are resolved. A sentiment gate on top of a broken baseline is worse than useless.

---

## 2. Goals and Non-Goals

### Goals
- Replace the current ML stack with two models that have documented edge in crypto perpetuals: a Triple-Barrier Meta-Labeler and an Order-Flow GBDT.
- Seed `market_data` with 6 years of real OHLCV for BTCUSDT and ETHUSDT at 15m, 1h, and 4h, plus funding-rate and open-interest history.
- Consolidate to a single backtest engine with consistent fees, funding, slippage, and market-impact modeling; remove the synthetic-candle fallback as a silent behavior.
- Wire paper trading end to end: scheduled `@Scheduled` job every 15 minutes, signals routed through `ExecutionModeRouter` to `PaperBrokerAdapter`, Telegram out-only alerts on fills.
- Integrate the meta-labeler into `TradeRiskEngine` as a veto: trades with `meta_prob <= 0.55` are rejected.

### Non-Goals
- No live-money trading in this workstream. That gate requires 4+ weeks of successful paper trading first.
- No options-market PCR gate in this workstream. Deferred to a follow-up spec.
- No new frontend work. Existing paper-trading dashboard stays as-is; we may add a single "ML signal" column later.
- No multi-tenant or SaaS features. Single-user local deployment.
- No deep reinforcement learning. Ruled out on research grounds for retail scale.
- No overhaul of the existing rule-based strategies. They remain the primary signal generators; the new ML is a filter.

---

## 3. Architecture Overview

```
                    ┌──────────────────────────────────────────┐
                    │  Binance Vision (data.binance.vision)     │
                    │  monthly ZIP CSVs: OHLCV + funding        │
                    └────────────────┬─────────────────────────┘
                                     │  (one-shot seed + monthly cron)
                                     ▼
          ┌─────────────────────────────────────────────────────┐
          │  ml-service/ingest/seed_binance_vision.py (NEW)      │
          │  - pandas read_csv from zip                          │
          │  - validate columns, timestamp alignment             │
          │  - upsert into Postgres via COPY + ON CONFLICT       │
          └────────────────┬─────────────────────────────────────┘
                           │
                           ▼
   ┌────────────────────────────────────────────────────────────┐
   │  Postgres / TimescaleDB                                     │
   │  - market_data (PK changed to symbol, timeframe, time)      │ <-- V22 migration
   │  - funding_rate_history (NEW)                               │ <-- V22
   │  - open_interest_history (NEW)                              │ <-- V22
   │  - ml_feature_snapshots (existing, live-populated)          │
   └────────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┴───────────────┐
           │                               │
           ▼                               ▼
┌──────────────────────┐        ┌────────────────────────┐
│ Java Spring backend  │        │ Python ml-service      │
│ (existing, modified) │        │ (existing, rewritten)  │
│                      │        │                        │
│ @Scheduled every 15m │        │ NEW endpoints:         │
│   → fetch latest bar │        │   POST /predict-meta   │
│   → MultiTimeFrame   │        │   POST /predict-flow   │
│     strategies       │        │                        │
│   → signal           │        │ DEPRECATED (kept):     │
│   → TradeRiskEngine  │◄──────►│   /train, /predict,    │
│      ├─ risk checks  │  HTTP  │   /train-lstm, etc.    │
│      └─ meta-gate    │        │                        │
│   → ExecutionMode-   │        │ Shared:                │
│     Router           │        │   /health, /features   │
│   → PaperBroker-     │        │                        │
│     Adapter          │        │                        │
│   → Telegram alert   │        │                        │
└──────────────────────┘        └────────────────────────┘
```

### Service boundaries and ownership
- **Java owns execution.** `StrategyOrchestrator`, `TradeRiskEngine`, `ExecutionModeRouter`, broker adapters, and the scheduled tick.
- **Python owns ML.** Feature engineering, model training, prediction, walk-forward validation, and the new historical ingestion script.
- **Postgres is the integration point.** Neither service calls the other for training data; both read the same `market_data` and `ml_feature_snapshots` tables.
- **HTTP is the integration point at runtime.** Java calls Python's `/predict-meta` and `/predict-flow` once per signal. Fail-open on Python being unreachable (warn + allow), matching the gating-filter design decision.

---

## 4. Component Designs

Each component below has one purpose, a clear interface, and can be understood and tested independently.

### 4.1 Flyway migration `V22__multi_timeframe_and_derivatives_history.sql`

**Purpose:** make `market_data` capable of holding multiple timeframes per symbol, and add funding-rate and open-interest history tables.

**Changes:**
```sql
-- 1. Add timeframe column to market_data and update PK
ALTER TABLE market_data ADD COLUMN timeframe VARCHAR(8) NOT NULL DEFAULT '15m';
ALTER TABLE market_data DROP CONSTRAINT market_data_pkey;
ALTER TABLE market_data ADD CONSTRAINT market_data_pkey PRIMARY KEY (symbol, timeframe, time);
CREATE INDEX idx_market_data_symbol_tf_time ON market_data (symbol, timeframe, time DESC);

-- 2. Funding rate history (Binance 8h schedule by default)
CREATE TABLE funding_rate_history (
    symbol       VARCHAR(32) NOT NULL,
    time         TIMESTAMPTZ NOT NULL,
    funding_rate NUMERIC(20, 10) NOT NULL,
    mark_price   NUMERIC(20, 8),
    PRIMARY KEY (symbol, time)
);
SELECT create_hypertable('funding_rate_history', 'time', chunk_time_interval => INTERVAL '30 days', if_not_exists => TRUE);

-- 3. Open interest history
CREATE TABLE open_interest_history (
    symbol         VARCHAR(32) NOT NULL,
    time           TIMESTAMPTZ NOT NULL,
    period         VARCHAR(8)  NOT NULL,   -- '5m', '15m', '1h', '4h'
    open_interest  NUMERIC(24, 8) NOT NULL,
    PRIMARY KEY (symbol, period, time)
);
SELECT create_hypertable('open_interest_history', 'time', chunk_time_interval => INTERVAL '30 days', if_not_exists => TRUE);
```

**Rollback:** straightforward — drop new tables, drop new column, restore old PK. Flyway V22 is safe to revert because the new tables are empty until seeded.

**Risks:** existing rows in `market_data` (if any — table is believed empty) get `timeframe = '15m'` by default. Acceptable because the table is effectively unused.

### 4.2 Python seeder `ml-service/ingest/seed_binance_vision.py`

**Purpose:** one-shot bulk seed of 2020-01 through current month OHLCV + funding for BTCUSDT and ETHUSDT across 15m/1h/4h.

**Interface (CLI):**
```
python -m ingest.seed_binance_vision \
  --symbols BTCUSDT,ETHUSDT \
  --timeframes 15m,1h,4h \
  --start 2020-01 \
  --end 2026-05 \
  --types klines,fundingRate
```

**Flow:**
1. For each (symbol, timeframe, year-month), download
   `https://data.binance.vision/data/futures/um/monthly/klines/{SYM}/{TF}/{SYM}-{TF}-{YYYY-MM}.zip`
2. Unzip in memory, parse with `pandas.read_csv` (known 12-column schema).
3. Validate: 
   - `open_time` strictly increasing
   - `close_time - open_time` matches expected TF length
   - No NaN in OHLCV columns
4. Convert `open_time` ms-epoch to UTC `TIMESTAMPTZ`.
5. Upsert into `market_data` with `ON CONFLICT (symbol, timeframe, time) DO NOTHING` — bulk seed is idempotent.
6. Same flow for funding-rate CSVs into `funding_rate_history`, different URL path and column layout.
7. Open interest: use Binance REST `GET /futures/data/openInterestHist` for the rolling 30-day window; for longer history, pull from Coinalyze's free-tier API if a key is provided, otherwise skip with warning.

**Error handling:** 404 on a month means Binance has no data there (skip with log); network errors retry 3x with backoff; schema mismatch fails the whole run (don't write partial).

**Testing:**
- Unit test the parser on a committed fixture ZIP (trim one real month to a small sample).
- Integration test against the real Binance Vision URL for a single recent month (cheap, ~1 MB).
- Assert idempotency: running the script twice leaves the row count unchanged.

### 4.3 Java incremental gap-filler (existing `BinanceHistoricalClient` extension)

**Purpose:** fill the gap between the last Binance Vision monthly dump and "now" using Binance's REST `/fapi/v1/klines` — the client that already exists.

**Changes:**
- Add `persistToMarketData(List<Candle>, symbol, timeframe)` writer that batch-upserts via JPA with `ON CONFLICT DO NOTHING`.
- New `@Scheduled(cron = "0 15 0 * * *")` (daily 00:15 UTC) job in a new `MarketDataSyncScheduler`:
  - For each (symbol, timeframe), find `MAX(time)` in `market_data`.
  - Fetch `BinanceHistoricalClient.fetchCandles(symbol, tf, since=maxTime - 1h, until=now)`.
  - Write back.
- Expose `POST /api/v1/admin/market-data/resync` to trigger a manual run.

**Testing:** integration test runs a single symbol gap-fill against a test DB; assert row count grows, no duplicates, no holes.

### 4.4 ML service rewrite — new endpoints

Old endpoints stay in place, **marked deprecated**, wiring removed from trading path. No code deletions. New endpoints added.

**`ml-service/labelers/triple_barrier.py` (new):**
- Function `apply_triple_barrier(prices, signals, tp_pct, sl_pct, max_bars)` returns a DataFrame with columns `signal_time, label, outcome_time` where `label ∈ {1 if TP first, 0 if SL first, -1 if max_bars expire}`.
- Direct implementation from López de Prado *Advances in Financial ML* Ch. 3.
- `-1` labels are dropped before training (no clear outcome).

**`ml-service/models/meta_labeler.py` (new):**
- `MetaLabeler` class wrapping `XGBClassifier`.
- Features: all existing TA features from `feature_engine.py` + primary-signal direction (+1 / -1) + funding rate + OI delta.
- Binary target from `triple_barrier.py`.
- `train(df, primary_signals)`, `predict(df, primary_signal)`, `save(path)`, `load(path)` methods.
- `walk_forward_train()` using purged K-fold (purge window = max holding period) — used in production training path, not optional.

**`ml-service/models/order_flow.py` (new):**
- `OrderFlowModel` class wrapping `LGBMClassifier` (lighter than XGB for this feature set).
- **Fallback feature set** (chosen because L2 book reconstruction is operationally heavy at retail scale):
  - CVD (cumulative volume delta) computed from aggTrades when available, else synthesized from close-price direction × volume
  - Rolling aggressive-buyer-ratio (taker buy volume / total volume)
  - Funding rate and its 8-period MA
  - OI delta over last 1h and 4h, normalized by 7-day median OI
  - Perp-spot basis (needs spot price — use Binance spot for now)
  - Liquidation proxies from Coinalyze if available; else zero
- Target: sign of 15-minute-forward return, trained with triple-barrier labels for directional predictions.

**New endpoints in `main.py`:**
```
POST /predict-meta/{symbol}
  body: { "primary_signal": "LONG"|"SHORT", "entry_price": float,
          "tp_pct": 0.02, "sl_pct": 0.01, "tf": "15m" }
  returns: { "meta_prob": 0.62, "direction": "LONG", "model_version": "...",
             "feature_snapshot": {...} }

POST /predict-flow/{symbol}
  body: { "tf": "15m" }
  returns: { "flow_score": 0.31, "direction": "LONG" | "SHORT" | "NEUTRAL",
             "confidence": 0.58 }
```

**Deprecation:**
- Keep `/train`, `/predict`, `/train-lstm`, `/predict-lstm`, `/predict-ensemble`, `/ic` endpoints working.
- Add `X-Deprecated: true` response header and log a warning on each call.
- Remove their calls from any Java code that touches the trading path (audit shows only `IntentParserService` calls ML today — keep that, it's a user-facing command).
- Fix the look-ahead bias in `feature_engine.py:79` regardless — even if old endpoints are deprecated, the feature computation is shared and must produce honest training labels for the new models.

### 4.5 Consolidated backtest engine

**Decision:** delete `engine/BacktestEngine.java` and its service/controller. Keep only `MultiTimeFrameBacktestEngine`. One engine, one fee/funding/slippage model.

**Changes to `MultiTimeFrameBacktestEngine.java`:**
- **Delete synthetic-candle fallback** in `MultiTimeFrameBacktestController`. If `BinanceHistoricalClient` returns empty, fail the request with HTTP 503 and a clear error. Silent fakery is worse than a loud error.
- **Standardize slippage:** 3 bps maker, 7 bps taker. Configurable per-backtest but defaults are explicit, not 10 bps.
- **Add market-impact model:** `impact_bps = 2 * sqrt(notional / adv_1h) * 1e4`. ADV (average daily volume) read from `market_data`. Cheap square-root-impact model, matches what most quant shops use for sub-retail sizes.
- **Source data from Postgres first,** Binance REST second, never synthetic:
  ```
  candles = marketDataService.fetchCandles(symbol, tf, start, end);
  if (candles.size() < expectedBars * 0.95) {
      candles = binanceHistoricalClient.fetchCandles(...);  // fall through
      if (candles.isEmpty()) throw new DataNotAvailableException(...);
  }
  ```

**API:** existing `POST /api/v1/backtests/multi-tf` unchanged in URL and request shape. Responses include new `slippage_bps`, `impact_bps_avg` fields.

**Testing:**
- Existing tests updated for new defaults.
- New test: backtest on 6 months of seeded BTCUSDT 15m data, assert Sharpe and max-DD are within a sanity range (not checking exact numbers — checking the engine runs end-to-end).
- New test: request against unseeded symbol → assert 503, not silently-synthetic candles.

### 4.6 Paper trading wire-up

**New component: `MarketTickScheduler.java`** in `service/pipeline/`:
- `@Scheduled(cron = "0 */15 * * * *")` (every 15 minutes on :00, :15, :30, :45).
- For each active strategy-symbol pair:
  1. Fetch latest 500 bars from `market_data` via `MarketDataService`.
  2. Build `MultiTimeFrameData` (15m/1h/4h aligned).
  3. Call `StrategyOrchestrator.evaluateStrategies(data, ...)` with execution mode `PAPER`.
- Skips cleanly if it's already running (uses `@Scheduled` default non-reentrant lock).

**Changes to `StrategyOrchestrator.java`:**
- Inject the ML client.
- Between signal generation and risk engine, for each signal:
  1. Call `mlClient.predictMeta(symbol, signal.direction, signal.entryPrice, tpPct, slPct, tf)`.
  2. Attach `meta_prob` to the `TradeSignal` for logging.
  3. Do NOT veto here — vetoing happens inside `TradeRiskEngine` so the risk-rejection path is uniform.

**Changes to `TradeRiskEngine.java`:**
- Add a new `RiskCheck` step: "ML meta-filter".
- Read `meta_prob` threshold from `RiskParameters` (default 0.55, configurable per user for later SaaS multitenancy).
- If `meta_prob < threshold`: reject with reason `ML_META_BELOW_THRESHOLD`.
- If ML service is unreachable: log warning, return allow (fail-open, matches the earlier design decision for the PCR gate).

**Changes to `ExecutionModeRouter.java`:**
- Delete `TODO: Phase 3` stubs.
- Implement routing:
  ```
  switch (executionMode) {
    case PAPER -> paperBrokerAdapter.placeOrder(signal.toOrderRequest());
    case LIVE  -> deltaBrokerAdapter.placeOrder(...);  // still stub, guarded
    case MANUAL -> telegramNotifier.sendApprovalRequest(signal);
  }
  ```
- For this workstream only `PAPER` is implemented. `LIVE` and `MANUAL` remain guarded by a config flag that defaults to false.

**Changes to `PaperBrokerAdapter.java`:**
- Implement `getOpenOrders()` properly (read from `OrderManagementService`).
- Implement `getOrder(id)` properly.
- Replace `Math.random()` slippage with deterministic-per-order slippage: `slippage_bps = 3 + (order.id.hashCode() % 4)` — reproducible across replays.

**Telegram alerting:**
- On every paper fill, send a concise alert: strategy, symbol, side, entry, stop, target, meta_prob, timestamp.
- Out-only for this workstream. `/approve` / `/execute` two-way commands deferred.

### 4.7 Validation gate before live money

Before any code path that touches real Delta API is enabled:
- At least 90 days of real Binance historical data seeded and passing a sanity backtest.
- 4+ weeks of paper trading with:
  - Sharpe ratio > 1.5
  - Maximum drawdown < 15%
  - Win rate between 55% and 65%
  - More than 50 trades (enough sample)
  - Telegram alerts delivered on every fill, zero missed
- Walk-forward retraining of both ML models executed at least once with no crash.

Only after all five hold does a follow-up spec unlock the Delta broker live path.

---

## 5. Data Flow

### Seeding (one-time and daily)
1. User runs `python -m ingest.seed_binance_vision ...` once — seeds 2020-01 → current month.
2. Java `MarketDataSyncScheduler` runs daily at 00:15 UTC, fetches the last 24-48h via REST, upserts.

### Feature engineering (online, per tick)
1. `MarketTickScheduler` fires at :00/:15/:30/:45.
2. `MLFeatureCollector` (existing) polls Delta API for live funding rate, OI, book imbalance, writes to `ml_feature_snapshots`.
3. `StrategyOrchestrator` fetches `MultiTimeFrameData` and latest `ml_feature_snapshots` row.
4. Strategies generate signals.
5. For each signal, `TradeRiskEngine` calls `/predict-meta` with features from the snapshot, receives `meta_prob`.
6. If `meta_prob >= 0.55`: route to `PaperBrokerAdapter`, record trade, send Telegram alert.
7. If `meta_prob < 0.55`: log rejection with reason `ML_META_BELOW_THRESHOLD`, do not trade.

### Training (weekly, offline)
1. Cron (or manual trigger) fires `POST /ml/retrain-meta/{symbol}` and `POST /ml/retrain-flow/{symbol}`.
2. Python pulls 6-month rolling window from `market_data`, `funding_rate_history`, `open_interest_history`.
3. Applies triple-barrier labeling against the primary strategies' historical signals (replayed offline).
4. Runs purged K-fold walk-forward, refuses to save the new model if out-of-sample IC < 0.02 or log-loss worsens.
5. Writes new model to `models/{symbol}/{type}/v{n}.json` and atomically updates a `latest.json` symlink file.

---

## 6. Error Handling and Operational Concerns

- **ML service unreachable:** fail-open in `TradeRiskEngine`, loud Telegram alert, metric emitted.
- **Postgres unreachable:** fail everything. No silent degradation. `MarketTickScheduler` logs and skips this tick.
- **Binance Vision URL missing for a month:** skip with log, continue. Some months in 2020 had symbol-specific gaps.
- **Model prediction throws:** treat as unreachable — fail-open in risk engine.
- **Market data has a gap larger than 2 bars:** `MarketTickScheduler` skips this tick and alerts — don't predict on stale data.
- **`@Scheduled` tick takes longer than 15m:** Spring's default is non-reentrant; the next tick is skipped. Add a metric.

---

## 7. Testing Strategy

- **Unit tests** for the triple-barrier labeler: synthetic price series where TP/SL are obvious, assert labels match.
- **Unit tests** for the meta-labeler and flow model: assert `predict` returns a value in `[0, 1]` and doesn't crash on a feature row full of NaNs (fill with zeros / column median).
- **Unit tests** for `V22` migration: run Flyway against a disposable DB, assert new columns/tables exist, assert seeding a 15m row and a 1h row for the same symbol doesn't conflict.
- **Integration test** for the seeder: against a fixture ZIP, full pipeline to Postgres, assert row counts.
- **Integration test** for `MarketTickScheduler`: mock time, run one tick, assert one `Trade` row appears in the DB.
- **End-to-end test**: seeded 90 days of BTCUSDT, run the backtest, assert it produces a result with non-zero trade count and valid metrics (no NaN Sharpe).
- **Regression test for old endpoints**: deprecated endpoints still respond with 200 and the `X-Deprecated` header — so we don't accidentally break the `IntentParserService` chat path.

Code coverage target: aim for 40% overall by end of this workstream, up from ~1% today. Focus on `TradeRiskEngine`, `MarketTickScheduler`, the ML client, the seeder, and the meta-labeler.

---

## 8. Risks and Mitigations

1. **Binance-vs-Delta price divergence on stressed days.** On liquidity events, Delta can diverge from Binance by 50–200 bps for minutes. Backtests on Binance data will therefore look better than live performance. **Mitigation:** once Delta's native candle history is pulled (via existing Delta API client), compute the Binance-Delta basis distribution, inject that noise as additional slippage in the backtest. Tracked as a follow-up; not blocking.
2. **Model A depends entirely on the primary strategies being sound.** If the rules-based strategies generate random signals, the meta-labeler can only learn "all signals are bad." **Mitigation:** validate primary strategies in isolation first — a backtest of `MomentumStrategy` on 12 months of seeded data with no ML filter must be at least marginally positive (Sharpe > 0.5) before the meta-labeler is trained on it.
3. **Order-flow model falls back to weak features without L2 data.** The fallback feature set is documented to retain ~70% of the edge. If that's not enough, **we skip model B** and use only model A — a single good filter beats two weak ones. Tracked as a decision gate: train model B, measure IC, keep only if IC > 0.05 on walk-forward.
4. **Timestamp drift between ml-service and Java.** If Java is UTC and Python reads CSVs as local time, features and labels misalign by hours. **Mitigation:** all services use UTC internally; assert UTC in the seeder and in the feature pipeline with a tiny startup check.
5. **Weekly retrain silently breaks due to data gaps.** If `market_data` has a 3-day hole, the retrain can produce a garbage model. **Mitigation:** retrain refuses to save unless (a) data coverage in the window is >98%, (b) out-of-sample IC > 0.02, (c) log-loss improves.
6. **Slippage underestimated at $500 live.** Not an issue in paper trading, but noted here so we don't forget. Revisit before live money.

---

## 9. Open Questions (to resolve during implementation)

- Exact `tp_pct` / `sl_pct` grid for triple-barrier labeling — depends on the symbol's realized volatility. Start with `tp = 2 * ATR, sl = 1.5 * ATR, max_bars = 24` on 15m, tune on validation.
- Coinalyze free-tier API key — user to create an account and populate a config entry, or we skip OI history beyond Binance's 30-day rolling window.
- Whether to track rotational altcoin (the third pair mentioned in the user's trading profile) in the seed from day one. Default: no, keep seed focused on BTC/ETH; add the altcoin only after the main pipeline is stable.

---

## 10. Rollout Plan

Sequential, each step independently testable and revertible:

1. Flyway `V22` migration (lowest risk; empty table today).
2. Python seeder + one-shot seed of BTCUSDT/ETHUSDT 2020–present.
3. Java gap-filler daily job.
4. Fix `feature_engine.py` look-ahead bias.
5. Implement triple-barrier labeler + meta-labeler + `/predict-meta` endpoint.
6. Implement order-flow model + `/predict-flow` endpoint (measure IC; abandon if < 0.05).
7. Consolidate to `MultiTimeFrameBacktestEngine`, delete daily engine, remove synthetic fallback.
8. Wire `ExecutionModeRouter` → `PaperBrokerAdapter`, implement `MarketTickScheduler`.
9. Integrate meta-filter into `TradeRiskEngine`.
10. Enable Telegram fill alerts.
11. Run paper trading for 4+ weeks; gate live-money work on the validation criteria in §4.7.

Each step gets its own PR and is mergeable on its own. The PCR options-gate design work resumes after step 11 passes.

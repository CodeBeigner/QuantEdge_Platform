# Layer Feasibility Evaluation — Infrastructure & Performance Modules

**Date:** 2026-05-31
**Project:** QuantEdge Platform

---

## ═══════════════════════════════════════════════════════
## LAYER 1 — INFRASTRUCTURE LATENCY PROFILER
## ═══════════════════════════════════════════════════════

### Existing State
- **No latency measurement exists anywhere in the project.** No RTT tracking, no ping infrastructure, no exchange heartbeat monitoring.
- Kill switch via file-drop + API exists (`services/risk/kill_switch.py`), but only checks flag existence — no round-trip timing.
- Java backend already has WebSocket (STOMP/SockJS) for price streaming (`/ws`). Delta Exchange client has WebSocket support (`service/delta/DeltaExchangeClient.java`).
- Frontend WebSocket hook (`useWebSocket.ts`) already measures connection state (connected/disconnected) but does not measure latency.

### Dependencies

#### A. Python Network Latency Measurement

| Option | Already in project | Cost | Assessment |
|--------|-------------------|------|------------|
| **stdlib `time` + `asyncio`** (Recommended) | YES — stdlib | Free | Measure RTT via simple HTTP GET to exchange health endpoints. Zero dependencies. Sufficient for fetch-level RTT measurement. |
| **`ping3` / `icmplib`** | NO | Free | Raw ICMP ping. More accurate at network layer, but requires root on macOS/Linux. Not suitable for application-layer RTT (bypasses load balancers, proxies). Overkill for what we need. |
| **`aiohttp`** (timing layer) | NO | Free | Async HTTP client with built-in timing. Already have `httpx` in ml-service. Better API than urllib for timing, but adds dependency weight. |

**Recommendation:** `stdlib time + urllib` (already the project standard per CLAUDE.md). Measure broker health-endpoint RTT in milliseconds. No new dependency.

#### B. Broker/Exchange Heartbeat RTT

| Option | Already in project | Cost | Assessment |
|--------|-------------------|------|------------|
| **Exchange REST health endpoints** (Recommended) | Partially — Delta Exchange client exists | Free | Poll `/health` or `/ping` on each broker's REST API. Works for Alpaca, Delta Exchange, Binance. Adds no infrastructure. |
| **WebSocket ping/pong frames** | Partially — STOMP WebSocket exists | Free | Most accurate (frame-level RTT). More complex to implement, broker-dependent. |
| **External monitoring service** (DataDog, New Relic) | NO | **$15-100+/mo** | Professional but overkill for a personal tool. Large dependency. Not recommended. |

**Recommendation:** Start with REST health endpoint polling. Add WebSocket frame-level RTT later if sub-ms accuracy needed.

#### C. VPS / Co-location (External Service — Manual Provisioning)

| Provider | Cost Tier | Latency | Assessment |
|----------|-----------|---------|------------|
| **CME Co-location** | **$500-3,000+/mo** | <1ms | Required for futures HFT. Not viable for personal tool. |
| **AWS us-east-1** | $50-200/mo | 5-50ms to exchanges | Reasonable for equities, forex. |
| **Equinix NY4/CH1** | $500-2,000+/mo | <5ms | Institutional-grade. Not for personal tool. |
| **Local M2 Pro** | Free | Varies | Current setup. WAN latency 20-200ms to exchanges. |

**Cost flag: All VPS/co-location are paid external services.** User must provision manually. The latency profiler should measure current latency and display it — not auto-provision anything.

### Feasibility Assessment

| Factor | Score | Notes |
|--------|-------|-------|
| Implementation complexity | LOW | Timer + HTTP GET + store = maybe 50 lines of Python |
| New dependencies needed | NONE | stdlib urllib + time sufficient |
| Integration risk | LOW | Drops into existing `services/` pattern |
| Financial cost | FREE | No paid dependencies |
| **VERDICT** | **GO — build now** | |

### What Gets Built (If Confirmed)

```
services/latency/
├── __init__.py
├── profiler.py      # measure_rtt(host), latency_report()
├── thresholds.py    # asset_class -> max_rtt mapping
└── service.py       # FastAPI: GET /infrastructure/latency-report
```

- Tags strategies with asset class (`futures`, `forex`, `equities`, `crypto`) at registration
- Enforces: futures RTT >1ms = warn, forex RTT >5ms = warn, equities = cloud acceptable
- `GET /infrastructure/latency-report` returns per-broker RTT, warnings list

---

## ═══════════════════════════════════════════════════════
## LAYER 2 — STRATEGY CLASSIFIER + REGIME GUARD
## ═══════════════════════════════════════════════════════

### Existing State

**Strategy types already exist** in Java `ModelType.java`:
```
MOMENTUM, VOLATILITY, MACRO, CORRELATION, REGIME,
TREND_CONTINUATION, MEAN_REVERSION, FUNDING_SENTIMENT
```

Frontend TypeScript mirrors these (`StrategyModelType`). The user wants to add: `STAT_ARB`, `HFT`. These are new.

**No asset class enum** exists. Asset class is referenced implicitly (crypto in LiveTicker, futures in MLFeatureCollector, equities in agent prompts). Needs formalization.

**No strategy suppression** exists. Kill switch is global only. No per-strategy circuit breaker. No signal suppression logic. The user wants per-strategy rule enforcement (e.g., MEAN_REVERSION auto-suppresses in trending regimes, STAT_ARB requires live correlation >0.60).

**HMM regime detection already exists** in `services/stress_test/hmm_layer.py` (built in this session). 24 tests. Regimes: bear, sideways, bull. Transition matrix exposed. This can be injected as a shared service.

### Dependencies

#### A. Strategy Classification / Style Enum

| Option | Already in project | Cost | Assessment |
|--------|-------------------|------|------------|
| **Python Enum extending existing Java ModelType** (Recommended) | Partially — Java enum exists | Free | Mirror the 8 existing types + add STAT_ARB, HFT. Python dataclass with strategy style + asset class. Zero new deps. |
| **YAML/JSON config file** | NO | Free | External config for strategy types. More flexible but adds file I/O. Unnecessary. |
| **Database table** | NO — would need migration | Free | Proper but overkill for an enum. |

**Recommendation:** Python enum + dataclass. Mirror existing types, add the two new ones. No new dependency.

#### B. Regime Classifier (Shared Service)

| Option | Already in project | Cost | Assessment |
|--------|-------------------|------|------------|
| **Reuse `services/stress_test/hmm_layer.py`** (Recommended) | YES — built in this session | Free | GaussianHMM, 24 tests, bear/sideways/bull + high-volatility. Already integrated. Just expose the regime labels per signal. |
| **hmmlearn directly** | Partially — imported but not in requirements.txt | Free | Same library. Using existing wrapper adds convenience: regime names, transition matrix, performance analysis already baked in. |
| **ruptures** (change point detection) | NO | Free | Simpler, non-probabilistic. Handles regime shifts but doesn't model transition probabilities. Less useful for Monte Carlo path simulation. Lighter weight. |
| **Quandl/Nasdaq Data Link regime feeds** | NO | **$50-500/mo** | Pre-labeled regimes from data vendor. High cost, external dependency. Not recommended for personal tool. |

**Recommendation:** Reuse existing `hmm_layer.py`. Add a "high_volatility" regime (V > 2x normal) as a 4th regime option. No new dependency. Must add hmmlearn to requirements.txt.

#### C. Survivorship Bias Filter

| Option | Already in project | Cost | Assessment |
|--------|-------------------|------|------------|
| **Free yfinance + documentation** (Recommended for now) | YES — yfinance adapter exists | Free | yfinance includes delisted tickers. Validates ticker existence at scan time. No bias correction — flag limitation in docs. |
| **Norgate Data** | NO | **$27/mo** | Best survivorship-bias-free EOD data for US equities. But paid, external dependency. |
| **Polygon.io** | NO | **$29/mo (Basic)** | Good intraday + historical. Paid. Adds API dependency. |
| **SimFin** | NO | Free tier available | Limited to US equities, ~3,000 ticker coverage. Free tier has rate limits. |

**Recommendation:** Use yfinance (already integrated) + document the limitation. Flag TREND_FOLLOWING strategies at config time: "Survivorship bias correction not active — backtests may be upward-biased for trend strategies." If user wants paid correction later, the Norgate adapter is a 1-day integration.

### Feasibility Assessment

| Factor | Score | Notes |
|--------|-------|-------|
| Implementation complexity | MEDIUM | Strategy rules engine, signal suppression, mandatory regime attachment |
| New dependencies needed | NONE | Reuses hmm_layer.py. Just need to add hmmlearn to requirements.txt |
| Integration risk | MEDIUM | Per-strategy rules need to intercept the signal pipeline (A → B → C → D) |
| Financial cost | FREE | No paid dependencies (survivorship bias documented as limitation) |
| **VERDICT** | **GO — build now** | |

### What Gets Built (If Confirmed)

```
services/classifier/
├── __init__.py
├── strategy_style.py   # StrategyStyle enum (10 types), AssetClass enum
├── regime_guard.py     # Shared regime service wrapping hmm_layer.py
├── signal_policy.py    # Per-strategy enforcement rules
└── service.py          # FastAPI: POST /classifier/tag-strategy, GET /classifier/regime
```

Strategy rules per type:
- **TREND_FOLLOWING:** Survivorship bias filter enforcement at config
- **MEAN_REVERSION:** Mandatory regime attachment, auto-suppress in trending regimes, 20-40% capital soft cap
- **STAT_ARB:** Two correlated assets required at registration, live correlation >0.75 warn, <0.60 suppress
- **HFT:** Registration warning + explicit override acknowledgment

---

## ═══════════════════════════════════════════════════════
## LAYER 3 — CALMAR RATIO ENGINE
## ═══════════════════════════════════════════════════════

### Existing State

**No Calmar ratio anywhere.** The project computes:
- Sharpe ratio (Java BacktestEngine, PaperMetricsService, Python Monte Carlo, ML model.py)
- Max drawdown (Java + Python, multiple places)
- Win rate (Java backtest + paper metrics)
- VaR/CVaR (Java RiskEngine + Python Monte Carlo)
- Kelly sizing (Python `services/risk/kelly.py`)

**No Sortino, no Calmar, no Omega, no Information Ratio.**

**No `empyrical`, `quantstats`, or `pyfolio` in the project.**

Annualized return is not computed as a standalone function — Sharpe's numerator uses it but doesn't expose it separately. Rolling window calculations use pandas `.rolling()` implicitly in the Java backtest engines but not as a Python utility.

### Dependencies

#### A. Performance Metrics Library

| Option | Already in project | Bundle size | Assessment |
|--------|-------------------|-------------|------------|
| **numpy + scipy (hand-code)** (Recommended for in-service) | YES — already in requirements.txt | 0 new | Calmar = annualized_return / abs(max_drawdown). Both inputs already available. 10 lines of code. Zero risk. |
| **empyrical** | NO | ~500KB | Lightweight. Provides Calmar, Sortino, Omega, annual_return, max_drawdown, etc. Clean API. One new pure-Python dep. |
| **quantstats** | NO | ~2MB + matplotlib | Full tear sheets, HTML reports. Heavy for in-service use. Better for offline reporting. |
| **pyfolio** | NO | Heavy + zipline dependency | Deprecated (Quantopian). **DO NOT USE.** |

**Recommendation:** Hand-code Calmar using existing numpy/scipy for in-service calculation. **Optionally** add `empyrical` if Sortino/Omega/Information Ratio are wanted later — but NOT now. Calmar = `annualized_return / abs(max_drawdown)` where:
```
annualized_return = (final_value / initial_value) ** (252 / n_days) - 1
max_drawdown = min(equity_curve / running_max - 1)
```

#### B. Rolling Window Calculation

| Option | Already in project | Assessment |
|--------|-------------------|------------|
| **pandas .rolling()** (Recommended) | YES | Already in requirements.txt. `df['equity'].rolling(252).apply(calmar_fn)` — clean, one-liner. |
| numpy stride tricks | YES | Faster but harder to read. Not needed at this data scale. |

**Recommendation:** Pandas rolling. No new dependency.

#### C. Report Generation

| Option | Already in project | Assessment |
|--------|-------------------|------------|
| **JSON response to frontend** (Recommended) | YES | Frontend renders via Recharts. Already the pattern in `/api/risk/*` endpoints. |
| quantstats HTML reports | NO | Nice for offline, heavy for in-service. Optional Phase 2. |
| matplotlib charts | NO — not in requirements | Would need matplotlib. Not needed — Recharts handles frontend viz. |

**Recommendation:** JSON response. Frontend renders Calmar + drawdown + return via Recharts `AreaChart` / `LineChart` components already present.

### Feasibility Assessment

| Factor | Score | Notes |
|--------|-------|-------|
| Implementation complexity | **LOW** | Calmar is 10 lines of math. 3 levels (strategy, asset, portfolio). |
| New dependencies needed | NONE | Pure numpy + pandas. |
| Integration risk | LOW | Drops into existing `services/` pattern. Already have daily returns + drawdown as inputs. |
| Financial cost | FREE | No paid dependencies. |
| **VERDICT** | **GO — build now** | |

### What Gets Built (If Confirmed)

```
services/calmar/
├── __init__.py
├── engine.py         # compute_calmar(), calmar_benchmark(), rolling_calmar()
├── diagnostics.py    # trailing_stop_simulation(), regime_filter_simulation()
└── service.py        # FastAPI: GET /calmar/{scope}?strategy=...&asset=...&window=12m
```

Benchmarks:
- Calmar < 2.0 → "Underperforming — review or retire"
- Calmar 2–3 → "Acceptable"
- Calmar 3–5 → "Good — eligible for capital increase"
- Calmar ≥ 5 → "Elite — flag for leverage / prop firm deployment"

Two diagnostic simulations (non-destructive "what-if"):
1. **Trailing Stop Simulation:** Replay trades with ATR-based trailing stop (default 1.5x) → projected Calmar delta
2. **Regime Filter Simulation:** Replay with signals suppressed during adverse regimes → projected Calmar delta

---

## ═══════════════════════════════════════════════════════
## LAYER 4 — PORTFOLIO STACKING ENGINE
## ═══════════════════════════════════════════════════════

### Existing State

**Portfolio-level code exists in Java only** (not in Python `services/`):
- `BacktestEngine`: Portfolio equity curve from multi-asset backtests
- `Portfolio` entity/type: Frontend has `Portfolio` TypeScript interface with `totalValue`, `positions`, `dailyPnl`
- `optimizer.py` (ml-service): Markowitz, Ledoit-Wolf, risk parity — hand-coded, not using PyPortfolioOpt

**Correlation code exists at pairwise level only:**
- Java `CorrelationStrategy`: Pairwise 60-day Pearson between two price series
- Java `MathUtils.calculateCorrelation()`: Pearson correlation helper
- Python `ml-service/main.py`: Spearman rank correlation for IC

**No multi-asset correlation matrix. No n×n matrix computation.**

**No correlation heatmap in frontend.** Recharts is the charting library — supports `ScatterChart`, `AreaChart`, `LineChart`, `BarChart` but has no native heatmap component. Would need custom SVG or a lightweight heatmap library.

### Dependencies

#### A. Portfolio Optimization Library

| Option | Already in project | Bundle size | Assessment |
|--------|-------------------|-------------|------------|
| **scipy.optimize** (Recommended for core) | YES — in requirements.txt | 0 new | Already used in `optimizer.py` for Markowitz via `minimize()`. Sufficient for Sharpe-maximization, weight constraints. No new dep. |
| **PyPortfolioOpt** | NO | ~2MB | Finance-specific wrapper around scipy/cvxpy. Clean API: `EfficientFrontier`, HRP, Black-Litterman, CLA. Better error messages, output formatting, plotting. **Recommended if user wants advanced methods beyond Markowitz.** |
| **Riskfolio-Lib** | NO | ~5MB | Very comprehensive (risk parity, factor models, CVaR optimization). Overkill for a personal tool. Heavy. Not recommended for Phase 1. |
| **cvxpy** (standalone) | NO | ~3MB | Convex optimization engine. PyPortfolioOpt wraps it. Raw cvxpy is more powerful but harder to use. |

**Recommendation:** **Phase 1: scipy.optimize** (already in project). The portfolio stacking problem is: maximize Sharpe (already computed), minimize pairwise correlation, respect allocation caps. This is a constrained optimization solvable with `scipy.optimize.minimize(method='SLSQP')` which is already used in `optimizer.py`.

**Phase 2 (optional): PyPortfolioOpt** if user wants HRP (Hierarchical Risk Parity) or Black-Litterman. Add as separate dependency only when needed.

#### B. Correlation Heatmap (Frontend)

| Option | Already in project | Bundle size | Assessment |
|--------|-------------------|-------------|------------|
| **Custom SVG with Recharts primitives** (Recommended) | YES — Recharts v3.8 | 0 new | Recharts has `Rectangle`, `Cell`, `Label` components. A correlation heatmap is a grid of colored rectangles. Buildable with Recharts + inline SVG. Zero new deps. Cleanest integration. |
| **@nivo/heatmap** | NO | ~150KB | Beautiful, interactive heatmaps. React-native. Adds one dependency. Good if heatmap is core UX (it is — user wants it as a dashboard hero). |
| **D3.js** | NO | ~250KB | Maximum flexibility, high learning curve. Overkill for a heatmap. |
| **ApexCharts** | NO | ~500KB | Has heatmap chart type. Adds a full charting library when Recharts is already present. Not recommended. |

**Recommendation:** **@nivo/heatmap** for a polished, interactive heatmap out of the box. One lightweight dependency. If the dependency is a concern, custom SVG with Recharts works too.

#### C. Equity Curve Visualization

| Option | Already in project | Assessment |
|--------|-------------------|------------|
| **Recharts (already present)** (Recommended) | YES | `DashboardPage.tsx` and `BacktestPage.tsx` already render equity curves via Recharts. Just extend to multi-line "hero + muted subordinates" pattern. No new dep. |

**Recommendation:** Recharts. Already wired.

### Feasibility Assessment

| Factor | Score | Notes |
|--------|-------|-------|
| Implementation complexity | **MEDIUM-HIGH** | Multi-dimensional optimization, correlation matrix, heatmap UX |
| New dependencies needed | **OPTIONAL** (1) | @nivo/heatmap for polished heatmap OR custom SVG |
| Integration risk | MEDIUM | Portfolio view touches all existing metrics. Must integrate with Frontend, Calmar engine, Risk engine |
| Financial cost | FREE | No paid dependencies. |
| **VERDICT** | **GO — build with scipy + @nivo/heatmap** | |

### What Gets Built (If Confirmed)

```
services/stacking/
├── __init__.py
├── correlation.py     # pair_matrix(), correlation_heatmap_data()
├── allocation.py      # stacking_optimizer() via scipy.optimize, hard cap enforcement
├── equity_curve.py    # combined + individual strategy curves
└── service.py         # FastAPI: GET /stacking/correlation, GET /stacking/allocation, POST /stacking/optimize
```

Frontend:
- Correlation heatmap via @nivo/heatmap (or Recharts custom)
- Hero + muted equity curves via Recharts LineChart
- Allocation bar chart via Recharts BarChart
- Flag pairs with correlation >0.65
- Hard cap: no strategy >15% of portfolio (configurable)

---

## ═══════════════════════════════════════════════════════
## CROSS-CUTTING DEPENDENCY SUMMARY
## ═══════════════════════════════════════════════════════

| Dependency | Used By | Status | Cost |
|-----------|---------|--------|------|
| **hmmlearn** | Layer 2 (Regime Guard) | Already in use by stress-test module (NOT in requirements.txt) | Free |
| **scipy.optimize** | Layer 4 (Stacking) | Already in requirements.txt | Free |
| **numpy + pandas** | All layers | Already in requirements.txt | Free |
| **pydantic** | All layers (API validation) | Already in requirements.txt | Free |
| **fastapi + uvicorn** | All layers (service endpoints) | Already in requirements.txt | Free |
| **Recharts** | Layer 3, 4 (Dashboard) | Already in frontend package.json | Free |
| **@nivo/heatmap** (OPTIONAL) | Layer 4 (Correlation heatmap) | NOT in project | Free |
| **empyrical** (NOT recommended now) | Layer 3 (Performance metrics) | NOT in project | Free |
| **VPS/Co-location** | Layer 1 (Latency) | External paid service | $50-3,000+/mo |
| **Survivorship-free data** (Norgate/Polygon) | Layer 2 (TREND_FOLLOWING) | External paid service | $27-29/mo |

### Dependencies to Add (all free)
1. **hmmlearn** → `requirements.txt` (already used, not declared)
2. **@nivo/heatmap** → `frontend/package.json` (optional, for Layer 4 heatmap)

### Paid Services (user must provision manually, costs flagged)
1. **VPS/Co-location** ($50-3,000/mo) — Layer 1 only if sub-10ms latency required
2. **Survivorship-free data** ($27-29/mo) — Layer 2 only if bias correction needed

---

## ═══════════════════════════════════════════════════════
## RECOMMENDED BUILD ORDER
## ═══════════════════════════════════════════════════════

| Priority | Layer | Reason |
|----------|-------|--------|
| **1** | Layer 3 — Calmar Engine | Lowest risk, zero new deps, highest visibility (hero metric on every view). Builds on existing Sharpe + drawdown. |
| **2** | Layer 2 — Strategy Classifier | Reuses existing hmm_layer.py. Adds per-strategy rules enforcement. Foundation for signal suppression. |
| **3** | Layer 1 — Latency Profiler | Simplest implementation (~50 lines). Zero new deps. Pulls broker health RTT. |
| **4** | Layer 4 — Portfolio Stacking | Most complex. Depends on Calmar for metrics display, on correlation infrastructure. Build last when all data is flowing. |

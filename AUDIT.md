# QuantEdge Platform — Codebase Audit

**Date:** 2026-05-31  
**Purpose:** Comprehensive audit mapping every module to its role in the upgraded 4-pillar architecture: Market Intelligence → Research & Analysis → Decision & Risk → Learning & Observability.

---

## 1. Tech Stack Summary

| Layer | Current | Future |
|-------|---------|--------|
| **Frontend** | React 19, TypeScript 5.9, Vite 8, Tailwind CSS 4, Zustand 5 | Same stack. Add Vitest for tests. Consolidate 15 pages into 4-pillar dashboard. |
| **Backend (API)** | Java 21, Spring Boot 3.5, PostgreSQL 15, Redis 7 | Retain for user/auth, firm profile, broker credential storage. Replace strategy/agent execution with Python services. |
| **ML/Inference** | Python FastAPI, XGBoost, LSTM/PyTorch, scikit-learn | Kronos (autoregressive Transformer, MPS-accelerated), DeepSeek V4 Pro (LLM reasoning), FastAPI. |
| **Market Data** | Binance REST/WS, Yahoo Finance, FRED | Generalize to multi-provider adapter: Binance/CCXT (crypto), yfinance (equities), Polymarket/Kalshi (Phase 2). |
| **Brokers** | Paper (built-in), Delta Exchange, Alpaca (stub) | Abstract ExecutionProvider: Alpaca (equities), CCXT (crypto), Polymarket CLOB (Phase 2), paper (default). |
| **Infrastructure** | Docker Compose, Prometheus, Grafana, GitHub Actions | Same. Add Kronos service container, extend monitoring. |
| **DB Migrations** | 23 Flyway migrations (PostgreSQL) | Keep existing migrations. Add Trade Ledger, Knowledge Base, Agent Calibration tables. |
| **LLM** | Anthropic Claude | Provider-agnostic layer: DeepSeek V4 Pro (primary), Claude (fallback). |

---

## 2. Module Classification

### 2.1 KEEP — Solid, well-structured, already doing its job

These modules survive the upgrade intact or with minimal API-surface changes.

#### Backend (Java)

| Module | Path | Reason |
|--------|------|--------|
| **Backtest Engines** | `engine/BacktestEngine.java`, `engine/MultiTimeFrameBacktestEngine.java` | Deterministic, well-tested strategy replay. Port to Python as standalone service, keep Java version for reference. |
| **Risk Engine** | `service/risk/TradeRiskEngine.java` | 8 hard checks (position limits, exposure, leverage, liquidity, concentration, correlation, drawdown, meta-filter veto). Foundation for Pillar C extensions. |
| **Slippage/Fee Models** | `engine/trading/SlippageModel.java`, `engine/trading/FeeModel.java` | Deterministic, tested. Port to Python for Pillar C real-time use. |
| **Candle/Swing Utilities** | `engine/model/`, `engine/util/SwingDetector.java`, `engine/util/MathUtils.java` | Generic OHLCV primitives. Keep in both Java and Python. |
| **Meta-Labeler Integration** | `service/ml/MetaFilterGate.java`, `service/ml/MetaRetrainScheduler.java` | Triple-barrier meta-labeling infrastructure. Keep for ensemble prediction layer. |
| **Paper Trading** | `service/paper/PaperTradePersister.java`, `service/paper/PaperMetricsService.java`, `service/paper/ExecutionModeRouter.java` | Working paper engine. Keep as the default ExecutionProvider implementation. |
| **Security (JWT)** | `security/` | Auth infrastructure. Keep. |
| **Flyway Migrations** | `db/migration/V1–V23.sql` | 23 well-structured schema migrations. Keep. Add new migrations for Pillar D tables. |
| **Entity/Repository Layer** | `model/entity/`, `repository/` | JPA entities and Spring Data repositories. Keep for user/auth/firm/profile/broker-credentials. |
| **Delta Exchange Client** | `service/delta/DeltaExchangeClient.java` | Working. Keep as one of the CCXT-backed execution providers. |
| **Binance Data Clients** | `client/BinanceHistoricalClient.java`, `client/BinanceMarketDataClient.java` | Working. Keep as crypto data source in the Market Intelligence Layer. |

#### ML Service (Python)

| Module | Path | Reason |
|--------|------|--------|
| **Feature Engine** | `feature_engine.py` | Technical indicator computation. Keep as input to Kronos tokenizer and meta-labeler. |
| **Triple-Barrier Labeler** | `labelers/triple_barrier.py` | Well-implemented financial labeling. Keep for Pillar A signal generation. |
| **Meta-Labeler** | `ml_models/meta_labeler.py` | XGBoost-based primary signal validator. Keep as one input to Prediction Aggregator. |
| **Order Flow Model** | `ml_models/order_flow.py` | Market microstructure model. Keep as input to Prediction Aggregator. |
| **Model Registry** | `ml_models/registry.py` | Versioned model persistence. Keep for Kronos model checkpoint management. |
| **Purged K-Fold** | `ml_models/purged_kfold.py` | Proper financial cross-validation. Keep for Kronos fine-tuning and calibration. |
| **Portfolio Optimizer** | `optimizer.py` | Markowitz, Ledoit-Wolf, risk parity. Keep for Pillar D performance analysis. |
| **Feature Enrichment** | `ml_models/feature_enrichment.py` | Derivative feature generation. Keep. |
| **Ingest Pipeline** | `ingest/` | Binance Vision data download. Keep for backtesting data preparation. |

#### Frontend

| Module | Path | Reason |
|--------|------|--------|
| **Layout Components** | `components/layout/Sidebar.tsx`, `TopBar.tsx`, `LiveTicker.tsx` | Working. Keep with cleanup of silent error handlers (done). |
| **UI Primitives** | `components/ui/` | MaterialIcon, PageHeader, KpiCard. Keep. |
| **API Client** | `services/api.ts` | Axios-based. Keep. Add new endpoints for Pillar A/B/C/D services. |
| **Delta Exchange Client** | `services/deltaExchange.ts` | Working. Keep. Add security note about localStorage credentials. |
| **Auth Store** | `stores/authStore.ts` | Working. Keep. |
| **WebSocket Hook** | `hooks/useWebSocket.ts` | STOMP/SockJS. Keep with cleanup (done). |

#### Infrastructure

| Module | Path | Reason |
|--------|------|--------|
| **Docker Compose (prod)** | `docker-compose.prod.yml` | Production-ready. Extend with Kronos container. |
| **Prometheus/Grafana** | `monitoring/` | Working monitoring stack. Extend metrics for new services. |
| **GitHub Actions CI** | `.github/workflows/test.yml` | Working. Extend with Python tests, frontend tests. |

---

### 2.2 REFACTOR — Right idea, wrong implementation

These modules have the correct purpose but need restructuring to fit the 4-pillar architecture.

| Module | Current State | Target State |
|--------|--------------|--------------|
| **ML Service** (`ml-service/main.py`) | Deprecated XGBoost/LSTM endpoints, bare except blocks, wildcard CORS. 648-line monolithic FastAPI app. | Split into: `kronos_service/` (forecasting), `prediction_service/` (ensemble), `research_service/` (agent orchestration). Each independently deployable. |
| **Frontend Pages** (15 pages) | Disconnected pages for Dashboard, Trade, Strategies, Backtest, Orders, Risk, Settings, Auth, Paper, Firm, Market, ML, AI Intel, Agents, Alerts, Trade Log. No tests. | Consolidate into 4 pillar-aligned views: **Dashboard** (portfolio + signals), **Research** (agent reports), **Trading** (execution + orders), **Analytics** (calibration + backtest). Add Vitest test suite. |
| **Strategy Engine** | 3 hardcoded multi-TF strategies (TrendContinuation, MeanReversion, FundingSentiment) running on a fixed 4H/1H/15M schedule. | Decouple strategies from signal generation path. Keep as a backtest-compatible strategy library. Kronos + ensemble prediction becomes the live signal pathway. |
| **Market Data Pipeline** | Binance-specific REST + WebSocket. Hardcoded symbols, timeframes. | Generalize to `MarketDataProvider` abstract interface. Implementations: `BinanceProvider`, `YFinanceProvider`, `CCXTProvider`. Feed unified Market Intelligence Layer. |
| **Telegram Bot** | `TelegramBotService` + `TelegramCommandHandler` with 4 unimplemented TODO stubs. | Rebuild as a notification channel in Pillar D (not a command handler). Alerts for: new signals, risk breaches, daily P&L summary. configurable on/off. |

---

### 2.3 REPLACE — Obsolete approach; better one exists

| Module | Current | Replacement | Reason |
|--------|---------|------------|--------|
| **XGBoost/LSTM Signal Generation** | `model.py` (SignalModel, LSTMSignalModel) — single-model point predictions for BUY/SELL/HOLD. | **Kronos** price-path forecasting → ensemble probability estimation. | Kronos produces full distributional forecasts (probabilistic paths), not point predictions. This is fundamentally superior for Kelly sizing and risk calibration. |
| **Agent Scheduler** | Java cron-based `AgentSchedulerService`. Agents execute linked strategies on fixed schedules. | **Python event-driven orchestration**: `Scanner → Research → Prediction → Risk/Execution` pipeline. Each agent independently runnable, testable, composable. | Java cron is not suited for the dynamic, data-dependent agent pipeline. Event-driven orchestration allows agents to fire based on market events, not wall-clock time. |
| **Claude Agent Integration** | `engine/agent/` — hardcoded Anthropic Claude prompts and execution. `IntentParserService` for chat-based command parsing. | **Provider-agnostic LLM layer** (`llm/providers/`): `DeepSeekProvider`, `ClaudeProvider`, with OpenAI-compatible API. Per-agent token budgets, usage tracking. | DeepSeek V4 Pro is ~10x cheaper than Claude with comparable reasoning quality. The LLM layer should be swappable without changing agent logic. |
| **Legacy/deprecated ML endpoints** | 6 deprecated endpoints kept "to avoid 404s": `/train/{symbol}`, `/predict/{symbol}`, `/train-lstm/{symbol}`, `/predict-lstm/{symbol}`, `/predict-ensemble/{symbol}`, `/ic/{symbol}`. | `/forecast/{symbol}` (Kronos), `/batch-forecast`, `/predict-meta/{symbol}` (triple-barrier). | Remove entirely. Deprecated endpoints will error at runtime anyway (target column doesn't exist). Replace with Kronos forecasting endpoints. |
| **`IntentParserService`** | Chat-based command parsing that routes user messages to deprecated endpoints. | Remove. Replace with direct agent invocation through the API and dashboard. | Chat-based trading is cute but not production-grade. The dashboard + API are the primary interfaces. |

---

### 2.4 DELETE — Dead code, doesn't belong

| Module | Path | Reason |
|--------|------|--------|
| **Deprecated ML Endpoints** | `ml-service/main.py` lines 60-81, handlers at lines 176-530 | 6 endpoints that will error at runtime. Remove entirely. |
| **Hardcoded Secrets** | `application.yml` — password, JWT secret, encryption key defaults | Already fixed in this session's security hardening pass. |
| **TelegramCommandHandler TODOs** | `TelegramCommandHandler.java` — 4 unimplemented stubs | Replace with notification-only Telegram channel (Pillar D). The command-handler pattern is wrong for an autonomous system. |
| **Stale BUG Comments** | `StrategyService.java`, `AgentSchedulerService.java`, `BacktestEngine.java`, `BacktestResult.java` | Already cleaned up in this session. |
| **`test_sig.py`** | `frontend/test_sig.py` | Misplaced HMAC signature test. Move to proper test directory or delete. |
| **`show-sql: true`** | `application.yml` | Already fixed (set to `false`). |
| **`include-message: always`** | `application.yml` | Already fixed (set to `never`). |
| **Wildcard CORS** | `ml-service/main.py` `allow_origins=["*"]` | Already fixed (uses `CORS_ORIGINS` env var, defaults to localhost:3000). |

---

### 2.5 MISSING — Gaps for the 4-pillar vision

These are entirely new modules that must be built. Listed in implementation priority order.

#### Pillar C: Decision & Risk (built first — safety net)

| Module | Description | Priority |
|--------|-------------|----------|
| **Risk & Sizing Engine** | Kelly Criterion sizing (`f* = (p*b - q) / b`, default quarter-Kelly). VaR check (95% confidence). Max drawdown gate (8% hard stop). Daily loss limit. Position limit (≤5% NAV). Exposure check. All deterministic Python — no LLM rules. | **CRITICAL** |
| **Kill Switch** | File-drop trigger (`STOP.flag`) AND API endpoint. Immediately halts all order generation. On live mode: sends cancel-all-orders to every connected broker. Periodic health check verifies switch is functional. | **CRITICAL** |
| **Execution Module** | Abstract `ExecutionProvider` interface. Concrete: `AlpacaProvider` (equities), `CCXTProvider` (crypto), `PaperProvider` (default). `LIVE_TRADING=true` env gate + secondary confirmation flag. Slippage guard: abort if price moves >2% between signal and fill. Position reconciliation: compare broker state vs internal ledger. | **HIGH** |

#### Pillar A: Market Intelligence Layer

| Module | Description | Priority |
|--------|-------------|----------|
| **Kronos Integration Service** | `services/kronos/` — wraps KronosPredictor. Endpoints: `/forecast` (single symbol), `/batch-forecast` (portfolio). Accepts OHLCV DataFrames, returns probabilistic price paths (median + percentile bands). Caches tokenizer weights. Default: Kronos-small (24.7M params, 512 context). Configurable: Kronos-base. MPS-accelerated on Apple Silicon. | **HIGH** |
| **Market Scanner** | `agents/scanner/` — scans user-defined watchlists. Filters: minimum liquidity, unusual volume (>2σ vs 7-day avg), spread anomalies, catalyst events. Output: ranked `OpportunityList` with structured payloads for downstream agents. Configurable schedule. | **HIGH** |
| **Multi-Provider Data Adapter** | Abstract `MarketDataProvider`. Implementations: `BinanceProvider`, `YFinanceProvider`, `CCXTProvider`. Polymarket/Kalshi providers (Phase 2). Unified OHLCV format consumed by Kronos tokenizer. | **MEDIUM** |

#### Pillar B: Research & Analysis Layer

| Module | Description | Priority |
|--------|-------------|----------|
| **Fundamental Analyst Agent** | `agents/fundamental/` — auto-builds DCF (3-statement, 10-12% WACC range), comps table (EV/EBITDA, EV/Revenue, P/E), LBO skeleton. Pulls financials from SEC EDGAR, Yahoo Finance. Output: `FundamentalReport` with bear/base/bull valuation ranges, implied price vs current, 52-week range context. | **HIGH** |
| **Earnings/Catalyst Agent** | `agents/earnings/` — tracks upcoming earnings, Fed decisions, macro releases. Post-earnings: ingests transcript/press release via DeepSeek, extracts beats/misses, guidance changes, management tone shifts. Output: `EarningsSignal` (upgrade/downgrade/neutral). | **HIGH** |
| **Sentiment & News Agent** | `agents/sentiment/` — scrapes financial news RSS, Reddit (r/investing, r/stocks, r/algotrading). NLP sentiment scoring (bullish/neutral/bearish, confidence). Cross-references narrative against current price. Content sanitization before LLM pass to prevent prompt injection. | **MEDIUM** |
| **Sector & Macro Agent** | `agents/sector/` — sector rotation analysis, breadth expansion vs deterioration. Macro regime: rates environment, VIX, credit spreads. Output: `MacroRegimeReport` consumed by Prediction and Risk agents. | **MEDIUM** |

#### Pillar B (continued): Prediction Layer

| Module | Description | Priority |
|--------|-------------|----------|
| **Prediction Aggregator** | `agents/prediction/` — ensemble probability estimation. Inputs: Kronos forecast paths + FundamentalReport + EarningsSignal + SentimentSignal + MacroRegimeReport. Kronos path → implied directional probability. DeepSeek reasoning layer synthesizes all inputs. Output: `PredictionSignal` (asset, direction, probability, confidence, horizon, rationale). Brier Score tracking per agent. Phase 2: compare vs market-implied probability, calculate edge. | **HIGH** |
| **LLM Provider Layer** | `llm/providers/` — abstract `LLMProvider` interface. `DeepSeekProvider` (primary): OpenAI-compatible API. `ClaudeProvider` (fallback): Anthropic API. Per-agent daily token budget. Usage logging per call. Cost tracking. `LLM_USAGE_DAILY_BUDGET` env config. | **HIGH** |

#### Pillar D: Learning & Observability

| Module | Description | Priority |
|--------|-------------|----------|
| **Trade Ledger** | `data/ledger/` — append-only JSON `TradeRecord` log. Fields: entry price, exit price, model probability, actual outcome, P&L, time held, market conditions at entry. Versioned schema. | **MEDIUM** |
| **Post-Mortem Agent** | `agents/postmortem/` — classifies every closed trade: model error, timing error, execution error, external shock. Saves to `data/knowledge_base/`. Nightly consolidation: aggregate day's trades, update calibration metrics, flag systematic loss patterns. | **MEDIUM** |
| **Performance Dashboard** | Frontend views: real-time portfolio stats (Sharpe >2.0 target, Win Rate, Max Drawdown, Profit Factor, Brier Score per agent). Signal feed with confidence/edge scores. Kronos fan charts. Trade history with P&L breakdown. Model calibration chart (predicted vs actual over rolling windows). Paper/Live mode banner. | **MEDIUM** |

---

## 3. Architecture Before → After

### Current Architecture (Phase 4/7)
```
[Binance WS/REST] → [Java Backend] → [Strategy Engine] → [Risk Engine] → [Paper/Delta Exec]
                          ↕                    ↕
                   [React Dashboard]    [Python ML Service]
                                          (XGBoost + LSTM)
```

### Target Architecture (4-Pillar)
```
┌─────────────────────────────────────────────────────────┐
│                  PILLAR A: MARKET INTELLIGENCE            │
│  [Market Scanner] ← [Multi-Provider Data Adapter]        │
│       ↓                 (Binance/CCXT/YFinance)          │
│  [Kronos Service] → probabilistic price paths            │
└────────────────────────┬────────────────────────────────┘
                         ↓ OpportunityList + Forecasts
┌─────────────────────────────────────────────────────────┐
│              PILLAR B: RESEARCH & ANALYSIS                │
│  [Fundamental Agent] [Earnings Agent] [Sentiment Agent]  │
│  [Sector/Macro Agent]                                    │
│       ↓ structured reports                               │
│  [Prediction Aggregator] ← Kronos + Research + DeepSeek  │
│       ↓ PredictionSignal                                 │
└────────────────────────┬────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│               PILLAR C: DECISION & RISK                   │
│  [Risk & Sizing Engine]                                  │
│    - Kelly Criterion (quarter-Kelly default)             │
│    - VaR check, Drawdown gate, Daily loss limit          │
│    - Position limit, Exposure check                      │
│  [Kill Switch] (file-drop + API)                         │
│  [Execution Module] → Alpaca / CCXT / Paper              │
│       ↓ SizedOrder                                       │
└────────────────────────┬────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│            PILLAR D: LEARNING & OBSERVABILITY             │
│  [Trade Ledger] → append-only, versioned                 │
│  [Post-Mortem Agent] → classifies outcomes               │
│  [Knowledge Base] → feeds back to Scanner + Prediction   │
│  [Performance Dashboard] → Sharpe, Brier, calibration    │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Migration Strategy

The existing Java backend is NOT being deleted. It evolves:

- **Keeps**: Auth (JWT security), firm profile, broker credential storage, Flyway migrations, REST API for dashboard.
- **Loses**: Strategy execution, agent scheduling, hardcoded signal generation, Claude-specific agent code.
- **Gains**: New API routes proxying to Python services (Kronos, Research, Prediction, Execution).

The Python ML service transforms from a single monolithic FastAPI app into multiple independently deployable services under `services/` and `agents/`.

The frontend consolidates from 15 disconnected pages into 4 pillar-aligned dashboard views.

---

## 5. Risk Assessment

| Risk | Mitigation |
|------|------------|
| Kronos model not performing well on crypto timeframes | Kronos was pre-trained on 45+ exchanges including crypto. Start with Kronos-small, benchmark against existing strategies before replacing. |
| DeepSeek API rate limits or downtime | Provider-agnostic LLM layer with Claude fallback. Per-agent token budgets prevent runaway costs. |
| Java → Python migration breaks existing functionality | Incremental: build new Python services alongside Java, route via API. Cut over only when verified. Paper trading mode validates end-to-end before live. |
| Live execution bugs | `LIVE_TRADING=false` by default. Kill switch tested weekly. Paper trading validates all signal paths for 2+ weeks before live mode enabled. Quarter-Kelly sizing limits catastrophic losses even on bad signals. |
| Scope creep — trying to build everything at once | Strict 7-step delivery sequence. Each step produces working, testable output. No pillar starts until the previous one is verified. |

---

## 6. Open Questions (Owner Decisions Needed)

| # | Question | Status |
|---|----------|--------|
| 1 | Live execution in scope now? | **Resolved**: Yes, with LIVE_TRADING gate |
| 2 | GPU availability for Kronos? | **Resolved**: M2 Pro, MPS-accelerated |
| 3 | LLM provider? | **Resolved**: DeepSeek V4 Pro primary, Claude fallback |
| 4 | Prediction markets? | **Resolved**: Phase 2, toggleable |
| 5 | Deployment target? | **Resolved**: Both local + cloud |
| 6 | Acceptable monthly LLM cost ceiling? | **Open** — need a number for per-agent budget caps |
| 7 | Data feeds: yfinance, Alpaca, Polygon, paid? | **Open** — determines Fundamental Agent's data quality |
| 8 | Compliance/auditability needed? | **Open** — determines Trade Ledger schema strictness, audit trail depth |

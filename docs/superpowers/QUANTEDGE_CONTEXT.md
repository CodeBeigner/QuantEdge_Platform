# QuantEdge Context Memory

This file captures the complete state of the QuantEdge project as of the May 31, 2026 session. Load this at the start of any future session to reconstruct context.

## Project Identity

QuantEdge is an AI-native trading intelligence platform being built by the project owner. It fuses three reference sources:

1. **Kronos** (github.com/shiyu-coder/Kronos) — open-source foundation model for financial K-line sequences, Transformer-based probabilistic price path forecasting
2. **Prediction Market Trading Bot** — five-stage agentic pipeline (Scan → Research → Predict → Risk/Execute → Compound)
3. **Claude Finance Agents** (valuebyraph.com) — four retail-investor plugins: Equity Research, Earnings Reviewer, Financial Analysis, Market Researcher

## Architecture — 4 Pillars

All implemented in the May 31 session:

### Pillar C: Decision & Risk (Built First — Safety Net)
- `services/risk/config.py` — RiskConfig dataclass (12 env vars)
- `services/risk/kelly.py` — kelly_fraction(), apply_kelly_fraction() with quarter-Kelly default
- `services/risk/var.py` — historical_var(), portfolio_var() with bootstrap
- `services/risk/engine.py` — validate_order() with 7 sequential checks: edge → kelly → position limit → exposure → VaR → drawdown gate → daily loss limit
- `services/risk/kill_switch.py` — KillSwitch class with file-drop (STOP.flag) + API trigger
- `services/execution/base.py` — ExecutionProvider ABC, Position, OrderResult, AccountState
- `services/execution/paper.py` — PaperProvider with virtual P&L tracking, cash management
- `services/execution/slippage.py` — check_slippage() with configurable threshold

### Pillar A: Market Intelligence (Built Second)
- `services/kronos/config.py` — KronosConfig (model size, device, max_context=512)
- `services/kronos/predictor.py` — KronosPredictor wrapper with graceful model-not-loaded fallback
- `services/kronos/service.py` — FastAPI on port 5003: /forecast/{symbol}, /batch-forecast, /health
- `services/data/base.py` — MarketDataProvider ABC, MarketSnapshot
- `services/data/yfinance.py` — YFinanceProvider with OHLCV + snapshot fetching
- `agents/scanner/config.py` — ScannerConfig (watchlist symbols, filter thresholds)
- `agents/scanner/filters.py` — filter_liquidity(), filter_spread(), filter_unusual_volume(), filter_significant_move()
- `agents/scanner/scanner.py` — MarketScanner producing ranked OpportunityList with composite signal strength

### Pillar B: Research & Analysis (Built Third)
- `llm/config.py` — LLMConfig (DeepSeek API, $0.66/day budget, per-agent allocations)
- `llm/base.py` — LLMProvider ABC, LLMMessage, LLMResponse
- `llm/deepseek.py` — DeepSeekProvider via OpenAI-compatible API (urllib, no external HTTP dependency)
- `llm/budget.py` — LLMBudget with per-agent daily limits, date-based reset
- `llm/sanitizer.py` — Prompt injection prevention with regex patterns + 32k truncation
- `agents/fundamental/agent.py` — FundamentalAnalyst: DCF, comps table, valuation
- `agents/earnings/agent.py` — EarningsAnalyst: event tracking (add_event/get_upcoming), transcript analysis
- `agents/sentiment/agent.py` — SentimentAnalyst: NLP sentiment from headlines
- `agents/sector/agent.py` — SectorAnalyst: macro regime, sector rotation
- `agents/prediction/config.py` — PredictionConfig (Kronos weight 0.40, LLM weight 0.35, research weight 0.25)
- `agents/prediction/calibration.py` — CalibrationTracker with Brier Score per agent
- `agents/prediction/aggregator.py` — PredictionAggregator: weighted ensemble from Kronos + LLM + research

### Pillar D: Learning & Observability (Built Fourth)
- `data/ledger/schema.py` — TradeRecord v1.0.0 with close() method
- `data/ledger/store.py` — TradeLedger: append-only JSONL with stats, symbol lookup
- `agents/postmortem/agent.py` — PostMortemAgent: classify (model_error/timing_error/execution_error/external_shock) + consolidate
- `data/knowledge_base/store.py` — KnowledgeBase: lessons + patterns persistence

### Frontend
- `frontend/src/pages/RiskDashboardPage.tsx` — 4-widget dashboard: kill switch status, portfolio snapshot, risk config, signal feed + scanner opportunities + LLM budget tracker
- `frontend/src/services/api.ts` — Added getRiskStatus, getRiskPortfolio, getRiskSignals, getRiskOpportunities, getBudgetStatus
- `frontend/vite.config.ts` — Added /api/risk proxy to port 5002

### Cross-Cutting
- `services/api.py` — FastAPI on port 5002 unifying Pillar C + A + B endpoints
- `services/tests/` — 141 tests total across 15 test files

## Key Design Decisions

1. **Python 3.9.6** compatibility — no Python 3.10+ features (no `|` union syntax, no `match`/`case`). Use `Optional[X]`, `from __future__ import annotations`
2. **DeepSeek V4 Pro** as primary LLM ($0.14/M input, $0.28/M output), OpenAI-compatible API
3. **Kronos-small** (24.7M params, 512 context) as default model, Kronos-base optional
4. **MPS acceleration** on Apple M2 Pro (32GB unified memory)
5. **LIVE_TRADING=false** default — requires +true AND +confirm for live execution
6. **Kill switch** dual-trigger: file-drop (local) + API endpoint (cloud)
7. **All risk rules** are deterministic Python — not LLM judgment calls
8. **Quarter-Kelly** (fraction=0.25) as default position sizing
9. **Provider-agnostic** interfaces everywhere: LLM, data, execution can be swapped
10. **LLM budget** $0.66/day total ($10-20/month), per-agent caps enforced
11. **Prompt sanitization** on all user/external content before LLM calls
12. **Paper trading** is the default execution mode
13. **All services use urllib** (std library) for HTTP, avoiding external HTTP dependencies

## Commands Reference

```bash
# Run all tests
cd /Users/animesh/Desktop/QuantEdge_Platform
python3 -m pytest services/tests/ -v

# Run specific test file
python3 -m pytest services/tests/test_kelly.py -v

# Start risk API
python3 services/api.py

# Start Kronos service
python3 services/kronos/service.py

# Install Kronos model (one-time setup)
git clone https://github.com/shiyu-coder/Kronos /tmp/kronos_repo
cd /tmp/kronos_repo && pip install -r requirements.txt

# Frontend
cd frontend && npm run dev
cd frontend && npx tsc -b   # typecheck
cd frontend && npx eslint .  # lint
```

## Test Suite Map

| File | Tests | What it covers |
|------|-------|---------------|
| test_kelly.py | 10 | Kelly fraction formula, fractional sizing, edge cases |
| test_var.py | 6 | Historical VaR, percentile, portfolio VaR, bootstrap |
| test_risk_engine.py | 10 | 7 checks: edge, kelly, position, exposure, VaR, drawdown, loss |
| test_kill_switch.py | 7 | File-drop detection, trigger/resume, health check |
| test_slippage.py | 5 | Within/exceeds threshold, price improvement, extreme |
| test_paper_execution.py | 9 | Buy/sell orders, cash management, cancel, account state |
| test_integration.py | 3 | Full order flow: signal→risk→slippage→execution |
| test_kronos.py | 7 | Config, predictor unloaded state, graceful degradation |
| test_data_adapters.py | 5 | YFinance provider, snapshot, import handling |
| test_scanner.py | 7 | Filters (liquidity, spread, volume, move), ranking |
| test_llm.py | 14 | Config, DeepSeek provider, budget tracking, sanitizer |
| test_fundamental.py | 8 | Schema, JSON extraction, budget limits, API key handling |
| test_earnings.py | 6 | Event tracking, transcript analysis, budget |
| test_sentiment_sector.py | 8 | Both agents, budget exceeded, no API key |
| test_prediction.py | 13 | Weighted ensemble, Brier calibration, Kronos→probability |
| test_pillar_a_integration.py | 4 | Scanner→opportunities, Kronos compatibility |
| test_pillar_b_integration.py | 4 | Prediction→risk engine, HOLD signal, calibration |
| test_pillar_d.py | 15 | TradeRecord lifecycle, ledger stats, post-mortem, knowledge base |
| **TOTAL** | **141** | |

## Environment Variables (from .env.example)

```bash
# Pillar C
MIN_CONFIDENCE_THRESHOLD=0.55
KELLY_FRACTION=0.25
MAX_POSITION_PCT=0.05
MAX_TOTAL_EXPOSURE=3.0
MAX_DRAWDOWN=0.08
DAILY_LOSS_LIMIT=5000
DAILY_VAR_LIMIT=10000
SLIPPAGE_THRESHOLD=0.02
LIVE_TRADING=false
LIVE_TRADING_CONFIRM=no
KILL_SWITCH_DIR=./flags

# Kronos
KRONOS_MODEL_SIZE=small
KRONOS_DEVICE=mps
KRONOS_PORT=5003

# LLM
DEEPSEEK_API_KEY=sk-...
LLM_DAILY_BUDGET=0.66

# Scanner
SCANNER_SYMBOLS=AAPL,MSFT,GOOGL,AMZN,NVDA,TSLA,META,BTC-USD,ETH-USD
```

## Next Priorities (from VISION.md)

1. **Backtesting integration** — wire PredictionAggregator into MultiTimeFrameBacktestEngine
2. **Continuous paper trading loop** — scheduler running full pipeline every 15m
3. **DeepSeek fine-tuning** — train on successful research report patterns

## Git Log (May 31 session, 27 commits)

All commits on `main` branch. First commit was security hardening (b593746), then full 4-pillar build:

```
7421c04 feat(pillar-c): add risk & execution config to .env.example
c1a4f32 docs: add VISION.md
0e49fac feat(pillar-d): add Trade Ledger, Post-Mortem agent, and Knowledge Base
bdca709 feat(pillar-b): integrate research agents with API, add budget widget to dashboard
a315cde feat(pillar-b): add Prediction Aggregator with ensemble probability, Brier calibration
3b1c743 feat(pillar-b): add Sentiment & Sector/Macro agents with LLM-powered analysis
9416738 feat(pillar-b): add Earnings/Catalyst Agent with event tracking and transcript analysis
[redacted] feat(pillar-b): add Fundamental Analyst agent with DCF, comps, valuation
012a5bb feat(pillar-b): add LLM provider layer with DeepSeek, budget tracking, prompt sanitizer
a5f1d37 feat(pillar-a): integrate scanner with risk dashboard, add integration tests
d9d7fe2 feat(pillar-a): add multi-provider data adapter layer with yfinance
[redacted] feat(pillar-a): add Market Scanner agent with liquidity, volume, spread filters
cc2ece8 feat(pillar-a): add Kronos integration service with graceful model-not-loaded fallback
[redacted] fix(pillar-c): restore position limit as fail-gate per spec, fix double-failure
[redacted] feat(pillar-c): add risk engine with 7 deterministic checks
d332dd6 feat(pillar-c): add paper execution provider with P&L tracking
1127f21 feat(pillar-c): add slippage guard with configurable threshold
80b02c2 feat(pillar-c): add dual-trigger kill switch with health check
32fe5fd feat(pillar-c): add historical VaR with portfolio-level bootstrap
[redacted] feat(pillar-c): add Kelly criterion sizing with quarter-Kelly default
b19e428 feat(pillar-c): add directory structure, shared data types, and test fixtures
```

## Doc References

- `AUDIT.md` — Full codebase audit with Keep/Refactor/Replace/Delete/Missing buckets
- `docs/superpowers/specs/2026-05-31-quantedge-v2-design.md` — Complete 4-pillar design spec
- `docs/superpowers/plans/2026-05-31-pillar-c-risk-execution.md` — Pillar C implementation plan
- `VISION.md` — Product strategy, moat analysis, Series A pitch

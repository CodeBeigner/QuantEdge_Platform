# QuantEdge Platform - Claude Code Guidelines

## Project Boundary (STRICT)

**All file operations (read, write, edit, delete, create) MUST be scoped to `/Users/animesh/Desktop/QuantEdge_Platform/` and its subdirectories.**

- Do NOT read, write, edit, or create files outside this project directory.
- Do NOT run commands that modify files outside this directory (e.g., no `cd ~` and editing dotfiles, no touching other repos).
- Temporary files (if needed) should be created inside this project directory.
- Git operations should only target this repository.

**Exceptions:**
- Reading global config/docs for reference (e.g., checking installed tool versions) is acceptable.
- Installing dependencies via package managers (npm, pip, mvn) that write to system paths is acceptable.
- Running the project's own dev servers, Docker commands, and test suites is acceptable.

## Project Structure

- `QuantPlatformApplication/` - Java Spring Boot backend (port 8080)
- `frontend/` - React 19 + TypeScript + Vite frontend (port 3000)
- `ml-service/` - Python FastAPI ML service (port 5001)

## Tech Stack

- Backend: Java 21, Spring Boot 3.5, PostgreSQL 15, Redis 7, Flyway migrations
- Frontend: React 19, TypeScript 5.9, Vite 8, Tailwind CSS 4, Zustand 5
- ML: Python 3.9+, FastAPI, XGBoost, PyTorch (LSTM)
- Brokers: Paper (built-in), Alpaca (US equities), Delta Exchange (crypto derivatives)
- AI: Anthropic Claude for agent decision-making

## Session Memory (May 31, 2026)

### What We Built
QuantEdge v2.0 — complete 4-pillar architecture with 141 tests (all passing):

- **Pillar C (Risk & Execution):** Kelly sizing, 7 deterministic risk checks, kill switch (file-drop + API), paper provider, slippage guard. Port 5002 API.
- **Pillar A (Market Intelligence):** Kronos-small forecasting service (port 5003), Market Scanner with 4 filters, yfinance data adapter.
- **Pillar B (Research & Analysis):** 4 LLM-powered agents (Fundamental Analyst, Earnings/Catalyst, Sentiment, Sector/Macro) + Prediction Aggregator with weighted ensemble (Kronos 40%, DeepSeek 35%, research 25%) + Brier Score calibration. LLM provider layer with DeepSeek V4 Pro ($0.66/day budget), per-agent caps, prompt sanitizer.
- **Pillar D (Learning):** Append-only Trade Ledger (JSONL), Post-Mortem Agent (classify: model_error/timing_error/execution_error/external_shock), Knowledge Base.
- **Frontend:** Risk Dashboard at /risk-dashboard with 4 widgets (kill switch, portfolio, risk config, signal feed + scanner opportunities + budget tracker).
- **Auth page enhanced, OrdersPage connected via backend proxy, TradingPage charts rewritten with reduce, all 10 silent .catch() handlers fixed.**

### Key Decisions
1. Python 3.9.6 — no `|` union syntax, use `Optional[X]` and `from __future__ import annotations`
2. DeepSeek V4 Pro primary LLM (OpenAI-compatible API via stdlib urllib), Claude fallback
3. Kronos-small (24.7M params, 512 context) default; not installed yet (model is 500MB+)
4. MPS-accelerated on Apple M2 Pro (32GB unified memory)
5. LIVE_TRADING=false default; requires +true AND +confirm for live
6. Quarter-Kelly (fraction=0.25) default
7. All risk rules deterministic Python, not LLM
8. Prompt sanitization on all external content before LLM calls
9. All HTTP via stdlib urllib (no requests/httpx dependency for LLM calls)

### Next Priority (from VISION.md)
1. Backtesting integration — wire PredictionAggregator into MultiTimeFrameBacktestEngine
2. Continuous paper trading loop — scheduler every 15m during market hours
3. DeepSeek fine-tuning pipeline for research agents

### Full Context
See `docs/superpowers/QUANTEDGE_CONTEXT.md` for complete file map, test suite breakdown, env vars, and git log.

# QuantEdge Platform — v2.0 Design Specification

**Date:** 2026-05-31
**Status:** Approved
**Source:** Synthesis of Kronos (shiyu-coder/Kronos), Prediction Market Trading Bot, Claude Finance Agents, and existing QuantEdge codebase

---

## 1. Overview

QuantEdge v2.0 restructures the platform around four pillars:

| Pillar | Purpose | Key Components |
|--------|---------|---------------|
| **A: Market Intelligence** | Continuous multi-asset signal generation | Kronos forecasting, Market Scanner, multi-provider data adapters |
| **B: Research & Analysis** | Deep multi-source intelligence per opportunity | Fundamental Analyst, Earnings/Catalyst, Sentiment/NLP, Sector/Macro agents |
| **C: Decision & Risk** | Sized, validated trade decisions | Kelly sizing engine, kill switch, execution providers |
| **D: Learning & Observability** | Feedback-driven improvement | Trade ledger, post-mortem classification, calibration dashboard |

**Delivery sequence:** Pillar C → Pillar A → Pillar B → Prediction Aggregator → Pillar D

---

## 2. Architecture Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **LLM Provider** | DeepSeek V4 Pro (primary), Claude (fallback) | ~10x cheaper than Claude, comparable reasoning, OpenAI-compatible API |
| **LLM Budget** | $10-20/month total | Per-agent daily caps enforced |
| **Forecasting Model** | Kronos-small (24.7M params, 512 context), Kronos-base (optional) | Distributional price path forecasts > point predictions for risk sizing |
| **GPU** | MPS-accelerated on Apple M2 Pro (32GB unified memory) | No CUDA dependency, unified memory eliminates VRAM bottleneck |
| **Market Data** | yfinance (equities) + Binance/CCXT (crypto) | Free tier, covers target assets |
| **Broker Execution** | Paper (default), Alpaca (equities), CCXT (crypto) | LIVE_TRADING=true gate + secondary confirmation |
| **Prediction Markets** | Phase 2 (toggleable) | Architecture designed with extension points |
| **Deployment** | Local (dev/backtest) + Cloud (24/7 live) | Docker Compose for both |
| **Compliance** | Personal tool (Phase 1), audit trails later | Ledger schema designed upgradeable |
| **Kill Switch** | File-drop (local) + API endpoint (cloud) | Dual trigger ensures remote operation |
| **Risk Rules** | Deterministic Python, not LLM | Code is the rule, not prose |

---

## 3. Pillar C: Decision & Risk (First Deliverable)

### 3.1 File Structure

```
services/
├── risk/
│   ├── __init__.py
│   ├── engine.py          # validate_order(): runs all 7 checks, returns Pass/Fail
│   ├── kelly.py           # kelly_fraction(): f* = (p*b - q)/b, apply_kelly_fraction()
│   ├── var.py             # historical_var(): 95% CI simulation
│   ├── kill_switch.py     # KillSwitch class: file-drop watch + API endpoint
│   └── config.py          # all thresholds from env, dataclass
├── execution/
│   ├── __init__.py
│   ├── base.py            # ExecutionProvider ABC
│   ├── paper.py           # PaperProvider
│   ├── alpaca.py          # AlpacaProvider (stub)
│   ├── ccxt_provider.py   # CCXTProvider (stub)
│   └── slippage.py        # check_slippage(): abort if price deviates >2%
└── tests/
    ├── test_risk_engine.py
    ├── test_kelly.py
    ├── test_var.py
    ├── test_kill_switch.py
    └── conftest.py
```

### 3.2 Risk Engine (engine.py)

**`validate_order(signal: PredictionSignal, portfolio: PortfolioState) -> RiskResult`**

7 sequential checks, all must pass:

```
1. EDGE CHECK:     signal.confidence >= MIN_CONFIDENCE_THRESHOLD
2. KELLY SIZING:   position_size = kelly_fraction(p, b) * KELLY_FRACTION * NAV
3. POSITION LIMIT:   position_size <= MAX_POSITION_PCT * NAV
4. EXPOSURE CHECK: new_exposure + existing_exposure <= MAX_TOTAL_EXPOSURE * NAV
5. VAR CHECK:      historical_var(portfolio, 0.95) <= DAILY_VAR_LIMIT
6. DRAWDOWN GATE:  current_drawdown <= MAX_DRAWDOWN
7. DAILY LOSS:     today_pnl >= -DAILY_LOSS_LIMIT
```

Returns `RiskResult(passed: bool, sized_order: Optional[SizedOrder], failures: List[str])`.

### 3.3 Kelly Sizing (kelly.py)

```python
def kelly_fraction(win_probability: float, win_loss_ratio: float) -> float:
    """
    f* = (p * b - q) / b
    where p = win_probability, q = 1 - p, b = win_loss_ratio
    """
    return (win_probability * win_loss_ratio - (1 - win_probability)) / win_loss_ratio

def apply_kelly_fraction(p: float, b: float, nav: float, fraction: float = 0.25) -> float:
    """Returns position size in dollars. fraction=0.25 is quarter-Kelly."""
    f_star = kelly_fraction(p, b)
    f_star = max(0.0, f_star)  # never negative
    return f_star * fraction * nav
```

### 3.4 Kill Switch (kill_switch.py)

```python
class KillSwitch:
    """Dual-trigger kill switch: file-drop (local) + API endpoint (cloud)."""

    def __init__(self, flag_dir: str = "./flags"):
        self.flag_dir = Path(flag_dir)
        self.flag_dir.mkdir(exist_ok=True)
        self.stop_flag = self.flag_dir / "STOP.flag"
        self.resume_flag = self.flag_dir / "RESUME.flag"
        self._active = False
        self._check_interval = 1.0  # seconds

    def is_active(self) -> bool:
        """Check file-drop trigger. Called before every order."""
        if self.stop_flag.exists():
            self._active = True
        return self._active

    async def trigger(self) -> None:
        """API trigger. Creates STOP.flag and returns cancellation count."""
        ...

    async def resume(self) -> None:
        """Clear flags. Requires manual confirmation."""
        ...

    async def health_check(self) -> bool:
        """Self-test: create temp flag, verify detection, clean up."""
        ...
```

### 3.5 Execution Providers (base.py)

```python
class ExecutionProvider(ABC):
    @abstractmethod
    async def submit_order(self, order: SizedOrder) -> OrderResult: ...
    @abstractmethod
    async def cancel_order(self, order_id: str) -> bool: ...
    @abstractmethod
    async def cancel_all_orders(self) -> int: ...
    @abstractmethod
    async def get_positions(self) -> List[Position]: ...
    @abstractmethod
    async def get_account(self) -> AccountState: ...

class PaperProvider(ExecutionProvider):
    """Deterministic paper trading. Tracks virtual P&L, applies slippage model."""
    ...

class AlpacaProvider(ExecutionProvider):
    """US equities via Alpaca Markets API. Gated behind LIVE_TRADING=true."""
    ...

class CCXTProvider(ExecutionProvider):
    """Crypto via CCXT unified API. Binance, Bybit, etc. Gated behind LIVE_TRADING=true."""
    ...
```

### 3.6 Config (config.py)

```python
@dataclass
class RiskConfig:
    min_confidence_threshold: float = float(os.getenv("MIN_CONFIDENCE_THRESHOLD", "0.55"))
    kelly_fraction: float = float(os.getenv("KELLY_FRACTION", "0.25"))
    max_position_pct: float = float(os.getenv("MAX_POSITION_PCT", "0.05"))
    max_total_exposure: float = float(os.getenv("MAX_TOTAL_EXPOSURE", "3.0"))
    max_drawdown: float = float(os.getenv("MAX_DRAWDOWN", "0.08"))
    daily_loss_limit: float = float(os.getenv("DAILY_LOSS_LIMIT", "5000"))
    daily_var_limit: float = float(os.getenv("DAILY_VAR_LIMIT", "10000"))
    slippage_threshold: float = float(os.getenv("SLIPPAGE_THRESHOLD", "0.02"))
    live_trading: bool = os.getenv("LIVE_TRADING", "false").lower() == "true"
    live_trading_confirm: bool = os.getenv("LIVE_TRADING_CONFIRM", "no").lower() == "yes"
    kill_switch_dir: str = os.getenv("KILL_SWITCH_DIR", "./flags")
```

### 3.7 Testing Requirements

Every risk check function must have a unit test **before** implementation:

| Test | What it verifies |
|------|-----------------|
| `test_kelly.py` | Kelly formula for known inputs, edge cases (p=0, p=1, b<1), quarter-Kelly scaling |
| `test_var.py` | Historical VaR against known returns distribution, 95% vs 99%, empty portfolio |
| `test_risk_engine.py` | Each check independently, all passes → order approved, any fail → order rejected, drawdown/edge limits |
| `test_kill_switch.py` | File creation → is_active returns True, resume clears, health check self-tests |
| `test_paper_execution.py` | Order lifecycle (submit→fill→cancel), P&L tracking, portfolio state consistency |
| `test_slippage.py` | Price within threshold → approve, price exceeds threshold → abort |

---

## 4. Pillar A: Market Intelligence (Second Deliverable)

### 4.1 Kronos Integration Service

```
services/kronos/
├── __init__.py
├── predictor.py       # KronosPredictor wrapper
├── tokenizer.py       # OHLCV → hierarchical discrete tokens
├── service.py         # FastAPI app: /forecast, /batch-forecast
├── cache.py           # Tokenizer weight caching
└── config.py          # Model size flag, device config (MPS)
```

**Endpoints:**
- `POST /forecast/{symbol}` — accepts OHLCV DataFrame, returns probabilistic price paths (open/high/low/close/volume percentiles)
- `POST /batch-forecast` — accepts list of DataFrames, returns parallel forecasts

**Behavior:** Kronos generates raw price paths. These do NOT become trade signals directly — they feed the Prediction Aggregator in Pillar B.

### 4.2 Market Scanner

```
agents/scanner/
├── __init__.py
├── scanner.py         # watchlist scan, opportunity ranking
├── filters.py         # liquidity, volume, spread, catalyst filters
└── config.py
```

### 4.3 Multi-Provider Data Adapters

```
services/data/
├── __init__.py
├── base.py            # MarketDataProvider ABC
├── binance.py         # BinanceProvider (REST + WS)
├── yfinance.py        # YFinanceProvider
└── ccxt_adapter.py    # CCXTProvider (unified crypto)
```

---

## 5. Pillar B: Research & Analysis (Third Deliverable)

### 5.1 Research Agents

```
agents/
├── fundamental/
│   ├── __init__.py
│   ├── dcf.py          # DCF model builder, WACC range
│   ├── comps.py        # Comparable company analysis
│   └── agent.py        # FundamentalAnalyst: orchestrate, call DeepSeek
├── earnings/
│   ├── __init__.py
│   ├── calendar.py     # Earnings/Fed/macro event tracking
│   ├── transcript.py   # Earnings call ingestion via DeepSeek
│   └── agent.py        # EarningsAnalyst
├── sentiment/
│   ├── __init__.py
│   ├── scraper.py      # News RSS, Reddit scraper
│   ├── sanitizer.py    # Content sanitization before LLM
│   └── agent.py        # SentimentAnalyst
└── sector/
    ├── __init__.py
    ├── breadth.py      # Sector breadth analysis
    └── agent.py        # SectorAnalyst
```

### 5.2 LLM Provider Layer

```
llm/
├── __init__.py
├── base.py             # LLMProvider ABC
├── deepseek.py         # DeepSeekProvider (OpenAI-compatible)
├── claude.py           # ClaudeProvider (fallback)
├── budget.py           # Token budget tracking per agent
├── sanitizer.py        # Prompt injection prevention
└── config.py           # API keys, budget caps
```

### 5.3 Prediction Aggregator

```
agents/prediction/
├── __init__.py
├── aggregator.py       # ensemble_probability(): Kronos + DeepSeek synthesis
├── edge.py             # market_implied_probability comparison (Phase 2)
├── calibration.py      # Brier Score tracking per agent, per asset class
└── schema.py           # PredictionSignal dataclass
```

---

## 6. Pillar D: Learning & Observability (Fourth Deliverable)

```
data/
├── ledger/
│   ├── __init__.py
│   ├── schema.py       # TradeRecord dataclass (versioned)
│   └── store.py        # Append-only JSON log writer
├── knowledge_base/
│   ├── __init__.py
│   └── store.py        # Structured failure/success pattern store

agents/postmortem/
├── __init__.py
├── classifier.py       # Categorize: model error, timing, execution, external
├── consolidator.py     # Nightly aggregation, calibration update
└── agent.py            # PostMortemAgent
```

---

## 7. Frontend Restructure

Consolidate 15 pages into 4 pillar-aligned views:

| View | Contents |
|------|----------|
| **Dashboard** | Portfolio stats (Sharpe, Win Rate, Max DD), signal feed with confidence/edge, Kronos fan charts |
| **Research** | Agent reports per asset: Fundamental valuation, earnings signal, sentiment score, macro context |
| **Trading** | Execution panel, order book, position list, order history |
| **Analytics** | Trade history P&L, calibration chart, backtest results, agent performance (Brier Score per agent) |

Always-visible: LIVE/PAPER mode banner, kill switch status indicator, LLM usage tracker.

---

## 8. Config Reference

All thresholds, keys, model paths via environment variables:

```bash
# LLM
DEEPSEEK_API_KEY=sk-...
DEEPSEEK_BASE_URL=https://api.deepseek.com/v1
DEEPSEEK_MODEL=deepseek-chat
ANTHROPIC_API_KEY=sk-ant-...         # fallback only
LLM_DAILY_BUDGET=5.0                 # $5/day cap across all agents

# Risk
MIN_CONFIDENCE_THRESHOLD=0.55
KELLY_FRACTION=0.25
MAX_POSITION_PCT=0.05
MAX_TOTAL_EXPOSURE=3.0
MAX_DRAWDOWN=0.08
DAILY_LOSS_LIMIT=5000
DAILY_VAR_LIMIT=10000
SLIPPAGE_THRESHOLD=0.02

# Execution
LIVE_TRADING=false
LIVE_TRADING_CONFIRM=no
KILL_SWITCH_DIR=./flags

# Kronos
KRONOS_MODEL_SIZE=small              # small | base
KRONOS_MODEL_PATH=./models/kronos
KRONOS_DEVICE=mps                    # mps | cpu | cuda

# Data
MARKET_DATA_PROVIDERS=binance,yfinance
```

---

## 9. What Was Removed

| Item | Reason |
|------|--------|
| 6 deprecated ML endpoints | Error at runtime anyway. Replaced by /forecast (Kronos). |
| Hardcoded secrets in application.yml | Fixed in security hardening pass. |
| TelegramCommandHandler TODOs | Telegram becomes notification channel only, not command handler. |
| IntentParserService (chat-based agent control) | Chat-based trading is not production-grade. API + dashboard are primary interfaces. |
| XGBoost/LSTM single-model signals | Replaced by Kronos ensemble prediction. |
| Claude-specific agent code | Replaced by provider-agnostic LLM layer. |
| AgentSchedulerService (Java cron) | Replaced by Python event-driven agent orchestration. |

---

## 10. Open Questions (Resolved)

| # | Question | Resolution |
|---|----------|------------|
| 1 | Live execution? | Yes, gated behind LIVE_TRADING=true + confirm |
| 2 | GPU? | M2 Pro with MPS acceleration |
| 3 | LLM provider? | DeepSeek V4 Pro primary, Claude fallback |
| 4 | LLM budget? | $10-20/month total |
| 5 | Prediction markets? | Phase 2, toggleable |
| 6 | Deployment? | Local + cloud |
| 7 | Data feeds? | yfinance + Binance (free tier) |
| 8 | Compliance? | Personal tool for now, upgradeable ledger schema |

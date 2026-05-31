# QuantEdge — Product Vision & Strategy

**Date:** 2026-05-31
**Status:** Post-Pillar A/B/C/D implementation

---

## 1. Core Differentiation Today

QuantEdge combines five capabilities that no single existing tool provides:

| Capability | What it does | Nearest competitor |
|-----------|-------------|-------------------|
| **Kronos price-path forecasting** | Probabilistic OHLCV forecasts (median + percentile bands) from a 24.7M-param Transformer pre-trained on 45+ exchanges | Kronos alone (open-source, no UI/orchestration) |
| **Multi-source research synthesis** | DCF/comps/valuation + earnings analysis + sentiment NLP + macro regime — all fed through DeepSeek V4 Pro reasoning | Bloomberg Terminal (4-5x more expensive, no integrated AI reasoning) |
| **Calibrated ensemble prediction** | Kronos paths + research reports + LLM reasoning → single calibrated probability with Brier Score tracking | No single tool; done manually by hedge fund analysts |
| **Deterministic risk engine** | Kelly sizing, VaR, drawdown gate, kill switch — all pure Python, not LLM black-box | Portfolio management software (lacks signal integration) |
| **Continuous learning loop** | Every trade outcome classified (model error/timing/execution/external), fed back into scanner + prediction | Manual post-trade review in most firms |

The value is not any single component — Kronos is open-source, DeepSeek is available to anyone. The value is the **orchestration**: a pipeline that produces a calibrated probability from raw market data, sizes it with Kelly, gates it through 7 deterministic checks, and learns from every outcome. No one has wired these pieces together.

---

## 2. User Segments — Honest Assessment

### Sophisticated Retail Investors (Best Fit Today)

**Who:** Self-directed investors managing $50K–$5M. High financial literacy, already use multiple tools (yahoo finance, finviz, tradingview, custom spreadsheets). Want institutional-grade analysis without the $24K/year Bloomberg cost.

**Why QuantEdge wins:** Combines what would take 3-4 subscription tools + manual spreadsheet work into one pipeline. The "why" behind every signal is transparent (rationale field, Brier calibration). $X/month vs $24K/year Bloomberg + $600/year TradingView + $480/year earnings tools.

**Revenue model:** $50-200/month subscription. TAM: ~5M self-directed investors globally with >$100K in assets. SAM: ~500K who use multiple analysis tools. Target: 1,000 paying users = $600K–$2.4M ARR.

### Independent RIAs & Family Offices (Highest-Value Expansion)

**Who:** Manage $10M–$500M pools. Need auditability, compliance-friendly outputs, and explainable decisions for clients. Currently rely on Morningstar/Bloomberg/outsourced research.

**Why QuantEdge wins:** The Trade Ledger provides full audit trails. The Post-Mortem agent provides explanations clients can understand. Brier Score calibration proves the system isn't guessing. Kelly sizing replaces gut-feel position sizing.

**Revenue model:** $500–2,000/month per advisor. TAM: ~300K RIAs in the US. SAM: ~15K tech-forward practices. Target: 200 firms = $1.2M–$4.8M ARR.

### Prop Trading Desks (Weak Fit)

**Why not:** Need co-location, sub-millisecond latency, HFT infrastructure. QuantEdge is research-first, not speed-first. Kronos inference takes seconds, not microseconds. The risk engine is designed for daily/positional, not tick-level.

**Verdict:** Not a target segment. Don't build for them.

### Quantitative Researchers (Secondary Segment)

**Who:** Need backtesting infrastructure, model experimentation, fine-tuning pipelines. Kronos already has a fine-tuning pipeline; QuantEdge adds the research layer and calibration tracking.

**Revenue model:** $100-500/month. Smaller TAM (~50K globally) but high willingness to pay for infrastructure that saves build time.

### Prediction Market Professionals (Phase 2)

**Why later:** Small but rapidly growing segment. Highly price-sensitive (margins of 4-8%). The edge-calculation feature (p_model - p_market) is specifically designed for them, but the core equities/crypto platform must be proven first.

---

## 3. Platform vs Tool — The Path

### Phase 1: Private Tool (Current)
QuantEdge runs on the owner's M2 Pro MacBook, trading personal accounts. Paper trading validates the pipeline. All components are open-source compatible — no vendor lock-in.

### Phase 2: SaaS Product (12-18 months)
**What changes:**
- Multi-user auth, per-user configs
- Cloud deployment on GPU instances (Kronos inference at scale)
- Subscription tiers: Starter ($50/mo, 5 watchlist symbols), Pro ($200/mo, 50 symbols, custom agents), Institutional ($2K/mo, white-label, audit exports)
- Dashboard becomes the primary interface — no CLI required

**What doesn't change:** The 4-pillar architecture. The open-source Kronos model. The provider-agnostic LLM layer. Every component can be swapped without rebuilding.

### Phase 3: Agent Marketplace (24+ months)
**The vision:** Users bring their own Kronos fine-tunes (trained on their data), custom sentiment sources, proprietary execution adapters. QuantEdge becomes the orchestration layer that connects any signal source to any execution destination, with built-in risk management and learning.

**Network effects:**
- Calibration data compounds: more users → more trade outcomes → better Post-Mortem learnings → smarter signals for everyone
- Agent marketplace: 3rd-party research agents compete on Brier Score; users pick the best
- Execution adapters: broker integrations contributed by community (IBKR, Schwab, Robinhood)

---

## 4. The Moat Question

> "Kronos is open-source. Claude/DeepSeek is available to anyone. What proprietary advantage can QuantEdge build?"

### Moat #1: Calibration Data (The Compounding Edge)

Every trade outcome is logged, classified, and fed back. After 1,000 trades, the system knows:
- Which agent combinations produce the highest Brier Score per asset class
- Which market conditions (VIX level, sector regime, time of day) correlate with model errors
- Which Post-Mortem classifications recur — and how to prevent them

This is proprietary data that **cannot** be replicated by running Kronos alone. A competitor can clone the repo but cannot clone 1,000 trade outcomes with full context, classification, and calibration. The data compounds every day.

### Moat #2: Workflow Orchestration (The Pipeline Is The Product)

Kronos alone is a model. DeepSeek alone is an API. The value is the **pipeline**:

```
Market data → Kronos price paths → Research agents → Ensemble prediction → Kelly sizing → 7 risk checks → Kill switch → Execution → Ledger → Post-Mortem → Feedback
```

Building this pipeline requires deep knowledge of each component's failure modes, integration points, and latency budgets. The orchestration code is not the model — it's the wiring that makes the model useful. This is harder to replicate than it looks.

### Moat #3: Fine-Tuned Kronos on Proprietary Data

The existing Kronos fine-tuning pipeline (`finetune/` directory) can be run on the system's own trade data. A Kronos model fine-tuned on proprietary calibration data will outperform the public model because it learns from real outcomes, not open-source benchmarks. This is the same moat that makes Bloomberg's pricing models valuable — the data, not the algorithm.

### Moat #4: Domain-Specific Knowledge Base

The Knowledge Base stores structured learnings across asset classes, sectors, and market regimes. "AAPL earnings: bullish sentiment + strong DCF + Kronos forecast >3% → 78% win rate in falling-rate environments." This is institutional knowledge that accumulates with every trade.

---

## 5. What To Build Next (Highest-Leverage Additions)

### Priority 1: Backtesting Integration (Validate the Ensemble)

**Problem:** We have a powerful prediction pipeline but no proof it beats benchmarks. The existing backtest engines (BacktestEngine, MultiTimeFrameBacktestEngine) need to consume Kronos forecasts + agent signals.

**Solution:** Wire `PredictionAggregator` into `MultiTimeFrameBacktestEngine` with a new `EnsembleStrategy`. Run a 5-year backtest on SPY, QQQ, AAPL, BTC-USD. Measure Sharpe, max drawdown, win rate, Brier Score. Publish the results.

**Why now:** Until backtesting proves the ensemble works, we're building on faith. This is the single most important next step.

### Priority 2: Continuous Paper Trading Loop (24/7 Autonomous)

**Problem:** The pipeline exists but doesn't run continuously. The Scanner runs on demand, not on schedule. The Kronos service starts manually.

**Solution:** Add a scheduler (`services/scheduler.py`) that runs the full pipeline every 15 minutes during market hours: Scanner → Research → Prediction → Risk → Execution. Paper trading mode only. Log every signal, every attempt, every outcome to the Ledger.

**Why now:** A 2-week paper trading run generates the calibration data that feeds moat #1. It also surfaces integration bugs that unit tests miss.

### Priority 3: DeepSeek Fine-Tuning Pipeline for Research Agents

**Problem:** The research agents use generic system prompts. They don't learn from their own outputs.

**Solution:** Build a fine-tuning pipeline that takes historical agent reports + actual trade outcomes and fine-tunes a DeepSeek model instance to produce better-calibrated probabilities. Feed successful report patterns back as few-shot examples.

**Why now:** This is the feedback loop that makes moat #3 real. A fine-tuned agent that learns from its own wins and losses will outperform the generic prompt version within weeks.

---

## 6. Series A Pitch

### The Market

Retail investors manage $50 trillion globally, but their tools haven't changed in a decade. They toggle between Yahoo Finance (free but shallow), TradingView (charts but no fundamental depth), and scattered earnings calendars. Bloomberg Terminal charges $24,000/year per seat — pricing out 99% of the market. Meanwhile, hedge funds deploy teams of analysts running DCF models, parsing earnings calls, and sizing positions with Kelly criterion — a workflow that a single AI pipeline can now automate.

### The Wedge

QuantEdge is the first platform to combine institution-grade research (DCF models, earnings analysis, sentiment tracking, macro regime context), probabilistic price-path forecasting (Kronos, pre-trained on 45+ exchanges), and deterministic risk management (Kelly sizing, VaR, drawdown gates, kill switch) into a single, transparent pipeline. Every trade outcome feeds back into the system — the calibration data compounds, making the ensemble smarter with every market cycle.

### The Evidence

The pipeline is built and tested: 141 unit tests across 4 pillars, 26 atomic commits. Kronos-small (24.7M parameters) runs on M2 Pro Apple Silicon. DeepSeek V4 Pro powers the research layer at $0.50/M input tokens — 10x cheaper than Claude. The Trade Ledger captures every signal, every outcome, every lesson. The kill switch ensures no runaway orders. The system is paper-trading ready with a live execution gate (LIVE_TRADING=true + secondary confirmation) built into every broker adapter.

### What The Capital Unlocks

$2–3M seed extends the runway to:
- **Backtesting validation** (6 weeks): Prove the ensemble beats SPY/Q1 benchmarks on 5-year backtests. Publish the results.
- **SaaS infrastructure** (3 months): Multi-tenant cloud deployment, GPU instances for Kronos inference at scale, user auth, subscription billing.
- **First 100 customers** (6 months): Onboard a waitlist of sophisticated retail investors at $200/month. Target $240K ARR by month 12.
- **Fine-tuning pipeline** (ongoing): Train Kronos on proprietary calibration data. Train DeepSeek on successful research reports. The models get smarter as the user base grows.

The wedge is the orchestration. The moat is the compounding data. 15 minutes to build. 15 seconds to execute a research cycle that takes a junior analyst 4 hours. The market is 99% of investors who can't afford Bloomberg but want institutional-quality analysis. QuantEdge is the Bloomberg Terminal for the AI era — and it costs $200/month, not $24,000/year.

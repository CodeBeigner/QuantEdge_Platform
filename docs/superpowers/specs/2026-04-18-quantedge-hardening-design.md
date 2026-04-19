# QuantEdge Platform — Hardening & Strategy Redesign Spec

**Date:** 2026-04-18
**Status:** Approved
**Author:** Abhinav + Claude

---

## 1. Overview

Transform QuantEdge from a prototype into a production-grade crypto derivatives trading system on Delta Exchange. Three phases: (A) rule-based multi-timeframe strategies with hard risk guardrails, then (B) paper trading validation, then evolve to (C) ML-enhanced meta-filter on top of proven strategies. Educational "Learn While Earning" layer built in from day one for eventual SaaS upsell.

### Targets

| Metric | Target |
|--------|--------|
| Win rate | 55-65% |
| Profit factor | > 1.5 |
| Sharpe ratio | > 1.5 (after costs) |
| Max drawdown | < 15% |
| Effective leverage cap | 5x |
| Starting capital | < $500 |
| Exchange | Delta Exchange (India) perpetual futures |
| Pairs | BTCUSD, ETHUSD, 1 rotational altcoin |
| Timeframes | 4h (bias), 1h (zones), 15m (entries) |

---

## 2. System Architecture

Event-driven monolith (not microservices). Strategy code never touches exchange APIs directly — adapter pattern enforces separation.

```
Telegram Bot (two-way: alerts + commands)
        │
Strategy Engine (Java)
  ├── Trend Continuation Strategy
  ├── Mean Reversion Strategy
  └── Liquidation Cascade / Funding Sentiment Strategy
        │
  Signal Aggregator (confluence scoring)
        │
  Risk Engine (hard-coded guardrails, 7 checks)
        │
  Execution Mode Router (AUTONOMOUS | HUMAN_IN_LOOP)
        │
  Order Execution (Delta Exchange API adapter)
        │
  Data Pipeline (WebSocket → 15m candle aggregation → indicators)
```

### Key Decisions

- Risk engine is a hard gate in code — no LLM prompts for risk decisions.
- Stop-loss is mandatory — system cannot place entry without corresponding stop.
- Execution mode is a config flag — switch without code changes.
- All strategies share one risk budget — they compete for capital, not stack risk.
- 4h and 1h candles built FROM 15m data — prevents timestamp misalignment.
- WebSocket for market data, REST only for order placement.

---

## 3. Trading Strategies

### 3.1 Trend Continuation (Multi-Timeframe)

**When:** Market trending on 4h, pullback to key level on 1h, 15m confirms continuation.

**4H Bias:**
- EMA 21 slope > threshold → BULLISH
- EMA 21 slope < -threshold → BEARISH
- Otherwise → NEUTRAL (no trades from this strategy)

**1H Zone Identification (only when 4h has bias):**
- Swing high/low support/resistance levels
- Order blocks (last opposing candle before impulsive move)
- Fair value gaps (3-candle imbalance gaps)
- Price must be pulling BACK toward a zone

**15M Entry Trigger (only when price is at a 1h zone):**
- Bullish/bearish engulfing with above-average volume
- RSI divergence at the zone
- Break of 15m structure (lower high broken for longs, higher low for shorts)

**Funding Rate Filter:**
- Long + negative funding → +confidence
- Short + positive funding → +confidence
- Funding opposes direction → reduce confidence, tighten stop

**Exit:**
- Stop-loss: Below/above the 1h zone (structural)
- TP1: 1.5R (move stop to breakeven)
- TP2: 2.5R (close remaining)
- Trail: ATR(14) on 15m once 2R reached

**Expected win rate:** 58-65%

### 3.2 Mean Reversion at Extremes

**When:** Market ranging or price at statistical extreme.

**4H Regime:**
- ADX < 20 → Ranging (preferred)
- Bollinger Band Width contracting → setup building
- In trending market: extreme extensions still trigger

**1H Extreme Detection:**
- Price touches/pierces Bollinger Band (2.5σ)
- RSI > 80 or < 20
- Price > 2× ATR(14) from VWAP
- Volume spike (> 2× 20-period average) at extreme

**15M Reversal Confirmation:**
- Pin bar / doji / engulfing at extreme
- RSI divergence
- Volume declining on push into extreme (exhaustion)

**Funding Rate Filter:**
- Extreme positive funding + overbought → SHORT with high confidence
- Extreme negative funding + oversold → LONG with high confidence

**Exit:**
- Stop-loss: Beyond the extreme
- Take-profit: VWAP or middle Bollinger Band
- Time stop: 8 candles (2 hours) with no progress → close

**Expected win rate:** 55-60%

### 3.3 Liquidation Cascade / Funding Rate Sentiment

**When:** Extreme market positioning detected.

**Setup Detection:**
- Funding > 0.05%/8h for 3+ consecutive periods → crowd heavily long → look for shorts
- Funding < -0.03%/8h for 3+ consecutive periods → crowd heavily short → look for longs
- OI spike (>10% in 24h) + extreme funding → maximum conviction

**Entry (requires 1h/15m confirmation):**
- Don't fade funding blindly — wait for price confirmation
- 1h: break of key level opposing the crowd
- 15m: momentum candle with volume confirmation

**Exit:**
- Tight stop beyond entry structure
- Aggressive TP: 3:1 to 5:1 R:R (liquidation cascades move fast)
- Trail aggressively once 2R reached

**Expected win rate:** 50-55% (lower frequency, huge R:R)

---

## 4. Trade Explanation Engine ("Learn While Earning")

Every trade generates a structured explanation stored as first-class data.

### Schema

```json
{
  "trade_id": "TRD-2026-04-18-001",
  "pair": "BTCUSD",
  "direction": "LONG",
  "strategy": "TREND_CONTINUATION",
  "explanation": {
    "bias": "4H analysis text...",
    "zone": "1H zone identification text...",
    "entry_trigger": "15M entry signal text...",
    "funding_context": "Funding rate analysis...",
    "risk_calc": "Position sizing math...",
    "lesson": "Educational context linking to trading concepts..."
  },
  "outcome": {
    "result": "WIN|LOSS",
    "pnl": "+$12.40",
    "r_multiple": "2.48R",
    "post_trade_lesson": "What happened vs expected..."
  }
}
```

### Design Decisions

- Explanations generated at entry time (no hindsight bias)
- Post-trade lessons generated after close (expected vs actual)
- `lesson` field links to broader trading concepts (educational, not just log)
- Stored in PostgreSQL, queryable for future learning modules
- Dashboard shows explanations alongside charts
- Multi-tenant ready: per-user trade history isolation

---

## 5. Risk Engine (Hard Guardrails)

Seven sequential checks. ALL must pass before any trade reaches the exchange.

| Check | Rule | Action on Fail |
|-------|------|----------------|
| 1. Position Size | risk_amount = balance × risk_per_trade_pct | REJECT |
| 2. Effective Leverage | total_exposure / equity ≤ max_effective_leverage | REJECT |
| 3. Daily Loss | daily_loss ≥ daily_loss_halt_pct of day-start balance | HALT for day + Telegram alert |
| 4. Max Drawdown | equity < (1 - max_drawdown_pct) × peak_equity | HALT + close all + Telegram CRITICAL |
| 5. Concurrent Positions | open_positions < max_concurrent AND no same-pair duplicate | REJECT |
| 6. Stop-Loss | stop attached AND stop ≤ max_stop_distance from entry | REJECT |
| 7. Fee Impact | estimated_fees ≤ fee_impact_threshold × risk_amount | REJECT |

### Default Parameters (configurable per-user)

| Parameter | Default |
|-----------|---------|
| risk_per_trade_pct | 1% |
| max_effective_leverage | 5x |
| daily_loss_halt_pct | 5% |
| max_drawdown_pct | 15% |
| max_concurrent_positions | 3 |
| max_stop_distance_pct | 2% |
| min_rr_ratio | 1.5 |
| fee_impact_threshold | 20% |

---

## 6. Telegram Bot

### Outbound Alerts

| Type | Trigger |
|------|---------|
| Trade Signal | Strategy generates trade (human-in-loop mode) |
| Trade Executed | Order filled (autonomous mode) |
| Trade Closed | Position closed |
| Risk Alert | Circuit breaker triggered |
| Critical Halt | Max drawdown reached |
| Daily Summary | End of day UTC |
| Weekly Report | Sunday |

### Inbound Commands

| Command | Action |
|---------|--------|
| /status | Equity, positions, daily P&L, active strategies |
| /positions | Open positions with unrealized P&L |
| /approve | Approve pending trade |
| /reject | Reject pending trade |
| /stop | Pause all trading (keep positions open) |
| /resume | Resume trading |
| /close_all | Emergency close all positions |
| /close [PAIR] | Close specific position |
| /mode auto | Switch to autonomous |
| /mode manual | Switch to human-in-loop |
| /risk | Show risk parameters and utilization |
| /today | Today's trades with explanations |
| /explain [id] | Full trade explanation |

### Timeout

Human-in-loop signal expires after 2 minutes if no /approve or /reject. Configurable.

---

## 7. Data Pipeline

### Multi-Timeframe Candle Aggregation

- Source: Delta Exchange WebSocket (15m candles + live ticks)
- Build 1h candles from 4× 15m candles
- Build 4h candles from 16× 15m candles
- All timeframes from same source (prevents misalignment)

### Indicators (per timeframe)

EMA 21/50, RSI 14, Bollinger Bands (2.5σ), ATR 14, VWAP, ADX, Volume vs 20-period average, Swing high/low detection, Order block identification, Fair value gap detection.

### Supplementary Feeds

Funding rate (8h intervals + current), Open interest, Long/short ratio, Liquidation data — all from Delta Exchange API.

### State Persistence

| Data | Storage | Rationale |
|------|---------|-----------|
| Open positions + orders | PostgreSQL | Survives restart. Reconcile via client order IDs |
| Candle history (15m) | PostgreSQL (TimescaleDB) | Backfill on restart |
| Current indicators | Redis | Fast access, recalculated on restart |
| Strategy state | Redis + PostgreSQL | Hot access + durability |

### Startup Reconciliation

1. Connect to Delta Exchange REST
2. Fetch open positions/orders
3. Compare with PostgreSQL state
4. Resolve discrepancies (exchange = source of truth)
5. Backfill missing 15m candles
6. Recalculate indicators
7. Resume strategy engine
8. Telegram: bot online notification

---

## 8. Backtesting Engine

### Realistic Simulation

| Aspect | Implementation |
|--------|---------------|
| Slippage | Volume-dependent: 5-15 bps BTC, 10-30 bps alts |
| Fees | Delta Exchange: 0.02% maker, 0.05% taker per side |
| Funding | 8h funding rate applied to all open positions |
| Bars | 15m with multi-timeframe aggregation (same as live) |
| Fills | Partial fill simulation based on volume |

### Anti-Overfitting Validation Pipeline

1. **Walk-Forward:** 6-month train → 2-month test, rolling windows
2. **Monte Carlo:** 1000 trade permutations, profitable in 95%+
3. **Regime-Conditional:** Separate metrics for trending/ranging/high-vol/low-vol, profitable in 3/4+
4. **Fee Stress Test:** 2× expected fees, still profitable
5. **Out-of-Sample Hold-Out:** Final 3 months never seen during development

PASS → deploy to paper trading. FAIL → back to refinement.

---

## 9. Deployment

### Oracle Cloud Free Tier

- Instance: VM.Standard.A1.Flex (ARM), 2 OCPU, 8 GB RAM
- OS: Ubuntu 22.04 ARM
- Stack: Java 21 + Node.js + Python on single VM
- Database: PostgreSQL on same VM (200 GB storage)
- Heartbeat cron: every 4 hours to prevent reclamation
- Monitoring: Prometheus + lightweight Grafana

### Security Fixes

| Issue | Fix |
|-------|-----|
| API keys in browser localStorage | Move to backend, encrypted at rest in PostgreSQL |
| Default DB password in config | Environment variables only, no defaults |
| JWT secret in config | Generate on deploy, env var storage |
| No rate limiting | Rate limit all API endpoints |
| No API key rotation | Support rotation without downtime |
| Telegram bot token | Env var only, never in code |

---

## 10. Future Evolution (Approach C — ML Meta-Filter)

After Approach A is live and profitable for 2-3 months, add:

- XGBoost model on 50+ features (funding rate, OI, liquidation data, volume profile, order book imbalance)
- ML meta-filter scores each trade signal 0-1
- Only execute trades above 0.7 threshold
- ML confidence maps to leverage: 0.7-0.8 = 10x, 0.8-0.9 = 15x, 0.9+ = 20-25x
- Walk-forward retrained weekly
- A/B test: with ML filter vs without
- Base strategies continue working independently if ML fails

---

## 11. SaaS Evolution ("Learn While Earning")

After system is proven for personal use:

- Multi-tenancy: user isolation, per-user config and risk parameters
- Strategy marketplace: proven strategies available to subscribers
- Educational content: structured learning from trade explanation data
- Subscription billing and onboarding
- Differentiator: not just signals — teaches WHY behind every trade

# Frontend Redesign — Bloomberg + Dark Neon Trading Platform

**Date:** 2026-04-19
**Status:** Approved
**Author:** Abhinav + Claude

---

## 1. Overview

Redesign the React frontend to align with the Phase 1-4 backend changes. 7 pages total — 5 redesigned, 2 new. Bloomberg-density information layout with the existing dark/neon aesthetic. Fully responsive at mobile (375px), tablet (768px), and desktop (1440px+). Zero layout bugs — this is a high-stakes trading platform.

### Design Principles

- **Bloomberg density** — every metric visible, no wasted space
- **Dark neon aesthetic** — existing palette (#0e1320 backgrounds, #00e479 green, #5de6ff cyan, #adc6ff blue, #ffb4ab red)
- **Single-screen trading** — chart + order entry + positions on one page (no tab-switching)
- **Progressive disclosure** — summary first, click to drill into details
- **Zero dead states** — empty pages show guided actions, not blank tables
- **Responsive** — all pages work at 375px, 768px, 1440px+

### Tech Stack (unchanged)

React 19, TypeScript 5.9, Vite 8, Tailwind CSS 4, Zustand 5, React Query 5, Recharts 3.8, Lightweight Charts 5.1, Lucide React, Sonner (toasts)

---

## 2. Navigation — 7 Pages

```
Sidebar:
  Dashboard        — Portfolio overview
  Trade            — Chart + order execution
  Strategies       — 3 algo strategies + signals
  Backtest         — Multi-TF backtesting
  Trade Log        — Learn While Earning
  Risk             — Risk engine dashboard
  Settings         — Configuration
```

### Pages Removed

| Page | Reason |
|------|--------|
| Market | Absorbed into Trade page |
| Orders | Absorbed into Trade page |
| Agents | Future feature (Approach C) |
| AI Intel | Future feature |
| ML | Future feature (Approach C) |
| Alerts | Alerts go to Telegram + Risk page inline |
| Firm Setup | Moved to Settings as a section |

---

## 3. Page Designs

### 3.1 Dashboard (Redesign)

**Layout:** 6-column KPI ticker → large equity curve → 2x2 grid

**KPI Ticker Bar (6 columns):**
- Equity ($518.50)
- Daily P&L (+$18.50)
- Effective Leverage (2.3x)
- Win Rate (62.5%)
- Sharpe Ratio (1.82)
- Max Drawdown (2.1%)

**Equity Curve:** Area chart with timeframe selector (1D | 1W | 1M | ALL). Gradient fill, responsive.

**2x2 Grid:**
- Open Positions: pair, side (colored), entry, P&L
- Strategy Status: 3 strategies with status dot, trade count
- Risk Budget: 3 progress bars (daily loss, drawdown, positions)
- Live Feed: timestamped event log (trades, signals, risk events)

**Empty State:** When no trades exist, show action cards: "Connect Delta Exchange", "Run First Backtest", "Start Paper Trading"

**Data Sources:**
- `GET /api/v1/system/health` — system status
- `GET /api/v1/risk-config` — risk parameters
- Account state from backend (balance, equity, positions)

### 3.2 Trade Page (New — replaces Market + Orders)

**Layout:** L-shaped, single screen

**Left (70%):** Multi-TF candlestick chart (lightweight-charts)
- Timeframe buttons: `15m | 1h | 4h`
- Full OHLCV candles with volume histogram below
- Symbol selector above chart

**Right (30%):**
- Order Book: asks (red) / bids (green) with depth bars, spread in middle
- Order Entry: BUY/SELL toggle, LIMIT/MARKET toggle, size, SL, TP inputs, submit button

**Bottom:** Tabbed table
- Open Positions: pair, side, size, entry, current, P&L, leverage, close button
- Open Orders: pair, side, type, size, price, SL, TP, cancel button
- Trade History: time, pair, side, type, size, price, status badge

**Rewiring:** All order operations go through backend `/api/v1/delta/*` endpoints (not browser-direct). Delta Exchange credentials never touch the frontend.

**Data Sources:**
- `GET /api/v1/delta/ticker/{symbol}` — live price
- `GET /api/v1/delta/orderbook/{productId}` — order book
- Orders/positions via backend broker adapter

### 3.3 Strategies Page (Redesign)

**Top Bar:** Execution mode toggle (AUTONOMOUS | HUMAN_IN_LOOP) with global status indicator

**3 Strategy Cards (responsive grid):**
Each card shows:
- Strategy name + status dot (Active/Waiting/Paused)
- Signals generated count
- Win rate percentage
- Last signal (pair + direction)
- Confidence score
- "View Signals" button

**Signals Table (below cards):**
- Expandable rows — collapsed shows: time, pair, side, confidence, R:R, status (executed/rejected/expired)
- Expanded shows full trade explanation (bias, zone, trigger, funding, lesson)
- Filterable by strategy, status

**Data Sources:**
- New API endpoints needed: `GET /api/v1/strategies/multi-tf` (list strategies with stats)
- Trade log data for signal history

### 3.4 Backtest Page (Redesign)

**Config Bar:** Symbol dropdown, capital input, slippage input, date range, Run/Walk-Forward buttons

**8-KPI Row (post-backtest):** Return%, Sharpe, MaxDD, WinRate, ProfitFactor, TotalTrades, TotalFees, FundingPaid

**Equity Curve:** Large area chart with drawdown overlay (red shaded areas below the curve)

**Per-Strategy Breakdown:** 3 cards showing each strategy's independent metrics (win rate, trades, profit factor)

**Trade Log Table:** Expandable rows with full explanations from backtest

**Data Sources:**
- `POST /api/v1/backtests/multi-tf` — run backtest
- Response: MultiTimeFrameBacktestResult with all metrics

### 3.5 Trade Log Page (New — Learn While Earning)

**Filters Bar:** All/Wins/Losses toggle, strategy dropdown, search by trade ID

**Summary Row:** Total trades, win rate, total P&L

**Accordion Trade Cards:**
- Collapsed: trade ID, pair, direction, P&L (color-coded), strategy, confidence, R-multiple
- Expanded: full explanation sections:
  - Bias (4H analysis)
  - Zone (1H identification)
  - Entry Trigger (15M signal)
  - Funding Context
  - Risk Calculation
  - Lesson (educational takeaway)
- Color-coded left border: green for wins, red for losses

**Data Sources:**
- `GET /api/v1/trade-logs` (new endpoint needed)
- `GET /api/v1/trade-logs/{id}` for individual trade detail

### 3.6 Risk Page (Redesign)

**Status Bar:** "RISK ENGINE: ARMED (7 checks active)" with green indicator

**Budget Utilization (4 progress bars):**
- Daily Loss: used / limit with percentage
- Drawdown: current / max with percentage
- Positions: open / max
- Effective Leverage: current / max

**Configurable Parameters Table:** Inline-editable table showing all 8 risk parameters. Edit button saves via `PUT /api/v1/risk-config`.

**Risk Event Log:** Timestamped list showing every risk decision — approved (green check) or rejected (red X with reason)

**Data Sources:**
- `GET /api/v1/risk-config` — current parameters
- `PUT /api/v1/risk-config` — update parameters
- `GET /api/v1/system/health` — component status

### 3.7 Settings Page (Redesign)

**4 Tab Sections:**

1. **Delta Exchange:** API key/secret inputs (masked), testnet/production toggle, save button (calls `POST /api/v1/delta/credentials`), test connection button, connection status indicator
2. **Telegram:** Bot status, chat ID display, test message button, webhook URL. Connected/disconnected indicator.
3. **Execution:** Mode toggle (AUTONOMOUS/HUMAN_IN_LOOP), signal timeout slider, active trading pairs checkboxes, account initialization (set starting capital)
4. **System:** Health dashboard (from `/api/v1/system/health`), version info, database status, Redis status

---

## 4. Responsive Breakpoints

| Breakpoint | Layout Changes |
|-----------|----------------|
| Desktop (1440px+) | Full layout as designed. Trade page L-shape. Dashboard 6-column KPIs. |
| Tablet (768px-1439px) | Dashboard KPIs wrap to 3x2. Trade page stacks chart above order book. Strategy cards 2-column. |
| Mobile (375px-767px) | Everything single column. Trade page: chart → order entry → positions (stacked). Tables become card lists with horizontal scroll for data-dense content. |

---

## 5. API Integration Changes

### New Frontend API Calls (to add to api.ts)

```typescript
// Delta Exchange (via backend — replaces direct browser calls)
getDeltaProducts(testnet?: boolean): Promise<any>
getDeltaTicker(symbol: string, testnet?: boolean): Promise<any>
getDeltaOrderBook(productId: number, depth?: number): Promise<any>
saveDeltaCredentials(apiKey: string, apiSecret: string, testnet: boolean): Promise<any>
deleteDeltaCredentials(testnet: boolean): Promise<void>
getDeltaConnectionStatus(): Promise<{ hasCredentials: boolean; environment: string }>

// Risk Config
getRiskConfig(): Promise<RiskConfig>
updateRiskConfig(config: Partial<RiskConfig>): Promise<RiskConfig>

// System
getSystemHealth(): Promise<SystemHealth>
getSystemVersion(): Promise<{ name: string; version: string; phase: string }>

// Trade Logs
getTradeLogs(filters?: TradeLogFilters): Promise<TradeLog[]>
getTradeLog(tradeId: string): Promise<TradeLog>

// Multi-TF Backtest
runMultiTFBacktest(config: BacktestConfig): Promise<MultiTFBacktestResult>
```

### Remove from Frontend

- Direct Delta Exchange API calls from `deltaExchange.ts` (credentials no longer in browser)
- Keep the TypeScript types from `deltaExchange.ts` for response parsing

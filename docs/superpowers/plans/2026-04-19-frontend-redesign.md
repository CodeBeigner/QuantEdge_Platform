# Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the React frontend to 7 pages — Bloomberg density with dark neon aesthetic, fully responsive, wired to Phase 1-4 backend APIs.

**Architecture:** Modify existing React 19 + TypeScript + Vite app. Update types, API service, routing, sidebar, then rebuild each page. Each task produces one working page that can be tested in the browser.

**Tech Stack:** React 19, TypeScript 5.9, Vite 8, Tailwind CSS 4, Zustand 5, React Query 5, Recharts 3.8, Lightweight Charts 5.1, Lucide React, Sonner

**Frontend Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/frontend`

---

## File Structure

### Modified Files

```
src/types/index.ts                    — Add new types for Phase 1-4 APIs
src/services/api.ts                   — Add new API calls (delta, risk-config, system, trade-logs)
src/App.tsx                           — Update routes (remove old pages, add new ones)
src/components/layout/Sidebar.tsx     — Update navItems to 7 new pages
src/index.css                         — Minor additions for new component styles
```

### New Page Files

```
src/pages/DashboardPage.tsx           — REWRITE: Bloomberg-style dashboard
src/pages/TradePage.tsx               — NEW: Single-screen trading (replaces Market + Orders)
src/pages/StrategiesPage.tsx          — REWRITE: 3 multi-TF strategies + signals
src/pages/BacktestPage.tsx            — REWRITE: Multi-TF backtest with realistic metrics
src/pages/TradeLogPage.tsx            — NEW: Learn While Earning accordion
src/pages/RiskPage.tsx                — REWRITE: 7-check risk engine dashboard
src/pages/SettingsPage.tsx            — REWRITE: Delta creds, Telegram, execution, system
```

### Files to Keep As-Is

```
src/pages/AuthPage.tsx                — No changes needed
src/pages/FirmSetupPage.tsx           — No changes needed (accessed from Settings)
src/components/layout/TopBar.tsx      — No changes needed
src/components/layout/LiveTicker.tsx  — No changes needed
src/components/layout/CommandPalette.tsx — Update routes list
src/components/layout/NotificationPanel.tsx — No changes
src/components/ui/*                   — No changes
src/stores/*                          — No changes
src/hooks/*                           — No changes
```

### Files to Remove (old pages absorbed elsewhere)

```
src/pages/MarketPage.tsx              — Absorbed into TradePage
src/pages/OrdersPage.tsx              — Absorbed into TradePage
src/pages/AgentsPage.tsx              — Future feature (Approach C)
src/pages/AIIntelPage.tsx             — Future feature
src/pages/MLPage.tsx                  — Future feature
src/pages/AlertsPage.tsx              — Alerts go to Telegram + Risk page
```

---

## Task 1: Types, API Service, Routing, Sidebar

**Files:**
- Modify: `src/types/index.ts`
- Modify: `src/services/api.ts`
- Modify: `src/App.tsx`
- Modify: `src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Add new types to types/index.ts**

Add these types after the existing types (keep all existing types — old pages may still reference them during transition):

```typescript
// === Phase 1-4 Types ===

export interface RiskConfig {
  id: number;
  userId: number;
  riskPerTradePct: number;
  maxEffectiveLeverage: number;
  dailyLossHaltPct: number;
  maxDrawdownPct: number;
  maxConcurrentPositions: number;
  maxStopDistancePct: number;
  minRiskRewardRatio: number;
  feeImpactThreshold: number;
  executionMode: 'AUTONOMOUS' | 'HUMAN_IN_LOOP';
  createdAt: string;
  updatedAt: string;
}

export interface SystemHealth {
  status: string;
  timestamp: string;
  version: string;
  components: {
    strategies: { status: string; count: number };
    riskEngine: { status: string };
    telegram: { status: string };
    account: { balance: number; peakEquity: number; openPositions: number };
    fundingRate: { current: number; historySize: number };
  };
}

export interface SystemVersion {
  name: string;
  version: string;
  phase: string;
}

export interface DeltaConnectionStatus {
  hasCredentials: boolean;
  environment: string;
}

export interface TradeLog {
  id: number;
  userId: number;
  tradeId: string;
  symbol: string;
  direction: 'BUY' | 'SELL';
  strategyName: string;
  entryPrice: number;
  stopLossPrice: number;
  takeProfitPrice: number;
  positionSize: number;
  effectiveLeverage: number;
  confidence: number;
  explanation: {
    bias?: string;
    zone?: string;
    entryTrigger?: string;
    fundingContext?: string;
    riskCalc?: string;
    lesson?: string;
  };
  outcome?: {
    result: 'WIN' | 'LOSS';
    pnl: number;
    rMultiple: number;
    exitPrice: number;
    postTradeLesson?: string;
  };
  status: 'PENDING' | 'OPEN' | 'CLOSED' | 'CANCELLED';
  executionMode: string;
  openedAt: string;
  closedAt?: string;
  createdAt: string;
}

export interface MultiTFBacktestResult {
  initialCapital: number;
  finalCapital: number;
  totalReturnPct: number;
  sharpeRatio: number;
  maxDrawdownPct: number;
  winRate: number;
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  profitFactor: number;
  totalFees: number;
  totalSlippage: number;
  totalFundingPaid: number;
  equityCurve: number[];
  tradeLog: Record<string, unknown>[];
  perStrategyWinRate: Record<string, number>;
}

export type StrategyModelType =
  | 'MOMENTUM' | 'VOLATILITY' | 'MACRO' | 'CORRELATION' | 'REGIME'
  | 'TREND_CONTINUATION' | 'MEAN_REVERSION' | 'FUNDING_SENTIMENT';
```

- [ ] **Step 2: Add new API calls to api.ts**

Add these methods to the existing `api` object at the end of api.ts:

```typescript
  // === Phase 1-4 APIs ===

  // Delta Exchange (via backend)
  getDeltaProducts: (testnet = true) =>
    request<any>(`${API_BASE}/delta/products?testnet=${testnet}`, { headers: headers() }),

  getDeltaTicker: (symbol: string, testnet = true) =>
    request<any>(`${API_BASE}/delta/ticker/${symbol}?testnet=${testnet}`, { headers: headers() }),

  getDeltaOrderBook: (productId: number, depth = 20, testnet = true) =>
    request<any>(`${API_BASE}/delta/orderbook/${productId}?depth=${depth}&testnet=${testnet}`, { headers: headers() }),

  saveDeltaCredentials: (apiKey: string, apiSecret: string, testnet = true) =>
    request<{ status: string; environment: string }>(`${API_BASE}/delta/credentials`, {
      method: 'POST', headers: headers(),
      body: JSON.stringify({ apiKey, apiSecret, testnet }),
    }),

  deleteDeltaCredentials: (testnet = true) =>
    request<void>(`${API_BASE}/delta/credentials?testnet=${testnet}`, {
      method: 'DELETE', headers: headers(),
    }),

  getDeltaConnectionStatus: (testnet = true) =>
    request<DeltaConnectionStatus>(`${API_BASE}/delta/connection-status?testnet=${testnet}`, { headers: headers() }),

  // Risk Config
  getRiskConfig: () =>
    request<RiskConfig>(`${API_BASE}/risk-config`, { headers: headers() }),

  updateRiskConfig: (config: Partial<RiskConfig>) =>
    request<RiskConfig>(`${API_BASE}/risk-config`, {
      method: 'PUT', headers: headers(),
      body: JSON.stringify(config),
    }),

  // System
  getSystemHealth: () =>
    request<SystemHealth>(`${API_BASE}/system/health`, { headers: headers() }),

  getSystemVersion: () =>
    request<SystemVersion>(`${API_BASE}/system/version`, { headers: headers() }),

  // Trade Logs
  getTradeLogs: () =>
    request<TradeLog[]>(`${API_BASE}/trade-logs`, { headers: headers() }).catch(() => [] as TradeLog[]),

  getTradeLog: (tradeId: string) =>
    request<TradeLog>(`${API_BASE}/trade-logs/${tradeId}`, { headers: headers() }),

  // Multi-TF Backtest
  runMultiTFBacktest: (config: { initialCapital?: number; slippageBps?: number }) =>
    request<MultiTFBacktestResult>(`${API_BASE}/backtests/multi-tf`, {
      method: 'POST', headers: headers(),
      body: JSON.stringify(config),
    }),
```

Add the new type imports at the top of api.ts.

- [ ] **Step 3: Update App.tsx routes**

Replace the route block inside `<Route element={<ProtectedRoute />}>` with:

```tsx
<Route path="/firm-setup" element={<FirmSetupPage />} />
<Route path="/dashboard" element={<DashboardPage />} />
<Route path="/trade" element={<TradePage />} />
<Route path="/strategies" element={<StrategiesPage />} />
<Route path="/backtest" element={<BacktestPage />} />
<Route path="/trade-log" element={<TradeLogPage />} />
<Route path="/risk" element={<RiskPage />} />
<Route path="/settings" element={<SettingsPage />} />
<Route path="/" element={<Navigate to="/dashboard" replace />} />
<Route path="*" element={<Navigate to="/dashboard" replace />} />
```

Update imports: remove old page imports (MarketPage, OrdersPage, AgentsPage, AIIntelPage, MLPage, AlertsPage), add new imports (TradePage, TradeLogPage).

- [ ] **Step 4: Update Sidebar navItems**

Replace the `navItems` array in Sidebar.tsx with:

```typescript
const navItems = [
  { label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
  { label: 'Trade', icon: 'candlestick_chart', path: '/trade' },
  { label: 'Strategies', icon: 'psychology', path: '/strategies' },
  { label: 'Backtest', icon: 'science', path: '/backtest' },
  { label: 'Trade Log', icon: 'menu_book', path: '/trade-log' },
  { label: 'Risk', icon: 'shield', path: '/risk' },
  { label: 'Settings', icon: 'settings', path: '/settings' },
];
```

- [ ] **Step 5: Create placeholder pages for new routes**

Create `src/pages/TradePage.tsx` and `src/pages/TradeLogPage.tsx` as simple placeholders:

```tsx
export default function TradePage() {
  return <div><h1 className="page-header">Trade</h1><p>Coming soon...</p></div>;
}
```

Same pattern for TradeLogPage.

- [ ] **Step 6: Verify the app compiles and routes work**

Run: `cd /Users/abhinavunmesh/Desktop/QuantEdge_Platform/frontend && npm run dev`

Open http://localhost:5173, verify:
- Login works
- Sidebar shows 7 items
- All routes navigate correctly
- No console errors

- [ ] **Step 7: Commit**

```bash
git commit -m "feat: update frontend foundation — new types, API calls, routes, sidebar for 7-page redesign"
```

---

## Task 2: Dashboard Page (Bloomberg-style)

**Files:**
- Rewrite: `src/pages/DashboardPage.tsx`

- [ ] **Step 1: Rewrite DashboardPage.tsx**

Build the Bloomberg-style dashboard with:
- **6-column KPI ticker bar** (responsive: 3x2 on tablet, 2x3 on mobile): Equity, Daily P&L, Eff. Leverage, Win Rate, Sharpe, Drawdown
- **Equity curve** (Recharts AreaChart, full width, with gradient fill and timeframe selector 1D|1W|1M|ALL)
- **2x2 grid** (responsive: stacks on mobile):
  - Open Positions card (table with pair, side, entry, P&L — color coded)
  - Strategy Status card (3 strategies with status dots, trade counts)
  - Risk Budget card (4 progress bars: daily loss, drawdown, positions, leverage)
  - Live Feed card (timestamped event list)
- **Empty state**: When no system health data, show action cards ("Connect Delta Exchange", "Run First Backtest", "Start Paper Trading")

Data sources:
- `api.getSystemHealth()` for all component status
- `api.getRiskConfig()` for risk parameters
- Use React Query `useQuery` with 10s refetchInterval for live updates

All values should gracefully handle 0/null (the system just started, no trades yet).

- [ ] **Step 2: Test in browser**

Verify at 1440px, 768px, 375px widths. Check:
- KPIs wrap correctly
- Charts resize
- Empty state shows when no data
- No overflow or scroll issues

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: redesign Dashboard — Bloomberg KPI ticker, equity curve, positions, risk budget"
```

---

## Task 3: Trade Page (Single-Screen)

**Files:**
- Create: `src/pages/TradePage.tsx` (replace placeholder)

- [ ] **Step 1: Build TradePage.tsx**

L-shaped layout:
- **Left 70%**: Multi-TF candlestick chart using lightweight-charts
  - Symbol selector buttons (BTCUSD, ETHUSD) + dropdown
  - Timeframe buttons: 15m | 1h | 4h
  - Full OHLCV candlestick chart with volume pane below
  - Data from: `api.getDeltaTicker(symbol)` and Delta Exchange API for candle data
- **Right 30%**: Order book + order entry
  - Order book: asks (red, top) / bids (green, bottom) with depth bars, spread in middle
  - Order entry form: BUY/SELL toggle (green/red), LIMIT/MARKET toggle, size input, SL/TP inputs, submit
  - Submit calls: `api.placeOrder()` or new Delta backend endpoint
- **Bottom**: Tabbed table (Open Positions | Open Orders | Trade History)
  - Each tab is a responsive table with action buttons (close position, cancel order)

Responsive:
- Desktop: L-shape as described
- Tablet: Chart full width, order book + entry below
- Mobile: Everything stacked vertically, tables become horizontal-scroll

Note: For now, the order book and order placement may return empty/mock data since the live Delta Exchange WebSocket data feed is Phase 5 work. The UI should handle empty states gracefully.

- [ ] **Step 2: Test in browser at all breakpoints**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add Trade page — single-screen L-shape with multi-TF chart, order book, positions"
```

---

## Task 4: Strategies Page

**Files:**
- Rewrite: `src/pages/StrategiesPage.tsx`

- [ ] **Step 1: Rewrite StrategiesPage.tsx**

- **Top bar**: Execution mode toggle (AUTONOMOUS | HUMAN_IN_LOOP) — calls `api.updateRiskConfig({ executionMode })`. Status indicator showing current mode.
- **3 strategy cards** (responsive grid, min 280px):
  - Each card: strategy name, status dot (Active/Waiting/Paused), signals count, win rate, last signal, confidence bar
  - Color-coded top border per strategy (green for Trend, cyan for MeanRev, purple for Funding)
  - "View Signals" expand button
- **Signals table** below cards:
  - Columns: time, pair, side, confidence, R:R, status (Executed/Rejected/Expired)
  - Status badges: green check (executed), red X (rejected), grey clock (expired)
  - Expandable rows: click to show full trade explanation (bias, zone, trigger, funding, lesson)
  - Filter by strategy dropdown

Data: For now, strategies are hardcoded display cards (Trend Continuation, Mean Reversion, Funding Sentiment) since the backend doesn't have a "list multi-TF strategies with stats" endpoint yet. The signals table pulls from `api.getTradeLogs()`.

- [ ] **Step 2: Test in browser**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: redesign Strategies page — 3 multi-TF strategy cards with execution mode toggle"
```

---

## Task 5: Backtest Page

**Files:**
- Rewrite: `src/pages/BacktestPage.tsx`

- [ ] **Step 1: Rewrite BacktestPage.tsx**

- **Config bar**: Symbol dropdown (BTCUSD, ETHUSD), capital input (default $500), slippage input (default 10 bps), date range picker, "Run Backtest" button
- **Loading state**: Animated progress bar while backtest runs
- **8-KPI row** (visible after backtest completes): Return%, Sharpe, MaxDD, WinRate, ProfitFactor, TotalTrades, TotalFees, FundingPaid — using existing KpiCard component pattern
- **Equity curve**: Recharts AreaChart with green gradient fill. If data includes drawdown periods, overlay red shading.
- **Per-strategy breakdown**: 3 cards showing each strategy's independent win rate, trades, profit factor — from `perStrategyWinRate` in the result
- **Trade log table**: Expandable rows from backtest's `tradeLog` array

API call: `api.runMultiTFBacktest({ initialCapital, slippageBps })`. Note: the backend endpoint currently returns null (needs candle data wired in Phase 5). Handle this gracefully — show "Backtest engine ready. Historical data loading will be available when connected to Delta Exchange." message.

- [ ] **Step 2: Test in browser**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: redesign Backtest page — multi-TF config, 8 KPIs, equity curve, per-strategy breakdown"
```

---

## Task 6: Trade Log Page (Learn While Earning)

**Files:**
- Create: `src/pages/TradeLogPage.tsx` (replace placeholder)

- [ ] **Step 1: Build TradeLogPage.tsx**

- **Filter bar**: All/Wins/Losses toggle buttons, strategy dropdown, search input for trade ID
- **Summary row**: Total trades, overall win rate, total P&L (color-coded)
- **Accordion trade cards**:
  - Collapsed: trade ID, pair, direction badge (LONG green / SHORT red), P&L, strategy name, confidence, R-multiple
  - Left border: 3px green for wins, 3px red for losses
  - Click to expand — shows explanation sections:
    - **Bias** (4H): full explanation text
    - **Zone** (1H): zone identification
    - **Entry Trigger** (15M): what triggered the entry
    - **Funding Context**: funding rate analysis
    - **Risk Calculation**: position sizing math
    - **Lesson**: educational takeaway (highlighted with a lightbulb icon)
  - If outcome exists: show exit price, P&L, R-multiple, post-trade lesson
- **Empty state**: "No trades yet. Your trade history and educational explanations will appear here once the strategies start generating signals."

Data: `api.getTradeLogs()` — currently returns empty array (trade logs populate when strategies run live). UI must handle empty gracefully.

- [ ] **Step 2: Test in browser**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add Trade Log page — Learn While Earning accordion with explanations"
```

---

## Task 7: Risk Page

**Files:**
- Rewrite: `src/pages/RiskPage.tsx`

- [ ] **Step 1: Rewrite RiskPage.tsx**

- **Status banner**: "RISK ENGINE: ARMED (7 checks active)" with green pulsing dot. Changes to red "HALTED" if daily loss or drawdown breached.
- **4 progress bars** (budget utilization):
  - Daily Loss: used/limit with percentage (cyan fill)
  - Drawdown: current/max (green when healthy, orange when >10%, red when >13%)
  - Open Positions: count/max (blue fill)
  - Effective Leverage: current/max (blue fill)
  - All bars animated on load
- **Configurable parameters table**:
  - 8 rows (one per risk parameter)
  - Columns: Parameter name, Current value, Edit button
  - Click Edit → inline number input → Save calls `api.updateRiskConfig()`
  - Toast notification on save success
- **Risk event log**:
  - Timestamped list of recent risk decisions
  - Green check for approved trades, red X for rejected (with reason)
  - Currently empty — populates when strategies run. Show "No risk events yet" empty state.

Data: `api.getRiskConfig()` for parameters. `api.getSystemHealth()` for account state (balance, positions, leverage).

- [ ] **Step 2: Test in browser**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: redesign Risk page — 7-check dashboard, budget progress bars, configurable parameters"
```

---

## Task 8: Settings Page

**Files:**
- Rewrite: `src/pages/SettingsPage.tsx`

- [ ] **Step 1: Rewrite SettingsPage.tsx**

4 tab sections (use simple tab buttons, not sidebar — the page sidebar already exists):

**Tab 1: Delta Exchange**
- Testnet/Production toggle switch
- API Key input (masked, type="password" with show/hide toggle)
- API Secret input (masked)
- "Save Credentials" button → `api.saveDeltaCredentials()`
- "Test Connection" button → `api.getDeltaConnectionStatus()`
- Connection status badge (green "Connected" / grey "Not configured")
- "Delete Credentials" button (red, with confirmation)

**Tab 2: Telegram**
- Bot status indicator (from system health)
- Chat ID display (read-only)
- "Send Test Message" button (calls Telegram API)
- Webhook URL display (read-only info text)

**Tab 3: Execution**
- Mode toggle: AUTONOMOUS / HUMAN_IN_LOOP → `api.updateRiskConfig({ executionMode })`
- Signal timeout slider (60-300 seconds)
- Active pairs checkboxes (BTCUSD, ETHUSD)
- Starting capital input + "Initialize Account" button

**Tab 4: System**
- System health cards from `api.getSystemHealth()` — each component with status dot
- Version info from `api.getSystemVersion()`
- Database, Redis, Strategies, Risk Engine, Telegram — all with status indicators

- [ ] **Step 2: Test in browser**

Verify:
- Save Delta Exchange credentials → API call succeeds
- Test connection → shows status
- Execution mode toggle → updates
- System health → shows live status
- All tabs work, responsive on mobile

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: redesign Settings — Delta Exchange credentials, Telegram status, execution mode, system health"
```

---

## Summary

| Task | Page | Description |
|------|------|-------------|
| 1 | Foundation | Types, API, routes, sidebar — wiring for all pages |
| 2 | Dashboard | Bloomberg KPI ticker, equity curve, 2x2 grid |
| 3 | Trade | Single-screen L-shape, multi-TF chart, order book |
| 4 | Strategies | 3 strategy cards, execution mode, signals table |
| 5 | Backtest | Multi-TF config, 8 KPIs, equity curve, per-strategy |
| 6 | Trade Log | Learn While Earning accordion with explanations |
| 7 | Risk | 7-check dashboard, progress bars, editable params |
| 8 | Settings | Delta creds, Telegram, execution mode, system health |

**Total: 8 tasks. Each produces one working, responsive page testable in the browser.**

**Quality requirements:**
- Every page responsive at 375px, 768px, 1440px+
- Empty states for all data-dependent sections
- Loading states for all API calls
- Error handling with toast notifications
- No hardcoded widths that break on resize
- Color-coded values (green positive, red negative) throughout

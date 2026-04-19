# QuantEdge Platform

> A production-grade algorithmic crypto trading system with multi-timeframe strategies, hard-coded risk guardrails, Telegram control, real-time Binance data, and an educational "Learn While Earning" engine — built for Delta Exchange perpetual futures.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              React 19 + TypeScript + Tailwind                │
│  Dashboard | Trade | Strategies | Backtest | Trade Log       │
│  Risk | Settings          (7 pages, Bloomberg-density UI)    │
└───────────────────────┬─────────────────────────────────────┘
                        │ REST + WebSocket
┌───────────────────────▼─────────────────────────────────────┐
│              Java Spring Boot 3.5 (port 8080)                │
│                                                              │
│  Strategy Engine ─── Risk Engine (7 checks) ─── Execution    │
│   ├─ Trend Continuation (4H→1H→15M)          ├─ Autonomous  │
│   ├─ Mean Reversion (Bollinger/RSI extremes)  └─ Human-in-   │
│   └─ Funding Sentiment (OI + liquidation)        Loop (TG)   │
│                                                              │
│  Data Pipeline ─── Binance Futures API ─── Delta Exchange    │
│   ├─ 15m/1h/4h candle aggregation            (order exec)   │
│   ├─ Technical indicators (12+)                              │
│   └─ ML feature collection                                   │
│                                                              │
│  Telegram Bot (two-way) ── Backtest Engine ── Trade Logger   │
└──┬──────────┬──────────┬──────────┬─────────────────────────┘
   │          │          │          │
   ▼          ▼          ▼          ▼
PostgreSQL  Redis     Binance    Python ML Service
(5432)      (6379)    (data)      (port 5001)
                                 XGBoost | LSTM | Markowitz
```

---

## What This Does

QuantEdge is an algo trading system that runs 3 multi-timeframe strategies on crypto perpetual futures (BTCUSD, ETHUSD) via Delta Exchange. It analyzes the market on 4H, 1H, and 15M timeframes simultaneously, generates trade signals with full educational explanations, runs every signal through a 7-check risk engine, and executes via Telegram approval or fully autonomous mode.

### Trading Strategies

| Strategy | Logic | Expected Win Rate |
|----------|-------|-------------------|
| **Trend Continuation** | 4H EMA bias → 1H pullback to support/resistance → 15M entry trigger (RSI + volume + MACD) | 58-65% |
| **Mean Reversion** | Bollinger Band (2.5σ) + RSI extremes → reversal at VWAP/mean target | 55-60% |
| **Funding Sentiment** | Extreme funding rate (3+ consecutive periods) + OI spike → liquidation cascade trade | 50-55% |

All strategies include a **funding rate confidence modifier** — when the crowd is overleveraged against your trade, confidence increases.

### Risk Engine (7 Hard Checks)

Every trade must pass ALL checks before reaching the exchange:

1. **Position Size** — max 1-2% of capital at risk per trade
2. **Effective Leverage** — capped at 5x (nominal 10-25x for margin only)
3. **Daily Loss Circuit Breaker** — halts at 5% daily loss
4. **Max Drawdown** — halts and closes all at 15%
5. **Concurrent Positions** — max 3 open trades, no duplicate symbols
6. **Stop-Loss Validation** — mandatory, correct direction, within 2%
7. **Fee Impact** — rejects trades where fees > 20% of risk amount

### Learn While Earning

Every trade generates a structured explanation:
- **Bias** (4H analysis) — why the market is trending this direction
- **Zone** (1H) — what support/resistance level was identified
- **Entry Trigger** (15M) — what specific candle pattern triggered entry
- **Funding Context** — what the funding rate tells us about crowd positioning
- **Risk Calculation** — exact position sizing math
- **Lesson** — educational takeaway linking to broader trading concepts

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | React 19, TypeScript 5.9, Vite 8, Tailwind CSS 4, Zustand, Recharts, Lightweight Charts 5 |
| **Backend** | Java 21, Spring Boot 3.5.11, Spring Security (JWT), Flyway, WebSocket (STOMP) |
| **Database** | PostgreSQL 15 (TimescaleDB compatible), 21 Flyway migrations |
| **Cache** | Redis 7 |
| **Market Data** | Binance Futures API (historical candles + real-time WebSocket) |
| **Execution** | Delta Exchange API (crypto perpetual futures, testnet + production) |
| **ML Features** | Funding rate, open interest, order book imbalance, basis spread (Binance) |
| **ML Models** | Python FastAPI — XGBoost, LSTM, portfolio optimization |
| **Notifications** | Telegram Bot (two-way: alerts out, commands in) |
| **AI** | Anthropic Claude API (agent intelligence, bias auditing) |
| **Monitoring** | Prometheus + Grafana, Spring Actuator |
| **CI** | GitHub Actions (unit tests on push/PR) |

---

## Frontend — 7 Pages

| Page | Description |
|------|-------------|
| **Dashboard** | Bloomberg-density overview: 6 KPI ticker, equity curve, positions, strategy status, risk budget, live feed |
| **Trade** | Single-screen L-shape: multi-TF candlestick chart (15m/1h/4h), order book, order entry, positions |
| **Strategies** | 3 strategy cards with live status, execution mode toggle (autonomous/human-in-loop), signals table |
| **Backtest** | Run backtests on real Binance data with realistic fees/slippage/funding. 8 KPIs, equity curve, per-strategy breakdown |
| **Trade Log** | "Learn While Earning" — accordion trade cards with full explanations (bias, zone, trigger, funding, lesson) |
| **Risk** | Risk engine dashboard: 4 budget progress bars, editable parameters, risk event log |
| **Settings** | Delta Exchange credentials (encrypted), Telegram status, execution mode, system health |

---

## Project Structure

```
QuantEdge_Platform/
├── QuantPlatformApplication/           # Java Spring Boot backend
│   ├── src/main/java/.../
│   │   ├── config/                     # Security, CORS, Redis, WebSocket, Encryption
│   │   ├── controller/                 # REST controllers (Delta, Risk, Backtest, Telegram, System)
│   │   ├── service/
│   │   │   ├── broker/                 # Paper, Alpaca, Delta Exchange adapters
│   │   │   ├── delta/                  # Delta Exchange REST client + WebSocket
│   │   │   ├── telegram/              # Telegram bot service + command handler
│   │   │   ├── pipeline/             # Candle aggregation, indicators, data pipeline
│   │   │   ├── risk/                  # 7-check TradeRiskEngine
│   │   │   └── ...                    # Orchestrator, backtest, account tracking
│   │   ├── engine/
│   │   │   ├── model/                 # Candle, TimeFrame, TradeSignal, RiskParameters, etc.
│   │   │   ├── strategy/             # 3 multi-TF strategies + old strategies
│   │   │   ├── util/                  # SwingDetector, MathUtils
│   │   │   ├── BacktestEngine.java
│   │   │   └── MultiTimeFrameBacktestEngine.java
│   │   ├── client/                    # Binance historical + market data clients
│   │   ├── model/entity/             # 18 JPA entities
│   │   └── repository/               # 14 Spring Data JPA repositories
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/             # Flyway V1–V21
│   ├── src/test/java/                # 100 unit tests
│   ├── docker-compose.yml             # DB + Redis (local dev)
│   ├── docker-compose.full.yml        # Full stack with monitoring
│   └── Dockerfile
│
├── frontend/                           # React 19 SPA
│   ├── src/
│   │   ├── pages/                      # 7 pages (Dashboard, Trade, Strategies, etc.)
│   │   ├── components/                 # Layout (Sidebar, TopBar, LiveTicker) + UI
│   │   ├── services/                   # API client (api.ts) + Delta Exchange types
│   │   ├── stores/                     # Zustand (auth, notifications)
│   │   └── types/                      # TypeScript types (Phase 1-4 + legacy)
│   └── package.json
│
├── ml-service/                         # Python ML microservice
│   ├── main.py                         # FastAPI (port 5001)
│   ├── model.py                        # XGBoost + LSTM
│   ├── feature_engine.py               # Technical indicators
│   ├── optimizer.py                    # Portfolio optimization
│   └── requirements.txt
│
├── docker-compose.prod.yml             # Production deployment (Oracle Cloud / Hetzner)
├── monitoring/                         # Prometheus + Grafana configs
├── scripts/                            # deploy.sh, heartbeat.sh, backup-db.sh
├── .github/workflows/test.yml          # CI: run tests on push/PR
├── .env.example                        # Environment variable template
└── CLAUDE.md                           # AI assistant project guardrails
```

---

## Getting Started

### Prerequisites

- **Java 21** — `brew install openjdk@21`
- **Docker Desktop** — `brew install --cask docker` (for PostgreSQL + Redis)
- **Node.js 18+** — `brew install node`
- **Python 3.9+** — for the ML service (optional)

### 1. Start Database + Redis

```bash
cd QuantPlatformApplication
docker compose up -d
```

### 2. Start Backend

```bash
cd QuantPlatformApplication
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Required environment variables
export DB_HOST=localhost DB_USER=postgres DB_PASS=Walktorem@12
export REDIS_HOST=localhost
export ENCRYPTION_KEY="your-32-char-encryption-key-here"
export KAFKA_ENABLED=false

# Optional (for Telegram bot)
export TELEGRAM_BOT_TOKEN=your-bot-token
export TELEGRAM_CHAT_ID=your-chat-id
export TELEGRAM_ENABLED=true

./mvnw spring-boot:run
```

Backend starts on **http://localhost:8080**. Flyway auto-runs 21 migrations.

### 3. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:3000** with API proxy to backend.

### 4. Start ML Service (Optional)

```bash
cd ml-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

ML service starts on **http://localhost:5001**.

### 5. Use the Platform

1. Open **http://localhost:3000**
2. Register an account
3. Go to **Settings** → save Delta Exchange API credentials
4. Go to **Backtest** → run a backtest with real Binance data
5. Go to **Trade** → see real-time BTC/ETH charts
6. Go to **Strategies** → toggle execution mode and monitor signals

---

## API Reference

### Core APIs (port 8080)

| Group | Endpoints |
|-------|----------|
| **Auth** | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` |
| **Delta Exchange** | `POST /api/v1/delta/credentials`, `GET /api/v1/delta/connection-status`, `GET /api/v1/delta/products`, `GET /api/v1/delta/ticker/{symbol}` |
| **Risk Config** | `GET /api/v1/risk-config`, `PUT /api/v1/risk-config` |
| **Backtest** | `POST /api/v1/backtests/multi-tf`, `GET /api/v1/backtests/multi-tf/candles` |
| **System** | `GET /api/v1/system/health`, `GET /api/v1/system/version` |
| **Telegram** | `POST /api/v1/telegram/webhook` |
| **ML Features** | `POST /api/v1/ml/features/collect`, `GET /api/v1/ml/features/recent` |
| **Strategies** | `GET/POST /api/v1/strategies`, `POST /{id}/execute` |
| **Orders** | `POST /api/v1/orders`, `GET /api/v1/orders` |
| **Risk** | `GET /api/v1/risk/var/{symbol}`, `/portfolio` |
| **Health** | `GET /actuator/health`, `GET /actuator/prometheus` |

### Telegram Bot Commands

| Command | Action |
|---------|--------|
| `/status` | Current equity, positions, daily P&L |
| `/positions` | Open positions with unrealized P&L |
| `/approve` | Approve pending trade signal |
| `/reject` | Reject pending trade signal |
| `/stop` | Pause all trading |
| `/resume` | Resume trading |
| `/close_all` | Emergency close all positions |
| `/mode auto` | Switch to autonomous execution |
| `/mode manual` | Switch to human-in-loop |
| `/risk` | Show risk parameters |
| `/today` | Today's trade history |

---

## Database Migrations

| Migration | Table | Purpose |
|-----------|-------|---------|
| V1–V16 | Various | Users, market data, strategies, agents, orders, positions, alerts, ML signals, firm profile |
| V17 | `risk_config` | Per-user configurable risk parameters |
| V18 | `delta_credentials` | Encrypted Delta Exchange API credentials |
| V19 | `trade_logs` | Trade history with JSONB explanations (Learn While Earning) |
| V20 | `pending_signals` | Trade signals awaiting human approval |
| V21 | `ml_feature_snapshots` | ML training features (funding, OI, book imbalance, basis) |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_USER` | `postgres` | Database username |
| `DB_PASS` | — | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `ENCRYPTION_KEY` | — | AES-256 key for encrypting API credentials |
| `TELEGRAM_BOT_TOKEN` | — | Telegram bot token from @BotFather |
| `TELEGRAM_CHAT_ID` | — | Your Telegram chat ID |
| `TELEGRAM_ENABLED` | `false` | Enable Telegram notifications |
| `ANTHROPIC_API_KEY` | — | Claude API key (for AI agent features) |
| `FRED_API_KEY` | `DEMO_KEY` | FRED API key (for macro data) |
| `ML_FEATURE_COLLECTION` | `false` | Enable scheduled ML feature collection |
| `KAFKA_ENABLED` | `false` | Enable Kafka event streaming |

---

## Testing

```bash
cd QuantPlatformApplication

# Run all 100 unit tests
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test

# Run specific test suites
./mvnw test -Dtest=TradeRiskEngineTest          # 20 risk engine tests
./mvnw test -Dtest=IndicatorCalculatorTest       # 10 indicator tests
./mvnw test -Dtest=TrendContinuationStrategyTest # 8 strategy tests
./mvnw test -Dtest=MultiTimeFrameBacktestEngineTest # 8 backtest tests
```

---

## Docker Deployment

```bash
# Local development (DB + Redis only)
cd QuantPlatformApplication && docker compose up -d

# Production (all services)
cp .env.example .env  # edit with your credentials
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

---

## Monitoring

| Service | URL | Credentials |
|---------|-----|-------------|
| Spring Actuator | http://localhost:8080/actuator/health | — |
| System Health | http://localhost:8080/api/v1/system/health | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3001 | admin / quantedge |

---

## Roadmap

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 1: Foundation | Done | Delta Exchange adapter, indicators, risk engine, encryption |
| Phase 2: Trading Core | Done | 3 strategies, orchestrator, trade explanations |
| Phase 3: Interface | Done | Telegram bot, backtest engine, live pipeline |
| Phase 4: Deployment | Done | Docker, monitoring, CI, scripts |
| Frontend Redesign | Done | 7-page Bloomberg-style UI |
| Data Integration | Done | Binance historical data, live charts, ML features |
| Phase 5: Validation | Next | Paper trading on Delta Exchange testnet (2-3 months) |
| Phase 6: ML Evolution | Planned | XGBoost meta-filter on 50+ features (Approach C) |
| Phase 7: SaaS | Planned | Multi-tenancy, subscriptions, strategy marketplace |

---

## License

Private — not open source.

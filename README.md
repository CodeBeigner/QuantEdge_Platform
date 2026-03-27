# QuantEdge Platform

> A full-stack quantitative trading firm OS with AI-powered agents, ML signal generation, real-time market data, and trade execution via Delta Exchange — built for learning, experimentation, and live paper trading.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              React 19 + TypeScript + Tailwind                │
│  Dashboard | Market | Strategies | Backtest | Agents        │
│  Orders | Risk | ML Intelligence | AI Intel | Settings      │
└───────────────────────┬─────────────────────────────────────┘
                        │ REST + WebSocket (STOMP)
┌───────────────────────▼─────────────────────────────────────┐
│              Java Spring Boot 3.5 (port 8080)                │
│  Auth (JWT) | Market Data | Strategy Execution | Backtesting │
│  Order Mgmt | Risk Engine | Broker Adapters | Claude AI      │
└──┬──────────┬──────────┬──────────┬─────────────────────────┘
   │          │          │          │
   ▼          ▼          ▼          ▼
PostgreSQL  Redis     Kafka     Python ML Service
(5432)      (6379)    (9092)      (port 5001)
                     optional    XGBoost | LSTM | Markowitz
```

**Trade Execution:** Delta Exchange (testnet/production) for crypto derivatives.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | React 19, TypeScript, Vite 8, Tailwind CSS 4, Zustand, Recharts, Lightweight Charts |
| **Backend** | Java 21, Spring Boot 3.5, Spring Security (JWT), Flyway, WebSocket (STOMP) |
| **Database** | PostgreSQL 15 (TimescaleDB compatible) |
| **Cache** | Redis 7 |
| **Messaging** | Apache Kafka (optional — app runs without it) |
| **ML Service** | Python 3.9+, FastAPI, XGBoost, PyTorch (LSTM), scikit-learn, scipy |
| **AI** | Anthropic Claude API for agent intelligence |
| **Exchange** | Delta Exchange API (testnet + production) |
| **Monitoring** | Prometheus + Grafana (optional, via Docker) |

---

## Features

### Trading Firm OS
- **Firm Setup** — Configure firm profile, persona, and trading focus
- **JWT Authentication** — Secure register/login with role-based access
- **Real Market Data** — Yahoo Finance (OHLCV) + FRED API (macro indicators)
- **Live WebSocket Ticker** — Real-time price updates via STOMP

### Strategy & Execution
- **5 Built-in Strategies** — Momentum, Volatility, Macro/Rate, Correlation, Regime
- **Strategy Auto-Generation** — AI-suggested strategy parameters
- **Backtesting Engine** — Equity curve, Sharpe ratio, max drawdown, win rate
- **Trading Agents** — Automated strategy execution on cron schedules with lifecycle management
- **Agent Consensus** — Multi-agent decision pipeline (research, risk, compliance)
- **Order Management** — BUY/SELL with paper and live broker execution

### Broker Integration
- **Paper Trading** — Simulated fills with 1–5 bps slippage modeling
- **Delta Exchange** — Crypto derivatives trading (testnet + production)
- **Alpaca Adapter** — US equities integration (pluggable)
- **Broker Manager** — Switch between paper/live brokers at runtime

### ML & Intelligence
- **XGBoost Signal Model** — Next-day direction prediction (BUY/SELL/HOLD)
- **LSTM Signal Model** — Deep learning time-series predictions
- **Ensemble Predictions** — Combined XGBoost + LSTM signals
- **Feature Engineering** — RSI, MACD, Bollinger Bands, SMA crossover, ATR, OBV, VWAP
- **Portfolio Optimizer** — Markowitz mean-variance, Ledoit-Wolf robust, risk parity
- **Walk-Forward Validation** — Out-of-sample model evaluation
- **Information Coefficient** — Signal quality tracking

### Risk & Monitoring
- **Risk Engine** — VaR (95%), CVaR, max drawdown, position limit monitoring
- **Risk Alerts** — Auto-generated on drawdown breaches and position limit violations
- **Portfolio Tracking** — Real-time P&L, average cost, position weights
- **Signal Tracking** — Prediction accuracy and IC monitoring

---

## Project Structure

```
QuantEdge_Platform/
├── QuantPlatformApplication/           # Java Spring Boot backend
│   ├── src/main/java/.../
│   │   ├── config/                     # Security, CORS, Redis, WebSocket, Kafka, Metrics
│   │   ├── controller/                 # 15 REST controllers
│   │   ├── service/                    # 28 services (trading, ML, risk, broker, AI)
│   │   │   └── broker/                 # Paper, Alpaca, Delta Exchange adapters
│   │   ├── model/
│   │   │   ├── entity/                 # 15 JPA entities
│   │   │   └── dto/                    # Request/response DTOs
│   │   ├── repository/                 # 12 Spring Data JPA repositories
│   │   ├── engine/                     # BacktestEngine + StrategyExecutor
│   │   │   └── strategy/              # 5 strategy implementations
│   │   ├── client/                     # Yahoo Finance + FRED API clients
│   │   ├── event/                      # Kafka event publisher/consumer
│   │   └── security/                   # JWT filter + token provider
│   ├── src/main/resources/
│   │   ├── application.yml             # Main config
│   │   ├── application-prod.yml        # Production overrides
│   │   └── db/migration/              # Flyway V1–V16
│   ├── docker-compose.yml              # DB + Redis (local dev)
│   ├── docker-compose.full.yml         # Full stack with monitoring
│   ├── Dockerfile
│   ├── k8s/                            # Kubernetes manifests
│   └── pom.xml
│
├── frontend/                           # React SPA
│   ├── src/
│   │   ├── pages/                      # 13 pages (Dashboard, Market, Agents, etc.)
│   │   ├── components/                 # Layout + UI components
│   │   ├── services/                   # API client + Delta Exchange client
│   │   ├── stores/                     # Zustand state (auth, notifications)
│   │   ├── hooks/                      # WebSocket + keyboard hooks
│   │   └── types/                      # TypeScript type definitions
│   ├── vite.config.ts                  # Dev server with API proxy to :8080
│   ├── package.json
│   └── tsconfig.json
│
├── ml-service/                         # Python ML microservice
│   ├── main.py                         # FastAPI app (port 5001)
│   ├── model.py                        # XGBoost + LSTM signal models
│   ├── feature_engine.py               # Technical indicator computation
│   ├── optimizer.py                    # Portfolio optimization (Markowitz, risk parity)
│   ├── requirements.txt
│   └── models/                         # Saved model artifacts (gitignored)
│
├── .gitignore
└── README.md
```

---

## Getting Started

### Prerequisites

- **Java 21** (JDK) — `brew install openjdk@21`
- **PostgreSQL 15** — `brew install postgresql@15 && brew services start postgresql@15`
- **Redis** — `brew install redis && brew services start redis`
- **Node.js 18+** — `brew install node`
- **Python 3.9+** — for the ML service

### 1. Start Backend

```bash
cd QuantPlatformApplication
export JAVA_HOME=/opt/homebrew/opt/openjdk@21  # macOS Apple Silicon
./mvnw spring-boot:run
```

Backend starts on **http://localhost:8080**. Flyway auto-runs 16 migrations. The app works without Kafka (event publishing is skipped gracefully).

### 2. Start ML Service

```bash
cd ml-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

ML service starts on **http://localhost:5001**.

### 3. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:3000** (or next available port) with API proxy to the backend.

### 4. Use the Platform

1. Open the frontend URL in your browser
2. Register a new account (name, email, password)
3. Login to access the full platform

---

## API Reference

### Backend (port 8080)

| Group | Endpoints |
|-------|----------|
| **Auth** | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` |
| **Market Data** | `GET /api/v1/market-data/prices/{symbol}`, `/symbols` |
| **Strategies** | `GET/POST /api/v1/strategies`, `POST /{id}/execute` |
| **Backtests** | `POST /api/v1/backtests/run` |
| **Agents** | `GET/POST /api/v1/agents`, `POST /{id}/start`, `/{id}/stop` |
| **Orders** | `POST /api/v1/orders`, `GET /api/v1/orders`, `POST /{id}/cancel` |
| **Risk** | `GET /api/v1/risk/var/{symbol}`, `/positions`, `/portfolio` |
| **Alerts** | `GET /api/v1/alerts`, `/unacknowledged`, `POST /{id}/acknowledge` |
| **ML** | `POST /api/v1/ml/predict/{symbol}`, `/train/{symbol}`, `/optimize` |
| **Broker** | `GET /api/v1/broker/status`, `POST /api/v1/broker/switch` |
| **Firm** | `GET/POST /api/v1/firm/profile` |
| **Health** | `GET /actuator/health`, `/actuator/prometheus` |

### ML Service (port 5001)

| Endpoint | Purpose |
|----------|---------|
| `GET /health` | Service status + loaded models |
| `POST /train/{symbol}` | Train XGBoost model |
| `POST /predict/{symbol}` | Get ML signal (BUY/SELL/HOLD) |
| `GET /features/{symbol}` | Technical indicators |
| `POST /optimize` | Markowitz portfolio optimization |
| `POST /optimize-robust` | Ledoit-Wolf robust optimization |
| `POST /risk-parity` | Risk parity allocation |
| `POST /train-lstm/{symbol}` | Train LSTM model |
| `POST /predict-ensemble/{symbol}` | Combined XGBoost + LSTM signal |
| `POST /walk-forward/{symbol}` | Walk-forward validation |
| `GET /ic/{symbol}` | Information Coefficient |

---

## Database Migrations (Flyway)

| Migration | Table | Purpose |
|-----------|-------|---------|
| V1 | `users` | Authentication and user profiles |
| V2 | `market_data` | OHLCV price data (TimescaleDB hypertable) |
| V3 | `strategies` | Trading strategy definitions |
| V4 | `trading_agents` | Automated execution agents |
| V5 | `backtest_results` | Historical backtest outputs |
| V6 | `orders` | Order management system |
| V7 | `portfolio_positions` | Holdings and P&L tracking |
| V8 | `alerts` | Risk alert notifications |
| V9 | `ml_signals` | ML prediction persistence |
| V10 | `trading_agents` | Extended agent fields |
| V11 | `backtest_results` | Extended backtest metrics |
| V12 | `signal_tracking` | Signal prediction tracking |
| V13 | `firm_profile` | Firm configuration |
| V14 | `trading_agents` | Persona fields |
| V15 | `agent_conversations` | AI agent conversation history |
| V16 | `trading_agents` | Lifecycle state management |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_USER` | `postgres` | Database username |
| `DB_PASS` | — | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `KAFKA_ENABLED` | `false` | Enable Kafka event streaming |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `ANTHROPIC_API_KEY` | — | Claude API key (for AI agent features) |
| `FRED_API_KEY` | `DEMO_KEY` | FRED API key (for macro data) |

---

## Docker (Optional)

For running infrastructure via Docker instead of Homebrew:

```bash
# Database + Redis only
cd QuantPlatformApplication
docker compose up -d

# Full stack (includes Kafka, Prometheus, Grafana)
docker compose -f docker-compose.full.yml up -d
```

---

## Monitoring (Optional)

| Service | URL | Credentials |
|---------|-----|-------------|
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3001 | admin / admin |
| Spring Actuator | http://localhost:8080/actuator/health | — |

Requires Docker or the monitoring compose file: `docker compose -f docker-compose.monitoring.yml up -d`

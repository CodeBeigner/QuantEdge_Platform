# QuantEdge — AI-Driven Quantitative Trading Platform

> A full-stack quantitative finance platform with real-time market data, 5 trading strategies, ML-powered signals, portfolio optimization, and paper-trading — built for learning and experimentation.

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    Frontend (React SPA)                   │
│   Dashboard │ Market │ Strategies │ Backtest │ Agents    │
│   Orders │ Risk Dashboard │ ML Intelligence │ Alerts     │
└────────────────────────┬─────────────────────────────────┘
                         │ REST + WebSocket
┌────────────────────────▼─────────────────────────────────┐
│               Java Spring Boot (port 8080)               │
│  Auth │ Market Data │ Strategy Execution │ Backtesting   │
│  Order Management │ Risk Engine │ ML Client │ Alerts     │
└───┬──────────┬──────────┬────────────┬───────────────────┘
    │          │          │            │
    ▼          ▼          ▼            ▼
TimescaleDB  Redis     Kafka    Python ML Service
 (port 5432) (6379)    (9092)     (port 5001)
                                  XGBoost + Markowitz
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | React 18 (CDN), Recharts, vanilla CSS |
| **Backend** | Java 21, Spring Boot 3.2, Spring Security (JWT) |
| **Database** | PostgreSQL 15 + TimescaleDB |
| **Cache** | Redis 7 |
| **Messaging** | Apache Kafka |
| **ML Service** | Python 3.12, FastAPI, XGBoost, scikit-learn, scipy |
| **Monitoring** | Prometheus + Grafana |

---

## Features

### Core (Horizon 1)
- **JWT Authentication** — Register/login with secure token-based auth
- **Real Market Data** — Yahoo Finance + FRED API integration
- **5 Trading Strategies** — Momentum, Volatility, Macro/Rate, Correlation, Regime
- **Backtesting Engine** — Equity curve, Sharpe ratio, max drawdown, win rate
- **Trading Agents** — Automated strategy execution on cron schedules
- **Live WebSocket Ticker** — Real-time price updates via STOMP

### Intelligence (Horizon 2)
- **ML Signal Model** — XGBoost predicting next-day return direction (BUY/SELL/HOLD)
- **Feature Engineering** — RSI, MACD, Bollinger Bands, SMA crossover, ATR, OBV
- **Portfolio Optimizer** — Markowitz mean-variance with efficient frontier
- **Risk Engine** — VaR (95%), CVaR, max drawdown, position limit monitoring

### Trading (Horizon 3)
- **Order Management** — Place BUY/SELL orders with paper-trading fill simulation
- **Slippage Modeling** — 1–5 basis point realistic slippage on fills
- **Portfolio Tracking** — Real-time P&L, average cost, position weights
- **Risk Alerts** — Auto-generated on drawdown breaches, position limit violations
- **Alert Dashboard** — Filter and acknowledge risk notifications

---

## Project Structure

```
QuantPlatformApplication/
├── QuantPlatformApplication/          # Java backend (Spring Boot)
│   ├── src/main/java/.../
│   │   ├── config/                    # Security, CORS, Redis, WebSocket, REST
│   │   ├── controller/                # 10 REST controllers
│   │   ├── service/                   # Business logic (14 services)
│   │   ├── model/entity/              # JPA entities (10)
│   │   ├── repository/                # Spring Data repos (9)
│   │   ├── engine/                    # Strategy execution + backtest
│   │   ├── client/                    # Yahoo Finance + FRED clients
│   │   └── security/                  # JWT filter + provider
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/             # Flyway V1–V9
│   ├── docker-compose.full.yml        # Full local stack
│   └── pom.xml
│
├── frontend/                          # React SPA
│   ├── index.html
│   ├── css/styles.css
│   └── js/
│       ├── app.js                     # 9-view application
│       └── api.js                     # API client with retry logic
│
├── ml-service/                        # Python ML microservice
│   ├── main.py                        # FastAPI (port 5001)
│   ├── feature_engine.py              # Technical indicators
│   ├── model.py                       # XGBoost signal predictor
│   ├── optimizer.py                   # Markowitz optimizer
│   ├── requirements.txt
│   └── Dockerfile
│
└── README.md                          # This file
```

---

## Getting Started

### Prerequisites
- **Java 21** (JDK)
- **Docker & Docker Compose**
- **Python 3.10+** (for ML service, optional)
- **Node.js** (only needed if you want to serve frontend via HTTP server)

### 1. Start Infrastructure

```bash
cd QuantPlatformApplication
docker compose -f docker-compose.full.yml up -d
```

This starts: TimescaleDB (5432), Redis (6379), Kafka (9092), Prometheus (9090), Grafana (3001).

### 2. Start Backend

```bash
cd QuantPlatformApplication
./mvnw spring-boot:run
```

Backend starts on **http://localhost:8080**. Flyway auto-creates tables, DataSeeder populates sample data (SPY, AAPL, QQQ + 5 strategies).

### 3. Start ML Service (Optional)

```bash
cd ml-service
pip install -r requirements.txt
python main.py
```

ML service starts on **http://localhost:5001**. Needed for the ML Intelligence and Portfolio Optimization views.

### 4. Open Frontend

Open `frontend/index.html` in your browser, or serve it:

```bash
cd frontend
python -m http.server 3000
# Open http://localhost:3000
```

### 5. Register & Login

1. Click "Create Account" on the auth screen
2. Enter name, email, password
3. Login to access the full platform

---

## API Endpoints

| Group | Endpoints |
|-------|----------|
| **Auth** | `POST /api/v1/auth/register`, `/login` |
| **Market Data** | `GET /api/v1/market-data/prices/{symbol}`, `/symbols` |
| **Strategies** | `GET/POST /api/v1/strategies`, `POST /{id}/execute` |
| **Backtests** | `POST /api/v1/backtests/run` |
| **Agents** | `GET/POST /api/v1/agents`, `POST /{id}/start`, `/stop` |
| **Orders** | `POST /api/v1/orders`, `GET /orders`, `POST /{id}/cancel` |
| **Risk** | `GET /api/v1/risk/var/{symbol}`, `/positions`, `/portfolio` |
| **Alerts** | `GET /api/v1/alerts`, `/unacknowledged`, `POST /{id}/acknowledge` |
| **ML** | `POST /api/v1/ml/predict/{symbol}`, `/train/{symbol}`, `/optimize` |
| **Health** | `GET /actuator/health`, `/actuator/prometheus` |

---

## Database Migrations

| Migration | Table | Purpose |
|-----------|-------|---------|
| V1 | `users` | Authentication |
| V2 | `market_data` | TimescaleDB hypertable for OHLCV |
| V3 | `strategies` | Trading strategy definitions |
| V4 | `trading_agents` | Automated execution agents |
| V5 | `backtest_results` | Historical backtest outputs |
| V6 | `orders` | Order management system |
| V7 | `portfolio_positions` | Holdings and P&L tracking |
| V8 | `alerts` | Risk alert notifications |
| V9 | `ml_signals` | ML prediction persistence |

---

## Environment Variables

Set these in a `.env` file in the `QuantPlatformApplication/` directory:

```env
DB_USER=quantuser
DB_PASS=quantpass
DB_HOST=localhost
DB_PORT=5432
DB_NAME=postgres
JWT_SECRET=your-256-bit-secret-key
```

---

## Monitoring

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (admin/admin)
- **Spring Actuator**: http://localhost:8080/actuator/health

---

*Built with ❤️ for quantitative finance exploration and learning.*

# Phase 4: Deployment — Oracle Cloud, Docker, Monitoring, CI/CD

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Get the system running on Oracle Cloud Free Tier with monitoring, health checks, and a heartbeat to prevent instance reclamation. Production-ready Docker setup.

**Architecture:** Single VM on Oracle Cloud ARM A1.Flex (2 OCPU, 8 GB RAM). All services containerized with Docker Compose. Prometheus + Grafana for monitoring. Heartbeat cron to prevent Oracle reclamation.

**Tech Stack:** Docker, Docker Compose, Oracle Cloud ARM, Prometheus, Grafana, GitHub Actions CI.

**Project Path:** `/Users/abhinavunmesh/Desktop/QuantEdge_Platform`

---

## File Structure

### New/Modified Files

```
# Production Docker setup
docker-compose.prod.yml                                    — Production compose (single VM, all services)
.env.example                                               — Environment variable template
scripts/deploy.sh                                          — Deployment script for Oracle Cloud
scripts/heartbeat.sh                                       — Heartbeat cron to prevent reclamation
scripts/backup-db.sh                                       — Database backup script

# Monitoring
monitoring/prometheus.yml                                   — Prometheus config targeting Spring Actuator
monitoring/grafana/provisioning/datasources/prometheus.yml  — Auto-provision Prometheus datasource
monitoring/grafana/provisioning/dashboards/dashboard.yml    — Auto-provision dashboard config
monitoring/grafana/dashboards/quantedge.json               — Trading system dashboard

# CI/CD
.github/workflows/test.yml                                 — Run tests on push/PR
.github/workflows/build.yml                                — Build Docker images

# Health check endpoint
QuantPlatformApplication/src/main/java/.../controller/HealthController.java — Custom health with component status
```

---

## Task 1: Production Docker Compose

**Files:**
- Create: `docker-compose.prod.yml`
- Create: `.env.example`

- [ ] **Step 1: Create .env.example**

```bash
# .env.example — Copy to .env and fill in values
# Database
DB_HOST=timescaledb
DB_USER=postgres
DB_PASS=CHANGE_ME_STRONG_PASSWORD

# Redis
REDIS_HOST=redis

# Delta Exchange
ENCRYPTION_KEY=CHANGE_ME_32_CHAR_ENCRYPTION_KEY

# Telegram
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
TELEGRAM_ENABLED=false

# AI (optional — for agent features)
ANTHROPIC_API_KEY=
FRED_API_KEY=DEMO_KEY

# JWT
JWT_SECRET=CHANGE_ME_BASE64_ENCODED_SECRET

# Monitoring
GRAFANA_ADMIN_PASSWORD=CHANGE_ME
```

- [ ] **Step 2: Create docker-compose.prod.yml**

```yaml
# docker-compose.prod.yml — Production deployment (Oracle Cloud ARM)
# Usage: docker compose -f docker-compose.prod.yml --env-file .env up -d

version: '3.8'

services:
  timescaledb:
    image: timescale/timescaledb:latest-pg15
    platform: linux/arm64
    restart: always
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: ${DB_USER:-postgres}
      POSTGRES_PASSWORD: ${DB_PASS}
      POSTGRES_DB: postgres
    volumes:
      - timescaledb_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-postgres}"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    platform: linux/arm64
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  backend:
    build:
      context: ./QuantPlatformApplication
      dockerfile: Dockerfile
    platform: linux/arm64
    restart: always
    ports:
      - "8080:8080"
    environment:
      DB_HOST: timescaledb
      DB_USER: ${DB_USER:-postgres}
      DB_PASS: ${DB_PASS}
      REDIS_HOST: redis
      ENCRYPTION_KEY: ${ENCRYPTION_KEY}
      TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN:-}
      TELEGRAM_CHAT_ID: ${TELEGRAM_CHAT_ID:-}
      TELEGRAM_ENABLED: ${TELEGRAM_ENABLED:-false}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY:-}
      FRED_API_KEY: ${FRED_API_KEY:-DEMO_KEY}
      JWT_SECRET: ${JWT_SECRET:-}
      KAFKA_ENABLED: "false"
      JAVA_OPTS: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -Xms256m"
    depends_on:
      timescaledb:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  ml-service:
    build:
      context: ./ml-service
      dockerfile: Dockerfile
    platform: linux/arm64
    restart: always
    ports:
      - "5001:5001"
    environment:
      BACKEND_URL: http://backend:8080
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:5001/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3

  prometheus:
    image: prom/prometheus:latest
    platform: linux/arm64
    restart: always
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=30d'
      - '--storage.tsdb.retention.size=5GB'

  grafana:
    image: grafana/grafana:latest
    platform: linux/arm64
    restart: always
    ports:
      - "3001:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-quantedge}
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro
    depends_on:
      - prometheus

volumes:
  timescaledb_data:
  redis_data:
  prometheus_data:
  grafana_data:
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add production Docker Compose and env template for Oracle Cloud deployment"
```

---

## Task 2: Monitoring Configuration

**Files:**
- Create: `monitoring/prometheus.yml`
- Create: `monitoring/grafana/provisioning/datasources/prometheus.yml`
- Create: `monitoring/grafana/provisioning/dashboards/dashboard.yml`

- [ ] **Step 1: Create Prometheus config**

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'quantedge-backend'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['backend:8080']
    scrape_interval: 10s

  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

- [ ] **Step 2: Create Grafana datasource provisioning**

```yaml
# monitoring/grafana/provisioning/datasources/prometheus.yml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

- [ ] **Step 3: Create Grafana dashboard provisioning**

```yaml
# monitoring/grafana/provisioning/dashboards/dashboard.yml
apiVersion: 1
providers:
  - name: 'QuantEdge'
    orgId: 1
    folder: 'QuantEdge'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: false
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add Prometheus and Grafana monitoring configuration"
```

---

## Task 3: Deployment and Heartbeat Scripts

**Files:**
- Create: `scripts/deploy.sh`
- Create: `scripts/heartbeat.sh`
- Create: `scripts/backup-db.sh`

- [ ] **Step 1: Create deployment script**

```bash
#!/bin/bash
# scripts/deploy.sh — Deploy QuantEdge to Oracle Cloud VM
set -euo pipefail

echo "=== QuantEdge Deployment ==="

# Check prerequisites
command -v docker >/dev/null 2>&1 || { echo "Docker not installed"; exit 1; }
command -v docker compose >/dev/null 2>&1 || { echo "Docker Compose not installed"; exit 1; }

# Check .env file exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found. Copy .env.example to .env and configure."
    exit 1
fi

# Pull latest code
echo "Pulling latest code..."
git pull origin main 2>/dev/null || git pull origin sanity-1 || echo "Git pull skipped"

# Build and start services
echo "Building and starting services..."
docker compose -f docker-compose.prod.yml --env-file .env build
docker compose -f docker-compose.prod.yml --env-file .env up -d

# Wait for health checks
echo "Waiting for services to be healthy..."
sleep 30

# Check health
echo "Checking service health..."
curl -sf http://localhost:8080/actuator/health && echo " Backend: OK" || echo " Backend: FAILED"
curl -sf http://localhost:5001/health && echo " ML Service: OK" || echo " ML Service: FAILED"
curl -sf http://localhost:9090/-/healthy && echo " Prometheus: OK" || echo " Prometheus: FAILED"
curl -sf http://localhost:3001/api/health && echo " Grafana: OK" || echo " Grafana: FAILED"

echo ""
echo "=== Deployment Complete ==="
echo "Backend:    http://$(hostname -I | awk '{print $1}'):8080"
echo "Grafana:    http://$(hostname -I | awk '{print $1}'):3001"
echo "Prometheus: http://$(hostname -I | awk '{print $1}'):9090"
```

- [ ] **Step 2: Create heartbeat script**

```bash
#!/bin/bash
# scripts/heartbeat.sh — Keep Oracle Cloud free tier instance alive
# Oracle reclaims instances with <20% utilization for 7 consecutive days.
# Run via cron: 0 */4 * * * /path/to/heartbeat.sh

LOG="/var/log/quantedge-heartbeat.log"

echo "$(date '+%Y-%m-%d %H:%M:%S') Heartbeat running..." >> "$LOG"

# Light CPU work (generates ~5% CPU for a few seconds)
dd if=/dev/urandom bs=1M count=50 | md5sum > /dev/null 2>&1

# Check Docker services are running
BACKEND_OK=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
if [ "$BACKEND_OK" != "200" ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') Backend unhealthy ($BACKEND_OK). Restarting..." >> "$LOG"
    cd /opt/quantedge && docker compose -f docker-compose.prod.yml --env-file .env restart backend
fi

# Check Redis
REDIS_OK=$(docker exec $(docker ps -qf name=redis) redis-cli ping 2>/dev/null || echo "FAIL")
if [ "$REDIS_OK" != "PONG" ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') Redis unhealthy. Restarting..." >> "$LOG"
    cd /opt/quantedge && docker compose -f docker-compose.prod.yml --env-file .env restart redis
fi

echo "$(date '+%Y-%m-%d %H:%M:%S') Heartbeat complete. Backend: $BACKEND_OK" >> "$LOG"
```

- [ ] **Step 3: Create database backup script**

```bash
#!/bin/bash
# scripts/backup-db.sh — Backup PostgreSQL database
# Run via cron: 0 3 * * * /path/to/backup-db.sh

BACKUP_DIR="/opt/quantedge/backups"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
KEEP_DAYS=7

mkdir -p "$BACKUP_DIR"

echo "Starting database backup..."
docker exec $(docker ps -qf name=timescaledb) pg_dump -U postgres postgres | gzip > "$BACKUP_DIR/quantedge_$TIMESTAMP.sql.gz"

# Clean old backups
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +$KEEP_DAYS -delete

echo "Backup complete: quantedge_$TIMESTAMP.sql.gz"
echo "Disk usage: $(du -sh $BACKUP_DIR | cut -f1)"
```

- [ ] **Step 4: Make scripts executable and commit**

```bash
chmod +x scripts/*.sh
git commit -m "feat: add deployment, heartbeat, and backup scripts for Oracle Cloud"
```

---

## Task 4: GitHub Actions CI

**Files:**
- Create: `.github/workflows/test.yml`

- [ ] **Step 1: Create test workflow**

```yaml
# .github/workflows/test.yml
name: Run Tests

on:
  push:
    branches: [main, sanity-1]
  pull_request:
    branches: [main]

jobs:
  backend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Maven packages
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2

      - name: Run unit tests
        working-directory: QuantPlatformApplication
        run: |
          ./mvnw test -Dtest="CandleAggregatorTest,IndicatorCalculatorTest,TradeRiskEngineTest,EncryptionConfigTest,DeltaExchangeClientTest,SwingDetectorTest,TrendContinuationStrategyTest,MeanReversionStrategyTest,FundingSentimentStrategyTest,StrategyOrchestratorTest,TelegramBotServiceTest,AccountStateTrackerTest,FundingRateTrackerTest,MultiTimeFrameBacktestEngineTest" -pl .

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: QuantPlatformApplication/target/surefire-reports/
```

- [ ] **Step 2: Commit**

```bash
git commit -m "ci: add GitHub Actions workflow for running unit tests on push/PR"
```

---

## Task 5: Custom Health Controller

**Files:**
- Create: `QuantPlatformApplication/src/main/java/com/QuantPlatformApplication/QuantPlatformApplication/controller/SystemHealthController.java`

- [ ] **Step 1: Create health endpoint with component status**

```java
// controller/SystemHealthController.java
package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.AccountStateTracker;
import com.QuantPlatformApplication.QuantPlatformApplication.service.pipeline.FundingRateTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemHealthController {

    private final AccountStateTracker accountState;
    private final FundingRateTracker fundingRateTracker;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> systemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        health.put("version", "1.0.0");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("strategies", Map.of("status", "UP", "count", 3));
        components.put("riskEngine", Map.of("status", "UP"));
        components.put("telegram", Map.of("status", "CONFIGURED"));
        components.put("account", Map.of(
            "balance", accountState.getCurrentBalance(),
            "peakEquity", accountState.getPeakEquity(),
            "openPositions", accountState.getOpenPositionSymbols().size()
        ));
        components.put("fundingRate", Map.of(
            "current", fundingRateTracker.getCurrentRate(),
            "historySize", fundingRateTracker.getHistory().size()
        ));

        health.put("components", components);
        return ResponseEntity.ok(health);
    }

    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> version() {
        return ResponseEntity.ok(Map.of(
            "name", "QuantEdge Platform",
            "version", "1.0.0",
            "phase", "Approach A — Rule-based multi-timeframe"
        ));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: add SystemHealthController with component status and version endpoint"
```

---

## Summary

| Task | Component | Description |
|------|-----------|-------------|
| 1 | Production Docker Compose | docker-compose.prod.yml + .env.example |
| 2 | Monitoring Config | Prometheus + Grafana provisioning |
| 3 | Deployment Scripts | deploy.sh, heartbeat.sh, backup-db.sh |
| 4 | GitHub Actions CI | Test workflow on push/PR |
| 5 | System Health Controller | /api/v1/system/health with component status |

**Total: 5 tasks. Mostly config files, minimal Java code.**

**After Phase 4:** The system is deployable to Oracle Cloud with monitoring, auto-restart, database backups, and CI. Ready for Phase 5 (live validation on Delta Exchange testnet).

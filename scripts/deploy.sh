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

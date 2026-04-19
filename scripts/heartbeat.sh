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

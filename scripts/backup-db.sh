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

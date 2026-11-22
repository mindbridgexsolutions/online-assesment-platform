#!/bin/bash
# Daily backup of the database and uploaded files. Keeps 14 days. Run by cron (see DEPLOY.md).
set -euo pipefail
cd "$(dirname "$0")"
DIR=/root/backups
mkdir -p "$DIR"
STAMP=$(date +%F_%H%M)
docker compose -f docker-compose.prod.yml exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d assessment' | gzip > "$DIR/db_$STAMP.sql.gz"
docker run --rm -v assess_uploads:/data -v "$DIR":/out alpine tar czf "/out/uploads_$STAMP.tar.gz" -C /data .
find "$DIR" -type f -mtime +14 -delete
echo "Backup done: $DIR (db_$STAMP.sql.gz, uploads_$STAMP.tar.gz)"

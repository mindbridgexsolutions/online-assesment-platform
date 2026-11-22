#!/bin/bash
# Daily backup of the database and uploaded files. Keeps 14 days. Run by cron.
set -euo pipefail
set -a; . /etc/assessment/assessment.env; set +a
DIR=/root/backups
mkdir -p "$DIR"
STAMP=$(date +%F_%H%M)
PGPASSWORD="$DB_PASSWORD" pg_dump -h localhost -U "$DB_USERNAME" -d assessment | gzip > "$DIR/db_$STAMP.sql.gz"
tar czf "$DIR/uploads_$STAMP.tar.gz" -C "$STORAGE_DIR" .
find "$DIR" -type f -mtime +14 -delete
echo "Backup done: $DIR ($STAMP)"

#!/usr/bin/env bash
set -euo pipefail

# Keep the database credentials outside this repository. See mysql-backup.cnf.example.
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-/etc/funfriday/mysql-backup.cnf}"
DATABASE_NAME="${DATABASE_NAME:-GameData}"
RCLONE_REMOTE="${RCLONE_REMOTE:-FunFriday:}"
REMOTE_FILE="${REMOTE_FILE:-${RCLONE_REMOTE}FunFridayBackups/gamedata-latest.sql.gz}"
WORK_DIRECTORY="${WORK_DIRECTORY:-/var/tmp/funfriday-backup}"
BACKUP_FILE="$WORK_DIRECTORY/gamedata-latest.sql.gz"

if [[ ! -r "$MYSQL_DEFAULTS_FILE" ]]; then
  echo "MySQL credentials file is missing or unreadable: $MYSQL_DEFAULTS_FILE" >&2
  exit 1
fi

mkdir -p "$WORK_DIRECTORY"
trap 'rm -f "$BACKUP_FILE"' EXIT

mysqldump --defaults-extra-file="$MYSQL_DEFAULTS_FILE" \
  --single-transaction \
  --no-tablespaces \
  --routines \
  --events \
  --triggers \
  --set-gtid-purged=OFF \
  --databases "$DATABASE_NAME" \
  | gzip -c > "$BACKUP_FILE"

# copyto writes to this fixed Drive path, replacing the previous daily backup.
rclone copyto "$BACKUP_FILE" "$REMOTE_FILE"

echo "Backup completed: $REMOTE_FILE"

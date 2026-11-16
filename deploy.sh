#!/usr/bin/env sh
set -eu

REPOSITORY_DIRECTORY="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

cd "$REPOSITORY_DIRECTORY"
git pull --ff-only
mvn clean install

cd "$REPOSITORY_DIRECTORY/core"
mvn clean package -DskipTests

sudo cp "$REPOSITORY_DIRECTORY/core/target/backend.jar" /var/www/funfriday/backend.jar
sudo install -m 700 "$REPOSITORY_DIRECTORY/script/backup/funfriday-backup.sh" /usr/local/sbin/funfriday-backup
sudo install -m 644 "$REPOSITORY_DIRECTORY/script/backup/funfriday-backup.cron" /etc/cron.d/funfriday-backup
sudo systemctl daemon-reload
sudo systemctl restart arcade-backend

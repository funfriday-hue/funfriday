#!/usr/bin/env bash
set -e
git pull
mvn clean install
cd core
mvn clean package -DskipTests
sudo cp target/backend.jar /var/www/funfriday/backend.jar
sudo install -m 700 script/backup/funfriday-backup.sh /usr/local/sbin/funfriday-backup
sudo install -m 644 script/backup/funfriday-backup.cron /etc/cron.d/funfriday-backup
sudo systemctl daemon-reload
sudo systemctl restart arcade-backend
cd ..

#!/usr/bin/env bash
set -e

mvn clean install
cd core
mvn clean package -DskipTests
sudo cp target/backend.jar /var/www/funfriday/backend.jar
sudo systemctl daemon-reload
sudo systemctl restart arcade-backend
cd ..

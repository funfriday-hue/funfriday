-- Run this once as a MySQL administrator after game_metrics.sql.
-- This grants the existing FunFriday application account only what the metrics writer needs.

GRANT SELECT, INSERT, UPDATE ON GameMetrics.* TO 'funfriday_app'@'localhost';
FLUSH PRIVILEGES;

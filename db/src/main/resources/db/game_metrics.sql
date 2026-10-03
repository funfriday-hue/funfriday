-- Aggregate-only FunFriday game and WebSocket analytics.
-- No room IDs, player IDs, player names, answers, or per-game rows are stored.

CREATE DATABASE IF NOT EXISTS GameMetrics
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE GameMetrics;

CREATE TABLE IF NOT EXISTS game_metrics_hourly (
    bucket_start DATETIME NOT NULL,
    game_type VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT '',
    play_mode VARCHAR(32) NOT NULL DEFAULT '',
    games_started BIGINT UNSIGNED NOT NULL DEFAULT 0,
    games_completed BIGINT UNSIGNED NOT NULL DEFAULT 0,
    games_abandoned BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_players BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_duration_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_start, game_type, category, play_mode)
);

CREATE TABLE IF NOT EXISTS game_metrics_daily (
    bucket_start DATE NOT NULL,
    game_type VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT '',
    play_mode VARCHAR(32) NOT NULL DEFAULT '',
    games_started BIGINT UNSIGNED NOT NULL DEFAULT 0,
    games_completed BIGINT UNSIGNED NOT NULL DEFAULT 0,
    games_abandoned BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_players BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_duration_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_start, game_type, category, play_mode)
);

CREATE TABLE IF NOT EXISTS websocket_metrics_hourly (
    bucket_start DATETIME NOT NULL PRIMARY KEY,
    connections_opened BIGINT UNSIGNED NOT NULL DEFAULT 0,
    connections_closed BIGINT UNSIGNED NOT NULL DEFAULT 0,
    peak_concurrent_connections BIGINT UNSIGNED NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS websocket_metrics_daily (
    bucket_start DATE NOT NULL PRIMARY KEY,
    connections_opened BIGINT UNSIGNED NOT NULL DEFAULT 0,
    connections_closed BIGINT UNSIGNED NOT NULL DEFAULT 0,
    peak_concurrent_connections BIGINT UNSIGNED NOT NULL DEFAULT 0
);

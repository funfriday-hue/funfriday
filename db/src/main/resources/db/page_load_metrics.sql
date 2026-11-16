-- Run once in the GameMetrics database after deploying page-load telemetry.
-- Stores only aggregate hourly and daily performance data; no visitor-level rows.

USE GameMetrics;

CREATE TABLE IF NOT EXISTS page_load_metrics_hourly (
    bucket_start DATETIME NOT NULL,
    page_name VARCHAR(64) NOT NULL,
    load_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_load_milliseconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    max_load_milliseconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    loads_over_three_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    loads_over_four_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_start, page_name)
);

CREATE TABLE IF NOT EXISTS page_load_metrics_daily (
    bucket_start DATE NOT NULL,
    page_name VARCHAR(64) NOT NULL,
    load_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_load_milliseconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    max_load_milliseconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    loads_over_three_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    loads_over_four_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_start, page_name)
);

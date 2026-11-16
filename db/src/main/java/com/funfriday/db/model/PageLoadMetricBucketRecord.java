package com.funfriday.db.model;

import java.time.LocalDateTime;

/** Aggregate-only page-load telemetry. No visitor, session, or URL data is retained. */
public record PageLoadMetricBucketRecord(
        LocalDateTime bucketStart,
        String pageName,
        long loadCount,
        long totalLoadMilliseconds,
        long maxLoadMilliseconds,
        long loadsOverThreeSeconds,
        long loadsOverFourSeconds
) {
}

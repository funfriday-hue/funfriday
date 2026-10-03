package com.funfriday.db.model;

import java.time.LocalDateTime;

/** Aggregate-only WebSocket connection analytics. */
public record WebSocketMetricBucketRecord(
        LocalDateTime bucketStart,
        long connectionsOpened,
        long connectionsClosed,
        long peakConcurrentConnections
) {
}

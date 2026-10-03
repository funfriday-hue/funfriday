package com.funfriday.db.model;

import java.time.LocalDateTime;

/** Aggregate-only game analytics. No room, player, or answer data is retained. */
public record GameMetricBucketRecord(
        LocalDateTime bucketStart,
        String gameType,
        String category,
        String playMode,
        long gamesStarted,
        long gamesCompleted,
        long gamesAbandoned,
        long totalPlayers,
        long totalDurationSeconds
) {
}

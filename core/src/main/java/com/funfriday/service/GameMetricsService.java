package com.funfriday.service;

import com.funfriday.db.dao.GameMetricsDao;
import com.funfriday.db.model.GameMetricBucketRecord;
import com.funfriday.db.model.WebSocketMetricBucketRecord;
import com.funfriday.games.quizroyale.QuizRoyaleConfiguration;
import com.funfriday.games.quizroyale.QuizRoyaleData;
import com.funfriday.model.GameRoom;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buffers aggregate-only analytics in memory and writes them in batches.
 * Individual rooms, users, player names, and answers are never persisted here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameMetricsService {
    private final GameMetricsDao gameMetricsDao;
    private final Object lock = new Object();
    private final Map<GameBucketKey, GameCounters> pendingHourlyGames = new HashMap<>();
    private final Map<GameBucketKey, GameCounters> pendingDailyGames = new HashMap<>();
    private final Map<LocalDateTime, WebSocketCounters> pendingHourlyWebSockets = new HashMap<>();
    private final Map<LocalDateTime, WebSocketCounters> pendingDailyWebSockets = new HashMap<>();
    private final Set<String> connectedWebSocketSessions = new HashSet<>();
    private long currentWebSocketConnections;

    public void recordGameStarted(GameRoom room) {
        GameDescriptor descriptor = describe(room);
        synchronized (lock) {
            gameCounters(pendingHourlyGames, hourlyKey(descriptor)).gamesStarted++;
            gameCounters(pendingDailyGames, dailyKey(descriptor)).gamesStarted++;
        }
    }

    public void recordGameCompleted(GameRoom room) {
        recordGameFinished(room, false);
    }

    public void recordGameAbandoned(GameRoom room) {
        recordGameFinished(room, true);
    }

    private void recordGameFinished(GameRoom room, boolean abandoned) {
        GameDescriptor descriptor = describe(room);
        long durationSeconds = Math.max(0, (System.currentTimeMillis() - room.getStartTime()) / 1000);
        long playerCount = room.getPlayerMap().size();
        synchronized (lock) {
            updateFinished(gameCounters(pendingHourlyGames, hourlyKey(descriptor)), abandoned, playerCount, durationSeconds);
            updateFinished(gameCounters(pendingDailyGames, dailyKey(descriptor)), abandoned, playerCount, durationSeconds);
        }
    }

    public void recordWebSocketConnected(String sessionId) {
        if (sessionId == null) return;
        synchronized (lock) {
            if (!connectedWebSocketSessions.add(sessionId)) return;
            currentWebSocketConnections++;
            incrementWebSocketOpened(hourlyWebSocketCounters());
            incrementWebSocketOpened(dailyWebSocketCounters());
        }
    }

    public void recordWebSocketDisconnected(String sessionId) {
        if (sessionId == null) return;
        synchronized (lock) {
            if (!connectedWebSocketSessions.remove(sessionId)) return;
            currentWebSocketConnections = Math.max(0, currentWebSocketConnections - 1);
            hourlyWebSocketCounters().connectionsClosed++;
            dailyWebSocketCounters().connectionsClosed++;
        }
    }

    /** Flushes asynchronously every 30 minutes; change the environment property only if needed. */
    @Scheduled(fixedDelayString = "${FUNFRIDAY_METRICS_FLUSH_MILLIS:1800000}")
    public void flushScheduledMetrics() {
        flushMetrics();
    }

    @PreDestroy
    public void flushOnShutdown() {
        flushMetrics();
    }

    private void flushMetrics() {
        PendingMetrics pending = drain();
        if (pending.isEmpty()) return;
        try {
            gameMetricsDao.persist(pending.hourlyGames(), pending.dailyGames(), pending.hourlyWebSockets(), pending.dailyWebSockets());
            log.info("Flushed game metrics: {} hourly game buckets, {} daily game buckets, {} hourly WebSocket buckets, {} daily WebSocket buckets",
                    pending.hourlyGames().size(), pending.dailyGames().size(), pending.hourlyWebSockets().size(), pending.dailyWebSockets().size());
        } catch (Exception exception) {
            mergeBack(pending);
            log.error("Unable to persist buffered game metrics; they will be retried during the next flush", exception);
        }
    }

    private PendingMetrics drain() {
        synchronized (lock) {
            PendingMetrics pending = new PendingMetrics(
                    toGameRecords(pendingHourlyGames),
                    toGameRecords(pendingDailyGames),
                    toWebSocketRecords(pendingHourlyWebSockets),
                    toWebSocketRecords(pendingDailyWebSockets)
            );
            pendingHourlyGames.clear();
            pendingDailyGames.clear();
            pendingHourlyWebSockets.clear();
            pendingDailyWebSockets.clear();
            return pending;
        }
    }

    private void mergeBack(PendingMetrics pending) {
        synchronized (lock) {
            mergeGameRecords(pendingHourlyGames, pending.hourlyGames());
            mergeGameRecords(pendingDailyGames, pending.dailyGames());
            mergeWebSocketRecords(pendingHourlyWebSockets, pending.hourlyWebSockets());
            mergeWebSocketRecords(pendingDailyWebSockets, pending.dailyWebSockets());
        }
    }

    private GameDescriptor describe(GameRoom room) {
        String category = "";
        String playMode = room.getInitialGameMode() == null ? "" : room.getInitialGameMode();
        if (room.getGameData() instanceof QuizRoyaleData quizData) {
            QuizRoyaleConfiguration configuration = quizData.getGameConfiguration();
            if (configuration == null) return new GameDescriptor(room.getType(), category, playMode);
            category = configuration.getCategory() == null ? "" : configuration.getCategory().name();
            playMode = configuration.getPlayMode() == null ? "" : configuration.getPlayMode().name();
        }
        return new GameDescriptor(room.getType(), category, playMode);
    }

    private GameBucketKey hourlyKey(GameDescriptor descriptor) {
        return new GameBucketKey(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS), descriptor.gameType(), descriptor.category(), descriptor.playMode());
    }

    private GameBucketKey dailyKey(GameDescriptor descriptor) {
        return new GameBucketKey(LocalDateTime.now().toLocalDate().atStartOfDay(), descriptor.gameType(), descriptor.category(), descriptor.playMode());
    }

    private WebSocketCounters hourlyWebSocketCounters() {
        return pendingHourlyWebSockets.computeIfAbsent(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS), ignored -> new WebSocketCounters());
    }

    private WebSocketCounters dailyWebSocketCounters() {
        return pendingDailyWebSockets.computeIfAbsent(LocalDateTime.now().toLocalDate().atStartOfDay(), ignored -> new WebSocketCounters());
    }

    private void incrementWebSocketOpened(WebSocketCounters counters) {
        counters.connectionsOpened++;
        counters.peakConcurrentConnections = Math.max(counters.peakConcurrentConnections, currentWebSocketConnections);
    }

    private static void updateFinished(GameCounters counters, boolean abandoned, long playerCount, long durationSeconds) {
        if (abandoned) counters.gamesAbandoned++;
        else counters.gamesCompleted++;
        counters.totalPlayers += playerCount;
        counters.totalDurationSeconds += durationSeconds;
    }

    private static GameCounters gameCounters(Map<GameBucketKey, GameCounters> counters, GameBucketKey key) {
        return counters.computeIfAbsent(key, ignored -> new GameCounters());
    }

    private static List<GameMetricBucketRecord> toGameRecords(Map<GameBucketKey, GameCounters> counters) {
        List<GameMetricBucketRecord> records = new ArrayList<>();
        counters.forEach((key, value) -> records.add(new GameMetricBucketRecord(key.bucketStart(), key.gameType(), key.category(), key.playMode(),
                value.gamesStarted, value.gamesCompleted, value.gamesAbandoned, value.totalPlayers, value.totalDurationSeconds)));
        return records;
    }

    private static List<WebSocketMetricBucketRecord> toWebSocketRecords(Map<LocalDateTime, WebSocketCounters> counters) {
        List<WebSocketMetricBucketRecord> records = new ArrayList<>();
        counters.forEach((bucket, value) -> records.add(new WebSocketMetricBucketRecord(bucket, value.connectionsOpened, value.connectionsClosed, value.peakConcurrentConnections)));
        return records;
    }

    private static void mergeGameRecords(Map<GameBucketKey, GameCounters> target, List<GameMetricBucketRecord> records) {
        for (GameMetricBucketRecord record : records) {
            GameCounters counters = gameCounters(target, new GameBucketKey(record.bucketStart(), record.gameType(), record.category(), record.playMode()));
            counters.gamesStarted += record.gamesStarted();
            counters.gamesCompleted += record.gamesCompleted();
            counters.gamesAbandoned += record.gamesAbandoned();
            counters.totalPlayers += record.totalPlayers();
            counters.totalDurationSeconds += record.totalDurationSeconds();
        }
    }

    private static void mergeWebSocketRecords(Map<LocalDateTime, WebSocketCounters> target, List<WebSocketMetricBucketRecord> records) {
        for (WebSocketMetricBucketRecord record : records) {
            WebSocketCounters counters = target.computeIfAbsent(record.bucketStart(), ignored -> new WebSocketCounters());
            counters.connectionsOpened += record.connectionsOpened();
            counters.connectionsClosed += record.connectionsClosed();
            counters.peakConcurrentConnections = Math.max(counters.peakConcurrentConnections, record.peakConcurrentConnections());
        }
    }

    private record GameDescriptor(String gameType, String category, String playMode) { }
    private record GameBucketKey(LocalDateTime bucketStart, String gameType, String category, String playMode) { }
    private record PendingMetrics(List<GameMetricBucketRecord> hourlyGames, List<GameMetricBucketRecord> dailyGames,
                                  List<WebSocketMetricBucketRecord> hourlyWebSockets, List<WebSocketMetricBucketRecord> dailyWebSockets) {
        boolean isEmpty() {
            return hourlyGames.isEmpty() && dailyGames.isEmpty() && hourlyWebSockets.isEmpty() && dailyWebSockets.isEmpty();
        }
    }
    private static final class GameCounters {
        private long gamesStarted;
        private long gamesCompleted;
        private long gamesAbandoned;
        private long totalPlayers;
        private long totalDurationSeconds;
    }
    private static final class WebSocketCounters {
        private long connectionsOpened;
        private long connectionsClosed;
        private long peakConcurrentConnections;
    }
}

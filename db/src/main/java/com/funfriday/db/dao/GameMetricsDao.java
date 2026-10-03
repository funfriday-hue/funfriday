package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.GameMetricBucketRecord;
import com.funfriday.db.model.WebSocketMetricBucketRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/** Persists only pre-aggregated analytics buckets. */
public class GameMetricsDao {
    private static final String GAME_HOURLY_UPSERT = gameUpsert("game_metrics_hourly");
    private static final String GAME_DAILY_UPSERT = gameUpsert("game_metrics_daily");
    private static final String WEBSOCKET_HOURLY_UPSERT = webSocketUpsert("websocket_metrics_hourly");
    private static final String WEBSOCKET_DAILY_UPSERT = webSocketUpsert("websocket_metrics_daily");

    private final DatabaseConnectionProvider connectionProvider;

    public GameMetricsDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void persist(
            List<GameMetricBucketRecord> hourlyGames,
            List<GameMetricBucketRecord> dailyGames,
            List<WebSocketMetricBucketRecord> hourlyWebSockets,
            List<WebSocketMetricBucketRecord> dailyWebSockets
    ) throws SQLException {
        if (hourlyGames.isEmpty() && dailyGames.isEmpty() && hourlyWebSockets.isEmpty() && dailyWebSockets.isEmpty()) return;

        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertGames(connection, GAME_HOURLY_UPSERT, hourlyGames);
                upsertGames(connection, GAME_DAILY_UPSERT, dailyGames);
                upsertWebSockets(connection, WEBSOCKET_HOURLY_UPSERT, hourlyWebSockets);
                upsertWebSockets(connection, WEBSOCKET_DAILY_UPSERT, dailyWebSockets);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void upsertGames(Connection connection, String sql, List<GameMetricBucketRecord> records) throws SQLException {
        if (records.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (GameMetricBucketRecord record : records) {
                statement.setTimestamp(1, Timestamp.valueOf(record.bucketStart()));
                statement.setString(2, record.gameType());
                statement.setString(3, record.category());
                statement.setString(4, record.playMode());
                statement.setLong(5, record.gamesStarted());
                statement.setLong(6, record.gamesCompleted());
                statement.setLong(7, record.gamesAbandoned());
                statement.setLong(8, record.totalPlayers());
                statement.setLong(9, record.totalDurationSeconds());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void upsertWebSockets(Connection connection, String sql, List<WebSocketMetricBucketRecord> records) throws SQLException {
        if (records.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (WebSocketMetricBucketRecord record : records) {
                statement.setTimestamp(1, Timestamp.valueOf(record.bucketStart()));
                statement.setLong(2, record.connectionsOpened());
                statement.setLong(3, record.connectionsClosed());
                statement.setLong(4, record.peakConcurrentConnections());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static String gameUpsert(String table) {
        return "INSERT INTO " + table + " (bucket_start, game_type, category, play_mode, games_started, games_completed, games_abandoned, total_players, total_duration_seconds) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "games_started = games_started + VALUES(games_started), "
                + "games_completed = games_completed + VALUES(games_completed), "
                + "games_abandoned = games_abandoned + VALUES(games_abandoned), "
                + "total_players = total_players + VALUES(total_players), "
                + "total_duration_seconds = total_duration_seconds + VALUES(total_duration_seconds)";
    }

    private static String webSocketUpsert(String table) {
        return "INSERT INTO " + table + " (bucket_start, connections_opened, connections_closed, peak_concurrent_connections) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "connections_opened = connections_opened + VALUES(connections_opened), "
                + "connections_closed = connections_closed + VALUES(connections_closed), "
                + "peak_concurrent_connections = GREATEST(peak_concurrent_connections, VALUES(peak_concurrent_connections))";
    }
}

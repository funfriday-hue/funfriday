package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.SudokuPuzzleProgress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stores only each player's best aggregate result for a Sudoku board, never individual moves. */
public class PlayerSudokuProgressDao {
    private final DatabaseConnectionProvider connectionProvider;

    public PlayerSudokuProgressDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Map<Integer, SudokuPuzzleProgress> findByPlayerAndPuzzleIds(String playerId, List<Integer> puzzleIds) throws Exception {
        if (playerId == null || playerId.isBlank() || puzzleIds == null || puzzleIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(puzzleIds.size(), "?"));
        String sql = "SELECT puzzle_id, best_progress_percent, is_completed FROM player_sudoku_progress "
                + "WHERE player_id = ? AND puzzle_id IN (" + placeholders + ")";
        Map<Integer, SudokuPuzzleProgress> progress = new HashMap<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId);
            for (int index = 0; index < puzzleIds.size(); index++) statement.setInt(index + 2, puzzleIds.get(index));
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    progress.put(results.getInt("puzzle_id"), new SudokuPuzzleProgress(
                            results.getInt("best_progress_percent"), results.getBoolean("is_completed")));
                }
            }
        }
        return Map.copyOf(progress);
    }

    public void recordProgress(String playerId, int puzzleId, int progressPercent, boolean completed) throws Exception {
        if (playerId == null || playerId.isBlank() || puzzleId <= 0) return;
        int safeProgress = Math.max(0, Math.min(100, progressPercent));
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_sudoku_progress
                         (player_id, puzzle_id, best_progress_percent, is_completed, completed_at)
                     VALUES (?, ?, ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                     ON DUPLICATE KEY UPDATE
                         best_progress_percent = GREATEST(best_progress_percent, VALUES(best_progress_percent)),
                         is_completed = is_completed OR VALUES(is_completed),
                         last_played_at = CURRENT_TIMESTAMP,
                         completed_at = CASE
                             WHEN completed_at IS NULL AND VALUES(is_completed) THEN CURRENT_TIMESTAMP
                             ELSE completed_at
                         END
                     """)) {
            statement.setString(1, playerId);
            statement.setInt(2, puzzleId);
            statement.setInt(3, safeProgress);
            statement.setBoolean(4, completed);
            statement.setBoolean(5, completed);
            statement.executeUpdate();
        }
    }
}

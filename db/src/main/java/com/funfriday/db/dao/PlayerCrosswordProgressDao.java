package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.CrosswordPuzzleProgress;
import java.sql.*;
import java.util.*;

/** Persists a player's best crossword percentage, not their individual entered letters. */
public class PlayerCrosswordProgressDao {
  private final DatabaseConnectionProvider connectionProvider;

  public PlayerCrosswordProgressDao(DatabaseConnectionProvider connectionProvider) {
    this.connectionProvider = connectionProvider;
  }

  public Map<Long, CrosswordPuzzleProgress> findByPlayerAndPuzzleIds(
      String playerId, List<Long> puzzleIds) throws SQLException {
    if (playerId == null || playerId.isBlank() || puzzleIds == null || puzzleIds.isEmpty())
      return Map.of();
    String sql =
        "SELECT puzzle_id, best_progress_percent, is_completed FROM player_crossword_progress WHERE"
            + " player_id=? AND puzzle_id IN ("
            + String.join(",", Collections.nCopies(puzzleIds.size(), "?"))
            + ")";
    Map<Long, CrosswordPuzzleProgress> result = new HashMap<>();
    try (Connection c = connectionProvider.getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, playerId);
      for (int i = 0; i < puzzleIds.size(); i++) s.setLong(i + 2, puzzleIds.get(i));
      try (ResultSet r = s.executeQuery()) {
        while (r.next())
          result.put(r.getLong(1), new CrosswordPuzzleProgress(r.getInt(2), r.getBoolean(3)));
      }
    }
    return Map.copyOf(result);
  }

  public void recordProgress(String playerId, long puzzleId, int progress, boolean completed)
      throws SQLException {
    if (playerId == null || playerId.isBlank() || puzzleId <= 0) return;
    try (Connection c = connectionProvider.getConnection();
        PreparedStatement s =
            c.prepareStatement(
                """
                INSERT INTO player_crossword_progress (player_id,puzzle_id,best_progress_percent,is_completed,completed_at)
                VALUES (?,?,?,?,CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                ON DUPLICATE KEY UPDATE best_progress_percent=GREATEST(best_progress_percent,VALUES(best_progress_percent)),
                is_completed=is_completed OR VALUES(is_completed), last_played_at=CURRENT_TIMESTAMP,
                completed_at=CASE WHEN completed_at IS NULL AND VALUES(is_completed) THEN CURRENT_TIMESTAMP ELSE completed_at END
                """)) {
      s.setString(1, playerId);
      s.setLong(2, puzzleId);
      s.setInt(3, Math.max(0, Math.min(100, progress)));
      s.setBoolean(4, completed);
      s.setBoolean(5, completed);
      s.executeUpdate();
    }
  }
}

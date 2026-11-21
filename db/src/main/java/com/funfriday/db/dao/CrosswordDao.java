package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.CrosswordEntry;
import com.funfriday.db.model.CrosswordPuzzle;
import com.funfriday.db.model.CrosswordPuzzleSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CrosswordDao {
  private final DatabaseConnectionProvider connectionProvider;

  public CrosswordDao(DatabaseConnectionProvider connectionProvider) {
    this.connectionProvider = connectionProvider;
  }

  public List<CrosswordPuzzleSummary> listActive() throws SQLException {
    String sql =
        """
        SELECT puzzle.id, puzzle.puzzle_key, puzzle.category, puzzle.title, COUNT(entry.id) AS clue_count
        FROM crossword_puzzles puzzle
        LEFT JOIN crossword_entries entry ON entry.puzzle_id = puzzle.id
        WHERE puzzle.is_active = TRUE
        GROUP BY puzzle.id, puzzle.puzzle_key, puzzle.category, puzzle.title
        ORDER BY puzzle.category ASC, puzzle.id ASC
        """;
    try (Connection connection = connectionProvider.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      List<CrosswordPuzzleSummary> results = new ArrayList<>();
      while (resultSet.next()) {
        results.add(
            new CrosswordPuzzleSummary(
                resultSet.getLong("id"),
                resultSet.getString("puzzle_key"),
                resultSet.getString("category"),
                resultSet.getString("title"),
                resultSet.getInt("clue_count")));
      }
      return List.copyOf(results);
    }
  }

  public Optional<CrosswordPuzzle> selectActiveById(long puzzleId) throws SQLException {
    String sql =
        "SELECT id,puzzle_key,category,title,width,height,grid_json FROM crossword_puzzles WHERE"
            + " id=? AND is_active=TRUE";
    try (Connection c = connectionProvider.getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setLong(1, puzzleId);
      try (ResultSet r = s.executeQuery()) {
        if (!r.next()) return Optional.empty();
        return Optional.of(readPuzzle(c, r));
      }
    }
  }

  public Optional<CrosswordPuzzle> selectRandomActive() throws SQLException {
    String sql =
        "SELECT id,puzzle_key,category,title,width,height,grid_json FROM crossword_puzzles WHERE"
            + " is_active=TRUE ORDER BY RAND() LIMIT 1";
    try (Connection c = connectionProvider.getConnection();
        PreparedStatement s = c.prepareStatement(sql);
        ResultSet r = s.executeQuery()) {
      return r.next() ? Optional.of(readPuzzle(c, r)) : Optional.empty();
    }
  }

  private CrosswordPuzzle readPuzzle(Connection c, ResultSet r) throws SQLException {
    long id = r.getLong("id");
    List<CrosswordEntry> entries = new ArrayList<>();
    try (PreparedStatement s =
        c.prepareStatement(
            "SELECT clue_number,direction,row_index,column_index,answer,clue FROM crossword_entries"
                + " WHERE puzzle_id=? ORDER BY clue_number,direction")) {
      s.setLong(1, id);
      try (ResultSet e = s.executeQuery()) {
        while (e.next())
          entries.add(
              new CrosswordEntry(
                  e.getInt(1),
                  e.getString(2),
                  e.getInt(3),
                  e.getInt(4),
                  e.getString(5),
                  e.getString(6)));
      }
    }
    return new CrosswordPuzzle(
        id,
        r.getString("puzzle_key"),
        r.getString("category"),
        r.getString("title"),
        r.getInt("width"),
        r.getInt("height"),
        r.getString("grid_json"),
        List.copyOf(entries));
  }
}

package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.SudokuPuzzle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.List;

public class SudokuDao {
    private static final String SELECT_RANDOM_BY_SIZE = """
            SELECT id, size, grid, solution, created_at
            FROM sudoku_puzzles
            WHERE size = ?
            ORDER BY RAND()
            LIMIT 1
            """;

    private final DatabaseConnectionProvider connectionProvider;

    public SudokuDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Optional<SudokuPuzzle> selectRandomBySize(int size) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_RANDOM_BY_SIZE)) {
            statement.setInt(1, size);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapSudokuPuzzle(resultSet));
            }
        }
    }

    public Optional<SudokuPuzzle> selectByIdAndSize(int puzzleId, int size) throws SQLException {
        String sql = "SELECT id, size, grid, solution, created_at FROM sudoku_puzzles WHERE id = ? AND size = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, puzzleId);
            statement.setInt(2, size);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSudokuPuzzle(resultSet)) : Optional.empty();
            }
        }
    }

    public Page listPageBySize(int size, int page, int pageSize) throws SQLException {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(24, pageSize));
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM sudoku_puzzles WHERE size = ?");
             PreparedStatement rows = connection.prepareStatement("""
                     SELECT id, size, grid, solution, created_at
                     FROM sudoku_puzzles
                     WHERE size = ?
                     ORDER BY id ASC
                     LIMIT ? OFFSET ?
                     """)) {
            count.setInt(1, size);
            int total;
            try (ResultSet resultSet = count.executeQuery()) {
                resultSet.next();
                total = resultSet.getInt(1);
            }
            rows.setInt(1, size);
            rows.setInt(2, safePageSize);
            rows.setInt(3, safePage * safePageSize);
            java.util.ArrayList<SudokuPuzzle> puzzles = new java.util.ArrayList<>();
            try (ResultSet resultSet = rows.executeQuery()) {
                while (resultSet.next()) puzzles.add(mapSudokuPuzzle(resultSet));
            }
            return new Page(List.copyOf(puzzles), total, safePage, safePageSize);
        }
    }

    /**
     * Returns a page filtered by the signed-in player's saved puzzle state.
     * The filter value is validated by the controller before it reaches here.
     */
    public Page listPageBySizeAndProgress(int size, int page, int pageSize, String playerId, String progressFilter) throws SQLException {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(24, pageSize));
        String progressPredicate = switch (progressFilter) {
            case "COMPLETED" -> " AND progress.is_completed = TRUE";
            case "PARTIAL" -> " AND progress.is_completed = FALSE AND progress.best_progress_percent > 0";
            case "NOT_SOLVED" -> " AND (progress.puzzle_id IS NULL OR (progress.is_completed = FALSE AND progress.best_progress_percent = 0))";
            default -> "";
        };
        String fromAndWhere = """
                FROM sudoku_puzzles puzzle
                LEFT JOIN player_sudoku_progress progress
                  ON progress.puzzle_id = puzzle.id AND progress.player_id = ?
                WHERE puzzle.size = ?
                """ + progressPredicate;
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) " + fromAndWhere);
             PreparedStatement rows = connection.prepareStatement("""
                     SELECT puzzle.id, puzzle.size, puzzle.grid, puzzle.solution, puzzle.created_at
                     """ + fromAndWhere + " ORDER BY puzzle.id ASC LIMIT ? OFFSET ?")) {
            count.setString(1, playerId);
            count.setInt(2, size);
            int total;
            try (ResultSet resultSet = count.executeQuery()) {
                resultSet.next();
                total = resultSet.getInt(1);
            }
            rows.setString(1, playerId);
            rows.setInt(2, size);
            rows.setInt(3, safePageSize);
            rows.setInt(4, safePage * safePageSize);
            java.util.ArrayList<SudokuPuzzle> puzzles = new java.util.ArrayList<>();
            try (ResultSet resultSet = rows.executeQuery()) {
                while (resultSet.next()) puzzles.add(mapSudokuPuzzle(resultSet));
            }
            return new Page(List.copyOf(puzzles), total, safePage, safePageSize);
        }
    }

    public record Page(List<SudokuPuzzle> puzzles, int total, int page, int pageSize) { }

    private SudokuPuzzle mapSudokuPuzzle(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new SudokuPuzzle(
                resultSet.getInt("id"),
                resultSet.getInt("size"),
                resultSet.getString("grid"),
                resultSet.getString("solution"),
                createdAt == null ? null : createdAt.toLocalDateTime()
        );
    }
}

package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.SudokuPuzzle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

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

package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.PlayerQuestionProgress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Stores the best result, not every individual attempt, for the player catalogue. */
public class PlayerQuestionProgressDao {
    private final DatabaseConnectionProvider connectionProvider;

    public PlayerQuestionProgressDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Map<String, PlayerQuestionProgress> findByPlayerAndCategory(String playerId, String category) throws SQLException {
        Map<String, PlayerQuestionProgress> progress = new HashMap<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT progress.question_key, progress.best_correct_answers, progress.answer_count, progress.is_completed
                     FROM player_question_progress progress
                     JOIN quiz_questions question ON question.question_key = progress.question_key
                     WHERE progress.player_id = ? AND question.category = ? AND question.is_active = TRUE
                     """)) {
            statement.setString(1, playerId);
            statement.setString(2, category);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String questionKey = resultSet.getString("question_key");
                    progress.put(questionKey, new PlayerQuestionProgress(
                            questionKey,
                            resultSet.getInt("best_correct_answers"),
                            resultSet.getInt("answer_count"),
                            resultSet.getBoolean("is_completed")
                    ));
                }
            }
        }
        return progress;
    }

    public void recordAttempt(String playerId, String questionKey, int correctAnswers, int answerCount) throws SQLException {
        if (playerId == null || playerId.isBlank() || questionKey == null || questionKey.isBlank() || answerCount <= 0) return;
        int boundedCorrectAnswers = Math.max(0, Math.min(correctAnswers, answerCount));
        boolean completed = boundedCorrectAnswers == answerCount;
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_question_progress
                         (player_id, question_key, best_correct_answers, answer_count, is_completed, completed_at)
                     VALUES (?, ?, ?, ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                     ON DUPLICATE KEY UPDATE
                         best_correct_answers = GREATEST(best_correct_answers, VALUES(best_correct_answers)),
                         answer_count = VALUES(answer_count),
                         is_completed = is_completed OR VALUES(is_completed),
                         completed_at = CASE
                             WHEN is_completed OR VALUES(is_completed) THEN COALESCE(completed_at, CURRENT_TIMESTAMP)
                             ELSE NULL
                         END,
                         last_played_at = CURRENT_TIMESTAMP
                     """)) {
            statement.setString(1, playerId);
            statement.setString(2, questionKey);
            statement.setInt(3, boundedCorrectAnswers);
            statement.setInt(4, answerCount);
            statement.setBoolean(5, completed);
            statement.setBoolean(6, completed);
            statement.executeUpdate();
        }
    }

    /** Retains a guest's history when that browser signs in for the first time. */
    public void mergePlayerProgress(String sourcePlayerId, String targetPlayerId) throws SQLException {
        if (sourcePlayerId == null || sourcePlayerId.isBlank() || sourcePlayerId.equals(targetPlayerId)) return;
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement merge = connection.prepareStatement("""
                    INSERT INTO player_question_progress
                        (player_id, question_key, best_correct_answers, answer_count, is_completed, first_played_at, last_played_at, completed_at)
                    SELECT ?, question_key, best_correct_answers, answer_count, is_completed, first_played_at, last_played_at, completed_at
                    FROM player_question_progress
                    WHERE player_id = ?
                    ON DUPLICATE KEY UPDATE
                        best_correct_answers = GREATEST(player_question_progress.best_correct_answers, VALUES(best_correct_answers)),
                        answer_count = GREATEST(player_question_progress.answer_count, VALUES(answer_count)),
                        is_completed = player_question_progress.is_completed OR VALUES(is_completed),
                        first_played_at = LEAST(player_question_progress.first_played_at, VALUES(first_played_at)),
                        last_played_at = GREATEST(player_question_progress.last_played_at, VALUES(last_played_at)),
                        completed_at = CASE
                            WHEN player_question_progress.is_completed OR VALUES(is_completed)
                                THEN COALESCE(player_question_progress.completed_at, VALUES(completed_at), CURRENT_TIMESTAMP)
                            ELSE NULL
                        END
                    """)) {
                merge.setString(1, targetPlayerId);
                merge.setString(2, sourcePlayerId);
                merge.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM player_question_progress WHERE player_id = ?")) {
                delete.setString(1, sourcePlayerId);
                delete.executeUpdate();
            }
            connection.commit();
        }
    }
}

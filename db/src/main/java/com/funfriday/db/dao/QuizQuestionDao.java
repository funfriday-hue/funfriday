package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.QuizAnswerRecord;
import com.funfriday.db.model.QuizQuestionRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuizQuestionDao {
    private static final String SELECT_RANDOM_ACTIVE_QUESTION = """
            SELECT id, question_key, category, question_type, prompt
            FROM quiz_questions
            WHERE category = ? AND is_active = TRUE
            ORDER BY RAND()
            LIMIT 1
            """;

    private static final String SELECT_ANSWERS = """
            SELECT id, canonical_answer, display_order, hint
            FROM quiz_answers
            WHERE question_id = ?
            ORDER BY display_order ASC
            """;

    private static final String SELECT_ALIASES = """
            SELECT alias
            FROM quiz_answer_aliases
            WHERE answer_id = ?
            ORDER BY id ASC
            """;

    private final DatabaseConnectionProvider connectionProvider;

    public QuizQuestionDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Optional<QuizQuestionRecord> selectRandomActiveByCategory(String category) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement questionStatement = connection.prepareStatement(SELECT_RANDOM_ACTIVE_QUESTION)) {
            questionStatement.setString(1, category);
            try (ResultSet resultSet = questionStatement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();

                long questionId = resultSet.getLong("id");
                List<QuizAnswerRecord> answers = selectAnswers(connection, questionId);
                return Optional.of(new QuizQuestionRecord(
                        questionId,
                        resultSet.getString("question_key"),
                        resultSet.getString("category"),
                        resultSet.getString("question_type"),
                        resultSet.getString("prompt"),
                        answers
                ));
            }
        }
    }

    private List<QuizAnswerRecord> selectAnswers(Connection connection, long questionId) throws SQLException {
        List<QuizAnswerRecord> answers = new ArrayList<>();
        try (PreparedStatement answerStatement = connection.prepareStatement(SELECT_ANSWERS)) {
            answerStatement.setLong(1, questionId);
            try (ResultSet resultSet = answerStatement.executeQuery()) {
                while (resultSet.next()) {
                    long answerId = resultSet.getLong("id");
                    answers.add(new QuizAnswerRecord(
                            answerId,
                            resultSet.getString("canonical_answer"),
                            resultSet.getInt("display_order"),
                            resultSet.getString("hint"),
                            selectAliases(connection, answerId)
                    ));
                }
            }
        }
        return answers;
    }

    private List<String> selectAliases(Connection connection, long answerId) throws SQLException {
        List<String> aliases = new ArrayList<>();
        try (PreparedStatement aliasStatement = connection.prepareStatement(SELECT_ALIASES)) {
            aliasStatement.setLong(1, answerId);
            try (ResultSet resultSet = aliasStatement.executeQuery()) {
                while (resultSet.next()) aliases.add(resultSet.getString("alias"));
            }
        }
        return aliases;
    }
}

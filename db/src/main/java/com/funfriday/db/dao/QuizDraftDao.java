package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.QuizDraftAnswerRecord;
import com.funfriday.db.model.QuizQuestionDraftRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin review storage backed by the standard quiz tables. A draft is simply a
 * quiz_questions row where is_active = FALSE.
 */
public class QuizDraftDao {
    private final DatabaseConnectionProvider connectionProvider;

    public QuizDraftDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public long createDraft(String questionKey, String category, String questionType, String prompt,
                            String model, List<QuizDraftAnswerRecord> answers) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO quiz_questions(question_key, category, question_type, prompt, is_active)
                    VALUES (?, ?, ?, ?, FALSE)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, questionKey);
                statement.setString(2, category);
                statement.setString(3, questionType);
                statement.setString(4, prompt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Draft question id was not generated.");
                    long questionId = keys.getLong(1);
                    insertAnswers(connection, questionId, answers);
                    connection.commit();
                    return questionId;
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<QuizQuestionDraftRecord> listDrafts(String ignoredStatus) throws SQLException {
        return listQuestions(false, "DRAFT");
    }

    public List<QuizQuestionDraftRecord> listActiveQuestions() throws SQLException {
        return listQuestions(true, "ACTIVE");
    }

    public List<String> randomPrompts(String category, int limit) throws SQLException {
        List<String> prompts = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                    SELECT prompt FROM quiz_questions
                    WHERE category = ?
                    ORDER BY RAND() LIMIT ?
                    """)) {
            statement.setString(1, category);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) prompts.add(resultSet.getString("prompt"));
            }
        }
        return prompts;
    }

    /**
     * Declined drafts are deleted from the playable-question tables, but their
     * editor feedback remains available to guide future LLM generations.
     */
    public List<String> randomDeclineReasons(String category, int limit) throws SQLException {
        List<String> reasons = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                    SELECT decline_reason
                    FROM quiz_question_decline_feedback
                    WHERE category = ?
                    ORDER BY RAND() LIMIT ?
                    """)) {
            statement.setString(1, category);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) reasons.add(resultSet.getString("decline_reason"));
            }
        }
        return reasons;
    }

    private List<QuizQuestionDraftRecord> listQuestions(boolean active, String status) throws SQLException {
        List<QuizQuestionDraftRecord> drafts = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection()) {
            List<QuizQuestionDraftRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, question_key, category, question_type, prompt, created_at, updated_at
                    FROM quiz_questions WHERE is_active = ? ORDER BY created_at DESC
                    """ );
             ) {
                statement.setBoolean(1, active);
                try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new QuizQuestionDraftRow(resultSet.getLong("id"), resultSet.getString("question_key"),
                            resultSet.getString("category"), resultSet.getString("question_type"), resultSet.getString("prompt"),
                            resultSet.getTimestamp("created_at"), resultSet.getTimestamp("updated_at")));
                }
                }
            }
            for (QuizQuestionDraftRow row : rows) drafts.add(readQuestion(connection, row, status));
        }
        return drafts;
    }

    public boolean approve(long questionId) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            if (!isCompleteDraft(connection, questionId)) return false;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE quiz_questions SET is_active = TRUE WHERE id = ? AND is_active = FALSE
                    """)) {
                statement.setLong(1, questionId);
                return statement.executeUpdate() == 1;
            }
        }
    }

    private boolean isCompleteDraft(Connection connection, long questionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT qq.question_type, COUNT(qa.id) AS answer_count,
                       SUM(CASE WHEN qa.hint IS NULL OR TRIM(qa.hint) = '' THEN 1 ELSE 0 END) AS missing_hints
                FROM quiz_questions qq
                LEFT JOIN quiz_answers qa ON qa.question_id = qq.id
                WHERE qq.id = ? AND qq.is_active = FALSE
                GROUP BY qq.id, qq.question_type
                """)) {
            statement.setLong(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return false;
                int answerCount = resultSet.getInt("answer_count");
                if (answerCount < 8) throw new IllegalArgumentException("A draft needs at least eight answers before approval.");
                if ("CHRONOLOGY".equals(resultSet.getString("question_type")) && resultSet.getInt("missing_hints") > 0) {
                    throw new IllegalArgumentException("Every chronology answer needs a hint before approval.");
                }
                return true;
            }
        }
    }

    public boolean updateDraft(long questionId, String prompt, List<QuizDraftAnswerRecord> answers) throws SQLException {
        return updateQuestion(questionId, prompt, answers, false);
    }

    public boolean updateActiveQuestion(long questionId, String prompt, List<QuizDraftAnswerRecord> answers) throws SQLException {
        return updateQuestion(questionId, prompt, answers, true);
    }

    private boolean updateQuestion(long questionId, String prompt, List<QuizDraftAnswerRecord> answers, boolean active) throws SQLException {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("Question text is required.");
        if (answers == null || answers.isEmpty()) throw new IllegalArgumentException("A draft needs at least one answer.");
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement question = connection.prepareStatement("""
                        UPDATE quiz_questions SET prompt = ? WHERE id = ? AND is_active = ?
                        """)) {
                    question.setString(1, prompt.trim());
                    question.setLong(2, questionId);
                    question.setBoolean(3, active);
                    if (question.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                List<Long> existingAnswerIds = selectAnswerIds(connection, questionId);
                List<Long> submittedAnswerIds = answers.stream().map(QuizDraftAnswerRecord::id).filter(id -> id > 0).toList();
                for (QuizDraftAnswerRecord answer : answers) {
                    if (answer.id() > 0) updateAnswer(connection, questionId, answer, active);
                    else insertAnswers(connection, questionId, List.of(answer));
                }
                for (Long answerId : existingAnswerIds) {
                    if (!submittedAnswerIds.contains(answerId)) deleteAnswer(connection, answerId);
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public boolean decline(long questionId, String reason) throws SQLException {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A decline reason is required.");
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement feedback = connection.prepareStatement("""
                        INSERT INTO quiz_question_decline_feedback
                            (question_key, category, question_type, prompt, decline_reason)
                        SELECT question_key, category, question_type, prompt, ?
                        FROM quiz_questions
                        WHERE id = ? AND is_active = FALSE
                        """)) {
                    feedback.setString(1, reason.trim());
                    feedback.setLong(2, questionId);
                    if (feedback.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                try (PreparedStatement aliases = connection.prepareStatement("""
                        DELETE qaa FROM quiz_answer_aliases qaa
                        JOIN quiz_answers qa ON qa.id = qaa.answer_id
                        JOIN quiz_questions qq ON qq.id = qa.question_id
                        WHERE qq.id = ? AND qq.is_active = FALSE
                        """)) {
                    aliases.setLong(1, questionId);
                    aliases.executeUpdate();
                }
                try (PreparedStatement answers = connection.prepareStatement("""
                        DELETE qa FROM quiz_answers qa
                        JOIN quiz_questions qq ON qq.id = qa.question_id
                        WHERE qq.id = ? AND qq.is_active = FALSE
                        """)) {
                    answers.setLong(1, questionId);
                    answers.executeUpdate();
                }
                try (PreparedStatement question = connection.prepareStatement("DELETE FROM quiz_questions WHERE id = ? AND is_active = FALSE")) {
                    question.setLong(1, questionId);
                    boolean deleted = question.executeUpdate() == 1;
                    connection.commit();
                    return deleted;
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void insertAnswers(Connection connection, long questionId, List<QuizDraftAnswerRecord> answers) throws SQLException {
        try (PreparedStatement answerStatement = connection.prepareStatement("""
                INSERT INTO quiz_answers(question_id, canonical_answer, display_order, hint) VALUES (?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement aliasStatement = connection.prepareStatement("""
                INSERT INTO quiz_answer_aliases(answer_id, alias) VALUES (?, ?)
                """)) {
            for (QuizDraftAnswerRecord answer : answers) {
                answerStatement.setLong(1, questionId);
                answerStatement.setString(2, answer.canonicalAnswer());
                answerStatement.setInt(3, answer.displayOrder());
                answerStatement.setString(4, answer.hint());
                answerStatement.executeUpdate();
                try (ResultSet keys = answerStatement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Answer id was not generated.");
                    long answerId = keys.getLong(1);
                    for (String alias : answer.aliases()) {
                        aliasStatement.setLong(1, answerId);
                        aliasStatement.setString(2, alias);
                        aliasStatement.addBatch();
                    }
                }
            }
            aliasStatement.executeBatch();
        }
    }

    private void updateAnswer(Connection connection, long questionId, QuizDraftAnswerRecord answer, boolean active) throws SQLException {
        if (answer.canonicalAnswer() == null || answer.canonicalAnswer().isBlank()) {
            throw new IllegalArgumentException("Each answer needs text.");
        }
        try (PreparedStatement answerStatement = connection.prepareStatement("""
                UPDATE quiz_answers qa
                JOIN quiz_questions qq ON qq.id = qa.question_id
                SET qa.canonical_answer = ?, qa.hint = ?
                WHERE qa.id = ? AND qq.id = ? AND qq.is_active = ?
                """)) {
            answerStatement.setString(1, answer.canonicalAnswer().trim());
            answerStatement.setString(2, answer.hint() == null || answer.hint().isBlank() ? null : answer.hint().trim());
            answerStatement.setLong(3, answer.id());
            answerStatement.setLong(4, questionId);
            answerStatement.setBoolean(5, active);
            if (answerStatement.executeUpdate() != 1) throw new SQLException("Draft answer " + answer.id() + " was not found.");
        }
        try (PreparedStatement deleteAliases = connection.prepareStatement("DELETE FROM quiz_answer_aliases WHERE answer_id = ?")) {
            deleteAliases.setLong(1, answer.id());
            deleteAliases.executeUpdate();
        }
        try (PreparedStatement insertAlias = connection.prepareStatement("INSERT INTO quiz_answer_aliases(answer_id, alias) VALUES (?, ?)")) {
            for (String alias : sanitizeAliases(answer.aliases(), answer.canonicalAnswer())) {
                insertAlias.setLong(1, answer.id());
                insertAlias.setString(2, alias);
                insertAlias.addBatch();
            }
            insertAlias.executeBatch();
        }
    }

    private List<Long> selectAnswerIds(Connection connection, long questionId) throws SQLException {
        List<Long> answerIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM quiz_answers WHERE question_id = ?")) {
            statement.setLong(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) answerIds.add(resultSet.getLong("id"));
            }
        }
        return answerIds;
    }

    private void deleteAnswer(Connection connection, long answerId) throws SQLException {
        try (PreparedStatement aliases = connection.prepareStatement("DELETE FROM quiz_answer_aliases WHERE answer_id = ?")) {
            aliases.setLong(1, answerId);
            aliases.executeUpdate();
        }
        try (PreparedStatement answer = connection.prepareStatement("DELETE FROM quiz_answers WHERE id = ?")) {
            answer.setLong(1, answerId);
            answer.executeUpdate();
        }
    }

    private List<String> sanitizeAliases(List<String> aliases, String canonicalAnswer) {
        if (aliases == null) return List.of();
        return aliases.stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .map(String::trim)
                .filter(alias -> !alias.equalsIgnoreCase(canonicalAnswer.trim()))
                .distinct()
                .toList();
    }

    private QuizQuestionDraftRecord readQuestion(Connection connection, QuizQuestionDraftRow row, String status) throws SQLException {
        return new QuizQuestionDraftRecord(row.id(), row.questionKey(), row.category(), row.questionType(), row.prompt(), status, null,
                row.createdAt().toInstant(), row.updatedAt() == null ? null : row.updatedAt().toInstant(),
                selectAnswers(connection, row.id()));
    }

    private List<QuizDraftAnswerRecord> selectAnswers(Connection connection, long questionId) throws SQLException {
        List<QuizDraftAnswerRecord> answers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, canonical_answer, display_order, hint
                FROM quiz_answers WHERE question_id = ? ORDER BY display_order
                """)) {
            statement.setLong(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long answerId = resultSet.getLong("id");
                    answers.add(new QuizDraftAnswerRecord(answerId, resultSet.getString("canonical_answer"),
                            resultSet.getInt("display_order"), resultSet.getString("hint"), selectAliases(connection, answerId)));
                }
            }
        }
        return answers;
    }

    private List<String> selectAliases(Connection connection, long answerId) throws SQLException {
        List<String> aliases = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT alias FROM quiz_answer_aliases WHERE answer_id = ? ORDER BY id
                """)) {
            statement.setLong(1, answerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) aliases.add(resultSet.getString("alias"));
            }
        }
        return aliases;
    }

    private record QuizQuestionDraftRow(long id, String questionKey, String category, String questionType,
                                        String prompt, Timestamp createdAt, Timestamp updatedAt) { }
}

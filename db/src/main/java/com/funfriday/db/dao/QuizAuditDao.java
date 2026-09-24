package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.QuizAuditRecord;
import com.funfriday.db.model.QuizAuditSuggestionRecord;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuizAuditDao {
    private final DatabaseConnectionProvider connectionProvider;

    public QuizAuditDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public long create(long questionId, String questionKey, String category, String questionType, String prompt,
                       String model, String status, String suggestionsJson) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO quiz_question_audits
                         (question_id, question_key, category, question_type, prompt, model, status, suggestions_json)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, questionId);
            statement.setString(2, questionKey);
            statement.setString(3, category);
            statement.setString(4, questionType);
            statement.setString(5, prompt);
            statement.setString(6, model);
            statement.setString(7, status);
            statement.setString(8, suggestionsJson);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Audit id was not generated.");
                return keys.getLong(1);
            }
        }
    }

    public List<QuizAuditRecord> listPending() throws SQLException {
        List<QuizAuditRecord> audits = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, question_id, question_key, category, question_type, prompt, status, model,
                            suggestions_json, decline_reason, created_at, reviewed_at
                     FROM quiz_question_audits
                     WHERE status = 'PENDING'
                     ORDER BY created_at DESC
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) audits.add(read(resultSet));
        }
        return audits;
    }

    public List<String> randomDeclineReasons(String category, int limit) throws SQLException {
        List<String> reasons = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT decline_reason FROM quiz_question_audits
                     WHERE category = ? AND status = 'DECLINED' AND decline_reason IS NOT NULL
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

    public boolean updateSuggestions(long auditId, String suggestionsJson) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE quiz_question_audits SET suggestions_json = ?
                     WHERE id = ? AND status = 'PENDING'
                     """)) {
            statement.setString(1, suggestionsJson);
            statement.setLong(2, auditId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean markDeclined(long auditId, String reason) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE quiz_question_audits
                     SET status = 'DECLINED', decline_reason = ?, reviewed_at = CURRENT_TIMESTAMP
                     WHERE id = ? AND status = 'PENDING'
                     """)) {
            statement.setString(1, reason.trim());
            statement.setLong(2, auditId);
            return statement.executeUpdate() == 1;
        }
    }

    public QuizAuditRecord findPending(long auditId) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, question_id, question_key, category, question_type, prompt, status, model,
                            suggestions_json, decline_reason, created_at, reviewed_at
                     FROM quiz_question_audits WHERE id = ? AND status = 'PENDING'
                     """)) {
            statement.setLong(1, auditId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? read(resultSet) : null;
            }
        }
    }

    public boolean markAccepted(Connection connection, long auditId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE quiz_question_audits SET status = 'ACCEPTED', reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING'
                """)) {
            statement.setLong(1, auditId);
            return statement.executeUpdate() == 1;
        }
    }

    /** Applies the editor-approved additions/removals atomically, then marks the audit accepted. */
    public boolean accept(long auditId, List<QuizAuditSuggestionRecord> suggestions) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long questionId = selectPendingQuestionId(connection, auditId);
                if (questionId == 0) {
                    connection.rollback();
                    return false;
                }
                List<StoredAnswer> finalAnswers = selectAnswers(connection, questionId);
                Set<String> removals = new HashSet<>();
                for (QuizAuditSuggestionRecord suggestion : suggestions) {
                    if ("REMOVE".equalsIgnoreCase(suggestion.action()) && hasText(suggestion.canonicalAnswer())) {
                        removals.add(normalize(suggestion.canonicalAnswer()));
                    }
                }
                finalAnswers.removeIf(answer -> removals.contains(normalize(answer.canonicalAnswer())));

                Set<String> knownAnswers = new HashSet<>();
                finalAnswers.forEach(answer -> knownAnswers.add(normalize(answer.canonicalAnswer())));
                suggestions.stream()
                        .filter(suggestion -> "ADD".equalsIgnoreCase(suggestion.action()) && hasText(suggestion.canonicalAnswer()))
                        .sorted(Comparator.comparingInt(QuizAuditSuggestionRecord::displayOrder))
                        .forEach(suggestion -> {
                            String normalized = normalize(suggestion.canonicalAnswer());
                            if (!knownAnswers.add(normalized)) return;
                            int position = suggestion.displayOrder() <= 0 ? finalAnswers.size()
                                    : Math.min(suggestion.displayOrder() - 1, finalAnswers.size());
                            finalAnswers.add(position, new StoredAnswer(suggestion.canonicalAnswer().trim(), suggestion.hint(), sanitizeAliases(suggestion.aliases(), suggestion.canonicalAnswer())));
                        });
                if (finalAnswers.isEmpty()) throw new IllegalArgumentException("An audit cannot remove every answer from a question.");

                deleteAnswers(connection, questionId);
                insertAnswers(connection, questionId, finalAnswers);
                if (!markAccepted(connection, auditId)) throw new SQLException("Audit is no longer pending.");
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private long selectPendingQuestionId(Connection connection, long auditId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT audit.question_id
                FROM quiz_question_audits audit
                JOIN quiz_questions question ON question.id = audit.question_id
                WHERE audit.id = ? AND audit.status = 'PENDING' AND question.is_active = TRUE
                """)) {
            statement.setLong(1, auditId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        }
    }

    private List<StoredAnswer> selectAnswers(Connection connection, long questionId) throws SQLException {
        List<StoredAnswer> answers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, canonical_answer, hint FROM quiz_answers WHERE question_id = ? ORDER BY display_order
                """)) {
            statement.setLong(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long answerId = resultSet.getLong("id");
                    answers.add(new StoredAnswer(resultSet.getString("canonical_answer"), resultSet.getString("hint"), selectAliases(connection, answerId)));
                }
            }
        }
        return answers;
    }

    private List<String> selectAliases(Connection connection, long answerId) throws SQLException {
        List<String> aliases = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT alias FROM quiz_answer_aliases WHERE answer_id = ? ORDER BY id")) {
            statement.setLong(1, answerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) aliases.add(resultSet.getString(1));
            }
        }
        return aliases;
    }

    private void deleteAnswers(Connection connection, long questionId) throws SQLException {
        try (PreparedStatement aliases = connection.prepareStatement("""
                DELETE alias FROM quiz_answer_aliases alias JOIN quiz_answers answer ON answer.id = alias.answer_id
                WHERE answer.question_id = ?
                """);
             PreparedStatement answers = connection.prepareStatement("DELETE FROM quiz_answers WHERE question_id = ?")) {
            aliases.setLong(1, questionId);
            aliases.executeUpdate();
            answers.setLong(1, questionId);
            answers.executeUpdate();
        }
    }

    private void insertAnswers(Connection connection, long questionId, List<StoredAnswer> answers) throws SQLException {
        try (PreparedStatement answerStatement = connection.prepareStatement("""
                INSERT INTO quiz_answers(question_id, canonical_answer, display_order, hint) VALUES (?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement aliasStatement = connection.prepareStatement("INSERT INTO quiz_answer_aliases(answer_id, alias) VALUES (?, ?)")) {
            for (int index = 0; index < answers.size(); index++) {
                StoredAnswer answer = answers.get(index);
                answerStatement.setLong(1, questionId);
                answerStatement.setString(2, answer.canonicalAnswer());
                answerStatement.setInt(3, index + 1);
                answerStatement.setString(4, hasText(answer.hint()) ? answer.hint().trim() : null);
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

    private List<String> sanitizeAliases(List<String> aliases, String canonicalAnswer) {
        if (aliases == null) return List.of();
        return aliases.stream().filter(QuizAuditDao::hasText).map(String::trim)
                .filter(alias -> !alias.equalsIgnoreCase(canonicalAnswer.trim())).distinct().toList();
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String normalize(String value) { return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(); }
    private record StoredAnswer(String canonicalAnswer, String hint, List<String> aliases) { }

    private QuizAuditRecord read(ResultSet resultSet) throws SQLException {
        Timestamp reviewedAt = resultSet.getTimestamp("reviewed_at");
        return new QuizAuditRecord(resultSet.getLong("id"), resultSet.getLong("question_id"), resultSet.getString("question_key"),
                resultSet.getString("category"), resultSet.getString("question_type"), resultSet.getString("prompt"),
                resultSet.getString("status"), resultSet.getString("model"), resultSet.getString("suggestions_json"),
                resultSet.getString("decline_reason"), resultSet.getTimestamp("created_at").toInstant(),
                reviewedAt == null ? null : reviewedAt.toInstant());
    }
}

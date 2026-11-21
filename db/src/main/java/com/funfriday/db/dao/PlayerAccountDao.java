package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Persists the profile claims obtained from a verified Google ID token. */
public class PlayerAccountDao {
    private final DatabaseConnectionProvider connectionProvider;

    public PlayerAccountDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void upsert(
            String playerId,
            String googleSubject,
            String email,
            String displayName,
            String pictureUrl) throws SQLException {
        if (isBlank(playerId) || isBlank(googleSubject) || isBlank(email) || isBlank(displayName)) {
            throw new IllegalArgumentException("A verified player profile is incomplete.");
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_accounts
                         (player_id, google_subject, email, display_name, picture_url)
                     VALUES (?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                         email = VALUES(email),
                         display_name = VALUES(display_name),
                         picture_url = VALUES(picture_url),
                         updated_at = CURRENT_TIMESTAMP
                     """)) {
            statement.setString(1, playerId);
            statement.setString(2, googleSubject);
            statement.setString(3, email.trim().toLowerCase());
            statement.setString(4, displayName.trim());
            statement.setString(5, pictureUrl == null ? "" : pictureUrl.trim());
            statement.executeUpdate();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;

import java.sql.*;
import java.util.Optional;

public class AdminPasswordDao {
    private final DatabaseConnectionProvider connectionProvider;

    public AdminPasswordDao(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public boolean hasActivePassword() throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM admin_passwords WHERE is_active = TRUE LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    public Optional<String> findMatchingHash(String rawPassword) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT password_hash FROM admin_passwords WHERE is_active = TRUE" );
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String hash = resultSet.getString("password_hash");
                if (org.springframework.security.crypto.bcrypt.BCrypt.checkpw(rawPassword, hash)) return Optional.of(hash);
            }
            return Optional.empty();
        }
    }

    public void addPassword(String label, String rawPassword) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO admin_passwords(label, password_hash, is_active) VALUES (?, ?, TRUE)
                    """)) {
            statement.setString(1, label);
            statement.setString(2, org.springframework.security.crypto.bcrypt.BCrypt.hashpw(rawPassword,
                    org.springframework.security.crypto.bcrypt.BCrypt.gensalt(12)));
            statement.executeUpdate();
        }
    }
}

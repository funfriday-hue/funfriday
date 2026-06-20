package com.funfriday.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class MySqlConnectionProvider implements DatabaseConnectionProvider {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/GameData";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "";
    private static final int DEFAULT_MAX_POOL_SIZE = 10;

    private final HikariDataSource dataSource;

    public MySqlConnectionProvider() {
        this(DEFAULT_URL, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    public MySqlConnectionProvider(String url) {
        this(url, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    public MySqlConnectionProvider(String url, String username, String password) {
        this(url, username, password, DEFAULT_MAX_POOL_SIZE);
    }

    public MySqlConnectionProvider(String url, String username, String password, int maximumPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setPoolName("funfriday-mysql-pool");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}

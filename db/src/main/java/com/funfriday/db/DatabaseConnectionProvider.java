package com.funfriday.db;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseConnectionProvider extends AutoCloseable {
    Connection getConnection() throws SQLException;

    @Override
    default void close() {
    }
}

package com.funfriday.config;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.MySqlConnectionProvider;
import com.funfriday.db.dao.SudokuDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {

    @Bean(destroyMethod = "close")
    public DatabaseConnectionProvider databaseConnectionProvider() {
        return new MySqlConnectionProvider();
    }

    @Bean
    public SudokuDao sudokuDao(DatabaseConnectionProvider databaseConnectionProvider) {
        return new SudokuDao(databaseConnectionProvider);
    }
}

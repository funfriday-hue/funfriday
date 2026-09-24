package com.funfriday.config;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.MySqlConnectionProvider;
import com.funfriday.db.dao.QuizQuestionDao;
import com.funfriday.db.dao.QuizDraftDao;
import com.funfriday.db.dao.AdminPasswordDao;
import com.funfriday.db.dao.SudokuDao;
import com.funfriday.db.dao.QuizAuditDao;
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

    @Bean
    public QuizQuestionDao quizQuestionDao(DatabaseConnectionProvider databaseConnectionProvider) {
        return new QuizQuestionDao(databaseConnectionProvider);
    }

    @Bean
    public QuizDraftDao quizDraftDao(DatabaseConnectionProvider databaseConnectionProvider) {
        return new QuizDraftDao(databaseConnectionProvider);
    }

    @Bean
    public QuizAuditDao quizAuditDao(DatabaseConnectionProvider databaseConnectionProvider) {
        return new QuizAuditDao(databaseConnectionProvider);
    }

    @Bean
    public AdminPasswordDao adminPasswordDao(DatabaseConnectionProvider databaseConnectionProvider) {
        return new AdminPasswordDao(databaseConnectionProvider);
    }
}

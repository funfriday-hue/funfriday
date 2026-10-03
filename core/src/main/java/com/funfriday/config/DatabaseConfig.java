package com.funfriday.config;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.MySqlConnectionProvider;
import com.funfriday.db.dao.QuizQuestionDao;
import com.funfriday.db.dao.QuizDraftDao;
import com.funfriday.db.dao.AdminPasswordDao;
import com.funfriday.db.dao.SudokuDao;
import com.funfriday.db.dao.QuizAuditDao;
import com.funfriday.db.dao.GameMetricsDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class DatabaseConfig {

    @Bean(destroyMethod = "close")
    @Primary
    public DatabaseConnectionProvider databaseConnectionProvider() {
        return new MySqlConnectionProvider();
    }

    /** Analytics is isolated from live game and question data. */
    @Bean(name = "gameMetricsDatabaseConnectionProvider", destroyMethod = "close")
    public DatabaseConnectionProvider gameMetricsDatabaseConnectionProvider() {
        return new MySqlConnectionProvider(
                System.getenv().getOrDefault("GAME_METRICS_DB_URL", "jdbc:mysql://127.0.0.1:3306/GameMetrics"),
                System.getenv().getOrDefault("GAME_METRICS_DB_USERNAME", "funfriday_app"),
                System.getenv().getOrDefault("GAME_METRICS_DB_PASSWORD", "funfriday_pass")
        );
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

    @Bean
    public GameMetricsDao gameMetricsDao(@Qualifier("gameMetricsDatabaseConnectionProvider") DatabaseConnectionProvider databaseConnectionProvider) {
        return new GameMetricsDao(databaseConnectionProvider);
    }
}

package com.funfriday.service;

import com.funfriday.db.dao.AdminPasswordDao;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminPasswordBootstrap {
    private final AdminPasswordDao adminPasswordDao;

    @PostConstruct
    void createInitialPasswordFromEnvironment() {
        String password = System.getenv("FUNFRIDAY_ADMIN_BOOTSTRAP_PASSWORD");
        if (password == null || password.isBlank()) return;
        try {
            if (!adminPasswordDao.hasActivePassword()) {
                if (password.length() < 12) throw new IllegalStateException("FUNFRIDAY_ADMIN_BOOTSTRAP_PASSWORD must be at least 12 characters.");
                adminPasswordDao.addPassword("bootstrap", password);
                log.info("Created initial Quiz Royale admin password from environment configuration.");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to configure the Quiz Royale admin password.", exception);
        }
    }
}

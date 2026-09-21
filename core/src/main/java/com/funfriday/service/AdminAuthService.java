package com.funfriday.service;

import com.funfriday.db.dao.AdminPasswordDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private static final long SESSION_LIFETIME_MILLIS = 12 * 60 * 60 * 1000L;
    private final AdminPasswordDao adminPasswordDao;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public String login(String password) throws Exception {
        if (password == null || password.isBlank() || adminPasswordDao.findMatchingHash(password).isEmpty()) return null;
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, Instant.now().toEpochMilli() + SESSION_LIFETIME_MILLIS);
        return token;
    }

    public boolean isAuthorized(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return false;
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (expiry <= Instant.now().toEpochMilli()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void addPassword(String label, String password) throws Exception {
        if (label == null || label.isBlank() || password == null || password.length() < 12) {
            throw new IllegalArgumentException("A label and a password of at least 12 characters are required.");
        }
        adminPasswordDao.addPassword(label.trim(), password);
    }
}

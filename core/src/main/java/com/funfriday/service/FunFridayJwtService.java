package com.funfriday.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Small HMAC-SHA256 JWT implementation used only for FunFriday's first-party session cookie. */
@Service
public class FunFridayJwtService {
    public static final String SESSION_COOKIE = "funfridaySession";
    private static final String ISSUER = "funfriday.co.in";
    private static final long SESSION_SECONDS = 60L * 60 * 24 * 30;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String secret = System.getenv("FUNFRIDAY_JWT_SECRET");

    public boolean configured() {
        return secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    public String issue(GoogleIdentityService.GoogleProfile profile) {
        if (!configured()) throw new IllegalStateException("FUNFRIDAY_JWT_SECRET is missing or too short");
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", profile.subject());
        claims.put("pid", playerIdFor(profile.subject()));
        claims.put("name", profile.name());
        claims.put("picture", profile.pictureUrl() == null ? "" : profile.pictureUrl());
        claims.put("iat", now);
        claims.put("exp", now + SESSION_SECONDS);
        try {
            String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = encode(objectMapper.writeValueAsBytes(claims));
            String signingInput = header + "." + payload;
            return signingInput + "." + encode(hmac(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue session token", exception);
        }
    }

    public Optional<PlayerPrincipal> currentUser(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName())) return parse(cookie.getValue());
        }
        return Optional.empty();
    }

    public Optional<PlayerPrincipal> parse(String token) {
        if (!configured() || token == null) return Optional.empty();
        try {
            String[] pieces = token.split("\\.");
            if (pieces.length != 3) return Optional.empty();
            String signingInput = pieces[0] + "." + pieces[1];
            if (!MessageDigest.isEqual(hmac(signingInput.getBytes(StandardCharsets.US_ASCII)), decode(pieces[2]))) return Optional.empty();
            Map<String, Object> claims = objectMapper.readValue(decode(pieces[1]), new TypeReference<>() {});
            if (!ISSUER.equals(claims.get("iss")) || !(claims.get("exp") instanceof Number expiry)
                    || expiry.longValue() <= Instant.now().getEpochSecond()) return Optional.empty();
            String subject = (String) claims.get("sub");
            String playerId = (String) claims.get("pid");
            String name = (String) claims.get("name");
            if (subject == null || playerId == null || name == null || !playerId.equals(playerIdFor(subject))) return Optional.empty();
            return Optional.of(new PlayerPrincipal(playerId, name, (String) claims.getOrDefault("picture", "")));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * Stable UUID-like ID derived from Google's immutable subject. It deliberately does not
     * depend on the JWT signing key, so routine session-key rotation never changes a player.
     */
    public String playerIdFor(String googleSubject) {
        byte[] bytes;
        try {
            bytes = MessageDigest.getInstance("SHA-256")
                    .digest(("funfriday-player-id:" + googleSubject).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive player identity", exception);
        }
        long most = 0;
        long least = 0;
        for (int index = 0; index < 8; index++) most = (most << 8) | (bytes[index] & 0xffL);
        for (int index = 8; index < 16; index++) least = (least << 8) | (bytes[index] & 0xffL);
        return new UUID(most, least).toString();
    }

    private byte[] hmac(byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign session token", exception);
        }
    }

    private static String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static byte[] decode(String value) { return Base64.getUrlDecoder().decode(value); }
    public record PlayerPrincipal(String playerId, String name, String pictureUrl) { }
}

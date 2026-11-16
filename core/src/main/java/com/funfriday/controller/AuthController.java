package com.funfriday.controller;

import com.funfriday.service.FunFridayJwtService;
import com.funfriday.service.GoogleIdentityService;
import com.funfriday.db.dao.PlayerQuestionProgressDao;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@Slf4j
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AuthController {
    private static final int ONE_YEAR_SECONDS = 60 * 60 * 24 * 365;
    private final GoogleIdentityService googleIdentityService;
    private final FunFridayJwtService jwtService;
    private final PlayerQuestionProgressDao playerQuestionProgressDao;

    /** Client IDs are public OAuth configuration; credentials remain server-side. */
    @GetMapping("/google/config")
    public Map<String, String> googleConfig() {
        return Map.of("clientId", googleIdentityService.clientId());
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleSignIn(@RequestBody GoogleCredentialRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        try {
            Optional<GoogleIdentityService.GoogleProfile> profile = googleIdentityService.verify(request.credential());
            if (profile.isEmpty()) return ResponseEntity.status(401).body(Map.of("error", "Google sign-in could not be verified."));
            if (!jwtService.configured()) return ResponseEntity.internalServerError().body(Map.of("error", "Google sign-in is not configured on this server."));

            GoogleIdentityService.GoogleProfile verifiedProfile = profile.get();
            String stablePlayerId = jwtService.playerIdFor(verifiedProfile.subject());
            String guestPlayerId = readCookie(httpRequest, "playerId");
            playerQuestionProgressDao.mergePlayerProgress(guestPlayerId, stablePlayerId);
            Cookie playerId = new Cookie("playerId", stablePlayerId);
            playerId.setPath("/");
            playerId.setHttpOnly(false); // existing WebSocket admission reads this stable game identity.
            playerId.setSecure(httpRequest.isSecure() || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto")));
            playerId.setMaxAge(ONE_YEAR_SECONDS);
            response.addCookie(playerId);
            Cookie session = new Cookie(FunFridayJwtService.SESSION_COOKIE, jwtService.issue(verifiedProfile));
            session.setPath("/");
            session.setHttpOnly(true);
            session.setSecure(httpRequest.isSecure() || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto")));
            session.setMaxAge(60 * 60 * 24 * 30);
            response.addCookie(session);

            return ResponseEntity.ok(Map.of(
                    "playerId", stablePlayerId,
                    "name", verifiedProfile.name(),
                    "pictureUrl", verifiedProfile.pictureUrl() == null ? "" : verifiedProfile.pictureUrl()
            ));
        } catch (Exception exception) {
            log.error("Google sign-in failed", exception);
            return ResponseEntity.internalServerError().body(Map.of("error", "Unable to complete Google sign-in."));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(HttpServletRequest request) {
        return jwtService.currentUser(request)
                .<ResponseEntity<?>>map(account -> ResponseEntity.ok(Map.of(
                            "signedIn", true,
                            "playerId", account.playerId(),
                            "name", account.name(),
                            "pictureUrl", account.pictureUrl()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of("signedIn", false)));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        clearCookie(response, FunFridayJwtService.SESSION_COOKIE, true, secure);
        clearCookie(response, "playerId", false, secure);
        return ResponseEntity.ok(Map.of("signedIn", false));
    }

    private void clearCookie(HttpServletResponse response, String name, boolean httpOnly, boolean secure) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    public record GoogleCredentialRequest(String credential) { }
}

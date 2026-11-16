package com.funfriday.controller;

import com.funfriday.service.FunFridayJwtService;
import com.funfriday.service.GoogleIdentityService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AuthController {
    private static final int ONE_YEAR_SECONDS = 60 * 60 * 24 * 365;
    private final GoogleIdentityService googleIdentityService;
    private final FunFridayJwtService jwtService;

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

    public record GoogleCredentialRequest(String credential) { }
}

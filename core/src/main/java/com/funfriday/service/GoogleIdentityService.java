package com.funfriday.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

/** Validates Google Identity Services tokens before any account data is trusted. */
@Service
public class GoogleIdentityService {
    private final String clientId = System.getenv("GOOGLE_CLIENT_ID");

    public Optional<GoogleProfile> verify(String credential) throws GeneralSecurityException, java.io.IOException {
        if (clientId == null || clientId.isBlank() || credential == null || credential.isBlank()) return Optional.empty();

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
        GoogleIdToken token = verifier.verify(credential);
        if (token == null) return Optional.empty();

        GoogleIdToken.Payload payload = token.getPayload();
        String subject = payload.getSubject();
        String name = (String) payload.get("name");
        String givenName = (String) payload.get("given_name");
        String email = payload.getEmail();
        String picture = (String) payload.get("picture");
        if (subject == null || subject.isBlank() || name == null || name.isBlank()) return Optional.empty();
        String displayName = givenName == null || givenName.isBlank() ? firstName(name) : givenName.trim();
        return Optional.of(new GoogleProfile(subject, displayName, email, picture));
    }

    public String clientId() {
        return clientId == null ? "" : clientId;
    }

    private static String firstName(String fullName) {
        String normalized = fullName.trim();
        int separator = normalized.indexOf(' ');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }

    public record GoogleProfile(String subject, String name, String email, String pictureUrl) { }
}

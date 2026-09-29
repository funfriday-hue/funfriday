package com.funfriday.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** Shared JSON-only LLM gateway for quiz generation, auditing and editor tools. */
@Component
public class LlmJsonClient {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public boolean isConfigured() { return Optional.ofNullable(System.getenv("LLM_API_KEY")).map(value -> !value.isBlank()).orElse(false); }

    public Completion complete(String systemPrompt, String userPrompt, double temperature) throws Exception {
        String apiKey = Optional.ofNullable(System.getenv("LLM_API_KEY")).filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("LLM_API_KEY is not configured."));
        Exception lastFailure = null;
        for (String model : configuredModels()) {
            try {
                return new Completion(model, call(apiKey, model, systemPrompt, userPrompt, temperature));
            } catch (Exception exception) {
                if (!isTransientFailure(exception)) throw exception;
                lastFailure = exception;
            }
        }
        throw new IllegalStateException("All configured LLM models failed temporarily.", lastFailure);
    }

    private JsonNode call(String apiKey, String model, String systemPrompt, String userPrompt, double temperature) throws Exception {
        String apiUrl = Optional.ofNullable(System.getenv("LLM_API_URL")).filter(value -> !value.isBlank())
                .orElse("https://api.openai.com/v1/chat/completions");
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model, "temperature", temperature, "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt))));
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(apiUrl)).timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new LlmRequestException(response.statusCode(), errorMessage(response.body()));
        JsonNode body = objectMapper.readTree(response.body());
        String content = body.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank()) throw new IllegalStateException("LLM response did not contain message content.");
        return objectMapper.readTree(stripCodeFence(content));
    }

    private String errorMessage(String body) { try { return objectMapper.readTree(body).path("error").path("message").asText(body); } catch (Exception ignored) { return body; } }
    private List<String> configuredModels() {
        String configured = Optional.ofNullable(System.getenv("LLM_MODELS")).filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(System.getenv("LLM_MODEL")).filter(value -> !value.isBlank()).orElse("gpt-4.1-mini"));
        List<String> models = Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        if (models.isEmpty()) throw new IllegalStateException("Configure at least one LLM model.");
        return models;
    }
    private boolean isTransientFailure(Exception exception) {
        if (exception instanceof HttpTimeoutException || exception instanceof IOException) return true;
        return exception instanceof LlmRequestException request && (request.statusCode == 408 || request.statusCode == 429 || request.statusCode >= 500);
    }
    private String stripCodeFence(String value) { return value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""); }
    public record Completion(String model, JsonNode json) { }
    public static final class LlmRequestException extends IllegalStateException { private final int statusCode; public LlmRequestException(int statusCode, String message) { super("LLM request failed: HTTP " + statusCode + " — " + message); this.statusCode = statusCode; } public int statusCode() { return statusCode; } }
}

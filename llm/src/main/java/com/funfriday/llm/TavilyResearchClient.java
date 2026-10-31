package com.funfriday.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Retrieves concise, attributable web evidence for the quiz generation pipeline. */
@Component
public class TavilyResearchClient {
    private static final URI SEARCH_URI = URI.create("https://api.tavily.com/search");
    private static final int MAX_SOURCE_CONTENT_CHARS = 2_400;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public boolean isConfigured() {
        return Optional.ofNullable(System.getenv("TAVILY_API_KEY")).map(value -> !value.isBlank()).orElse(false);
    }

    /**
     * Tavily's content field supplies relevant passages from each result. We intentionally cap
     * those passages before they reach an LLM to keep the evidence prompt bounded and predictable.
     */
    public List<ResearchSource> search(String query, int maxResults) throws Exception {
        String apiKey = Optional.ofNullable(System.getenv("TAVILY_API_KEY")).filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("TAVILY_API_KEY is not configured."));
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Research query is required.");

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "query", query.trim(),
                "search_depth", "basic",
                "chunks_per_source", 2,
                "max_results", Math.max(1, Math.min(maxResults, 5)),
                "topic", "general",
                "include_answer", false,
                "include_raw_content", false,
                "include_images", false,
                "include_usage", false,
                "safe_search", true));
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(SEARCH_URI)
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new TavilyRequestException(response.statusCode(), errorMessage(response.body()));
        }

        JsonNode results = objectMapper.readTree(response.body()).path("results");
        List<ResearchSource> sources = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (JsonNode result : results) {
            String url = result.path("url").asText().trim();
            String content = result.path("content").asText().trim();
            if (url.isBlank() || content.isBlank() || !seenUrls.add(url)) continue;
            String title = result.path("title").asText("Untitled source").trim();
            sources.add(new ResearchSource(title, url, truncate(content)));
        }
        if (sources.size() < 4) {
            throw new IllegalStateException("Tavily returned only " + sources.size() + " usable sources; at least four are required.");
        }
        return List.copyOf(sources);
    }

    public String formatForLlm(List<ResearchSource> sources) {
        StringBuilder evidence = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            ResearchSource source = sources.get(index);
            evidence.append("[SOURCE ").append(index + 1).append("]\n")
                    .append("Title: ").append(source.title()).append('\n')
                    .append("URL: ").append(source.url()).append('\n')
                    .append("Extract: ").append(source.content()).append("\n\n");
        }
        return evidence.toString();
    }

    /** A compact, safe-to-log summary that makes each research result traceable. */
    public String formatForLog(List<ResearchSource> sources) {
        StringBuilder summary = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            if (index > 0) summary.append(" | ");
            ResearchSource source = sources.get(index);
            summary.append(index + 1).append(": ")
                    .append(source.title().replaceAll("\\s+", " "))
                    .append(" — ").append(source.url());
        }
        return summary.toString();
    }

    private String errorMessage(String body) {
        try {
            JsonNode parsed = objectMapper.readTree(body);
            String detail = parsed.path("detail").path("error").asText();
            return detail.isBlank() ? body : detail;
        } catch (Exception ignored) {
            return body;
        }
    }

    private String truncate(String content) {
        return content.length() <= MAX_SOURCE_CONTENT_CHARS ? content : content.substring(0, MAX_SOURCE_CONTENT_CHARS) + "…";
    }

    public record ResearchSource(String title, String url, String content) { }

    public static final class TavilyRequestException extends IllegalStateException {
        private final int statusCode;

        public TavilyRequestException(int statusCode, String message) {
            super("Tavily search failed: HTTP " + statusCode + " — " + message);
            this.statusCode = statusCode;
        }

        public int statusCode() { return statusCode; }
    }
}

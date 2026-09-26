package com.funfriday.game.generator.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funfriday.db.dao.QuizAuditDao;
import com.funfriday.db.dao.QuizQuestionDao;
import com.funfriday.db.model.QuizAnswerRecord;
import com.funfriday.db.model.QuizQuestionRecord;
import com.funfriday.game.generator.Generator;
import com.funfriday.game.generator.GeneratorSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
@GeneratorSchedule(interval = "PT3H")
public class QuizQuestionAuditor implements Generator {
    private static final ZoneId QUIZ_TIME_ZONE = ZoneId.of("Asia/Kolkata");
    private final QuizQuestionDao quizQuestionDao;
    private final QuizAuditDao quizAuditDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void generate() throws Exception {
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Quiz audit skipped: LLM_API_KEY is not configured.");
            return;
        }
        QuizQuestionRecord question = quizQuestionDao.selectRandomActive()
                .orElseThrow(() -> new IllegalStateException("No active Quiz Royale question is available to audit."));
        List<String> declinedFeedback = quizAuditDao.randomDeclineReasons(question.category(), 10);
        LocalDate asOfDate = LocalDate.now(QUIZ_TIME_ZONE);
        Exception lastFailure = null;
        for (String model : configuredModels()) {
            try {
                AuditResult result = requestAudit(apiKey, model, question, asOfDate, declinedFeedback);
                validate(result, question.questionType());
                String status = result.suggestions().isEmpty() ? "CORRECT" : "PENDING";
                long auditId = quizAuditDao.create(question.id(), question.questionKey(), question.category(), question.questionType(),
                        question.prompt(), model, status, objectMapper.writeValueAsString(result.suggestions()));
                log.info("Created Quiz Royale audit {} for {} using {} ({})", auditId, question.questionKey(), model, status);
                return;
            } catch (Exception exception) {
                if (!isTransientFailure(exception)) throw exception;
                lastFailure = exception;
                log.warn("Quiz audit with {} failed temporarily; trying next configured model.", model, exception);
            }
        }
        throw new IllegalStateException("All configured LLM models failed temporarily for quiz audit.", lastFailure);
    }

    private AuditResult requestAudit(String apiKey, String model, QuizQuestionRecord question, LocalDate asOfDate, List<String> declinedFeedback) throws Exception {
        String questionJson = objectMapper.writeValueAsString(Map.of(
                "category", question.category(), "type", question.questionType(), "prompt", question.prompt(),
                "answers", question.answers().stream().map(answer -> Map.of(
                        "answer", answer.canonicalAnswer(), "displayOrder", answer.displayOrder(),
                        "hint", answer.hint() == null ? "" : answer.hint(), "aliases", answer.aliases())).toList()));
        String prompt = """
                Audit this Quiz Royale trivia question for factual correctness and completeness as of %s (Asia/Kolkata).
                This is the factual cutoff: include results and releases on or before this date, and reject entries
                after it. Do not assume an older knowledge cutoff (for example 2024) is current.
                For LIST questions, every valid answer must be present exactly once. For CHRONOLOGY questions, the
                answer set must be complete, ordered newest-to-oldest, and every answer must have the correct hint.
                Do not invent uncertain facts. Return no suggestions if it is already correct.

                Question and current answers JSON:
                %s

                Previous human feedback on declined audits in the same category. Use it to improve your audit quality;
                this is feedback, not a request to alter the supplied question:
                %s

                Return JSON only:
                {
                  "suggestions": [
                    {"action":"ADD","canonicalAnswer":"...","displayOrder":1,"hint":"year/event or null","aliases":["..."],"reason":"why this answer is missing"},
                    {"action":"REMOVE","canonicalAnswer":"...","displayOrder":0,"hint":null,"aliases":[],"reason":"why this answer is invalid"}
                  ]
                }
                Only use ADD or REMOVE. For chronology ADD, displayOrder is the desired final one-based position.
                Never add partial film titles or other weak aliases.
                """.formatted(asOfDate, questionJson, declinedFeedback.isEmpty() ? "(none)" : "- " + String.join("\n- ", declinedFeedback));
        String apiUrl = Optional.ofNullable(System.getenv("LLM_API_URL"))
                .filter(value -> !value.isBlank()).orElse("https://api.openai.com/v1/chat/completions");
        String body = objectMapper.writeValueAsString(Map.of(
                "model", model, "temperature", 0.1, "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "system", "content", "You are a meticulous trivia fact-checker. Return valid JSON only."),
                        Map.of("role", "user", "content", prompt))));
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(90)).header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new LlmRequestException(response.statusCode(), response.body());
        JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
        if (content.asText().isBlank()) throw new IllegalStateException("LLM audit response did not contain content.");
        JsonNode suggestions = objectMapper.readTree(stripCodeFence(content.asText())).path("suggestions");
        List<AuditSuggestion> values = new ArrayList<>();
        for (JsonNode suggestion : suggestions) {
            List<String> aliases = new ArrayList<>();
            for (JsonNode alias : suggestion.path("aliases")) aliases.add(alias.asText());
            values.add(new AuditSuggestion(suggestion.path("action").asText(), suggestion.path("canonicalAnswer").asText(),
                    suggestion.path("displayOrder").asInt(0), suggestion.path("hint").isNull() ? null : suggestion.path("hint").asText(),
                    aliases, suggestion.path("reason").asText()));
        }
        return new AuditResult(values);
    }

    private void validate(AuditResult result, String questionType) {
        Set<String> seen = new HashSet<>();
        for (AuditSuggestion suggestion : result.suggestions()) {
            if (!"ADD".equals(suggestion.action()) && !"REMOVE".equals(suggestion.action())) throw new IllegalArgumentException("Audit has an invalid action.");
            if (suggestion.canonicalAnswer() == null || suggestion.canonicalAnswer().isBlank()) throw new IllegalArgumentException("Audit has a blank answer.");
            if ("ADD".equals(suggestion.action()) && "CHRONOLOGY".equals(questionType)
                    && (suggestion.hint() == null || suggestion.hint().isBlank())) throw new IllegalArgumentException("Chronology addition is missing a hint.");
            if (!seen.add(suggestion.action() + ":" + suggestion.canonicalAnswer().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Audit contains duplicate suggestions.");
            }
        }
    }

    private List<String> configuredModels() {
        String configured = Optional.ofNullable(System.getenv("LLM_MODELS")).filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(System.getenv("LLM_MODEL")).filter(value -> !value.isBlank()).orElse("gpt-4.1-mini"));
        return Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private boolean isTransientFailure(Exception exception) {
        if (exception instanceof HttpTimeoutException || exception instanceof IOException) return true;
        return exception instanceof LlmRequestException request && (request.status == 408 || request.status == 429 || request.status >= 500);
    }

    private String stripCodeFence(String value) { return value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""); }
    private record AuditResult(List<AuditSuggestion> suggestions) { }
    private record AuditSuggestion(String action, String canonicalAnswer, int displayOrder, String hint, List<String> aliases, String reason) { }
    private static final class LlmRequestException extends IllegalStateException { private final int status; private LlmRequestException(int status, String message) { super("LLM audit request failed: HTTP " + status + " — " + message); this.status = status; } }
}

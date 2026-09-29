package com.funfriday.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.funfriday.db.dao.QuizDraftDao;
import com.funfriday.db.model.QuizDraftAnswerRecord;
import com.funfriday.llm.LlmJsonClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Turns an editor's proposed question into an editable inactive draft. */
@Service
@RequiredArgsConstructor
public class QuizQuestionCreator {
    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<String> CATEGORIES = Set.of("CRICKET", "FOOTBALL", "BOLLYWOOD", "WWE", "INDIA");
    private static final Set<String> TYPES = Set.of("LIST", "CHRONOLOGY", "RANKED_LIST");
    private final LlmJsonClient llmJsonClient;
    private final QuizDraftDao quizDraftDao;

    public GeneratedQuestion generate(String rawCategory, String rawType, String proposedPrompt, String hintGuidance) throws Exception {
        String category = normalize(rawCategory, CATEGORIES, "category");
        String type = normalize(rawType, TYPES, "question type");
        if (proposedPrompt == null || proposedPrompt.isBlank()) throw new IllegalArgumentException("Question text is required.");
        if (looksLikeQuestionRequest(proposedPrompt)) {
            throw new QuestionDoesNotFitException("Enter the actual playable question, not a request to create one. For example: ‘Name every player in India’s 2024 ICC Men’s T20 World Cup squad.’");
        }
        if ("CHRONOLOGY".equals(type) && (hintGuidance == null || hintGuidance.isBlank())) {
            throw new IllegalArgumentException("Describe the hint each chronology answer should show.");
        }
        LocalDate asOfDate = LocalDate.now(TIME_ZONE);
        String request = """
                You are helping an editor create a Quiz Royale trivia question.
                Proposed category: %s. Proposed type: %s. Today's factual cutoff is %s (Asia/Kolkata).
                Proposed question: %s
                Hint guidance: %s

                First decide whether the proposed question genuinely belongs in the category AND is a specific,
                answerable game question. Reject vague instructions such as “create a sample cricket question”,
                “give me a football quiz”, “make a question about Bollywood”, or any request that leaves the topic,
                scope, event, list, ranking, or factual criteria to you. If it is vague or does not fit the category,
                return {"fitsCategory":false,"isSpecific":false,"message":"brief explanation and an example of a specific playable question","prompt":"","answers":[]}.
                If it does, silently correct its wording so it is self-contained, objective and playable. Never say
                "these", "following", "iconic" or "legendary" to imply an unstated answer list.

                LIST answers may be given in any order. CHRONOLOGY must explicitly say "reverse chronological order",
                have a finite objective set, and provide a concise year/event hint for every answer, newest first.
                RANKED_LIST must define a finite exact ranking, put rank 1 first, and provide a value as the hint for
                every answer. Do not put an as-of date in the final question; it is stored separately.
                A question may have between 8 and 75 answers. Return at least 8 answers for LIST/CHRONOLOGY and at
                least 10 for RANKED_LIST; never return more than 75 answers. Use only genuine aliases,
                never title fragments. Include facts only through today's cutoff.

                Return JSON only:
                {"fitsCategory":true,"isSpecific":true,"message":"","prompt":"corrected question","answers":[{"answer":"...","hint":"null for list; required otherwise","aliases":["..."]}]}
                """.formatted(category, type, asOfDate, proposedPrompt.trim(),
                "CHRONOLOGY".equals(type) ? hintGuidance.trim() : "Use the standard type-appropriate hint.");
        LlmJsonClient.Completion completion = llmJsonClient.complete("You are a meticulous trivia editor. Return valid JSON only.", request, 0.15);
        JsonNode root = completion.json();
        if (!root.path("fitsCategory").asBoolean(false) || !root.path("isSpecific").asBoolean(false)) {
            throw new QuestionDoesNotFitException(root.path("message").asText("Enter a specific, playable question for the selected category."));
        }
        String prompt = root.path("prompt").asText().trim();
        if (prompt.isBlank()) throw new IllegalArgumentException("LLM did not produce a corrected question.");
        List<QuizDraftAnswerRecord> answers = parseAnswers(root.path("answers"), type);
        int minimum = "RANKED_LIST".equals(type) ? 10 : 8;
        if (answers.size() < minimum) throw new IllegalArgumentException("LLM generated fewer than " + minimum + " answers.");
        if (answers.size() > 75) throw new IllegalArgumentException("LLM generated more than 75 answers.");
        return new GeneratedQuestion(category, type, prompt, completion.model(), answers.stream()
                .map(answer -> new GeneratedAnswer(answer.canonicalAnswer(), answer.displayOrder(), answer.hint(), answer.aliases()))
                .toList());
    }

    public CreatedDraft saveDraft(String rawCategory, String rawType, String prompt, String model,
                                  List<GeneratedAnswer> generatedAnswers) throws Exception {
        String category = normalize(rawCategory, CATEGORIES, "category");
        String type = normalize(rawType, TYPES, "question type");
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("Question text is required.");
        List<QuizDraftAnswerRecord> answers = toAnswerRecords(generatedAnswers, type);
        int minimum = "RANKED_LIST".equals(type) ? 10 : 8;
        if (answers.size() < minimum) throw new IllegalArgumentException("At least " + minimum + " answers are required.");
        if (answers.size() > 75) throw new IllegalArgumentException("A question can have at most 75 answers.");
        String questionKey = "editor_" + category.toLowerCase(Locale.ROOT) + "_" + type.toLowerCase(Locale.ROOT) + "_"
                + Instant.now().toEpochMilli() + "_" + UUID.randomUUID().toString().substring(0, 8);
        long id = quizDraftDao.createDraft(questionKey, category, type, prompt.trim(),
                model == null || model.isBlank() ? "EDITOR" : model.trim(), LocalDate.now(TIME_ZONE), answers);
        return new CreatedDraft(id, model, prompt.trim());
    }

    private List<QuizDraftAnswerRecord> toAnswerRecords(List<GeneratedAnswer> generatedAnswers, String type) {
        if (generatedAnswers == null) return List.of();
        List<QuizDraftAnswerRecord> answers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 1;
        for (GeneratedAnswer input : generatedAnswers) {
            String answer = input.answer() == null ? "" : input.answer().trim();
            String hint = input.hint() == null ? null : input.hint().trim();
            if (answer.isBlank()) throw new IllegalArgumentException("Every answer needs text.");
            String key = answer.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            if (!"CHRONOLOGY".equals(type) && !seen.add(key)) throw new IllegalArgumentException("Duplicate answer: " + answer);
            if (("CHRONOLOGY".equals(type) || "RANKED_LIST".equals(type)) && (hint == null || hint.isBlank())) {
                throw new IllegalArgumentException("Every chronology or ranked-list answer needs a hint.");
            }
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            if (input.aliases() != null) for (String alias : input.aliases()) {
                if (alias != null && !alias.isBlank() && !alias.trim().equalsIgnoreCase(answer)) aliases.add(alias.trim());
            }
            answers.add(new QuizDraftAnswerRecord(0, answer, index++, "LIST".equals(type) ? null : hint, List.copyOf(aliases)));
        }
        return answers;
    }

    private List<QuizDraftAnswerRecord> parseAnswers(JsonNode nodes, String type) {
        List<QuizDraftAnswerRecord> answers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 1;
        for (JsonNode node : nodes) {
            String answer = node.path("answer").asText().trim();
            String hint = node.path("hint").isNull() ? null : node.path("hint").asText().trim();
            if (answer.isBlank()) throw new IllegalArgumentException("LLM generated a blank answer.");
            if (!"CHRONOLOGY".equals(type) && !seen.add(answer.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("LLM generated a duplicate answer.");
            if (("CHRONOLOGY".equals(type) || "RANKED_LIST".equals(type)) && (hint == null || hint.isBlank())) throw new IllegalArgumentException("Every chronology or ranked-list answer needs a hint.");
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            for (JsonNode alias : node.path("aliases")) if (!alias.asText().isBlank() && !alias.asText().trim().equalsIgnoreCase(answer)) aliases.add(alias.asText().trim());
            answers.add(new QuizDraftAnswerRecord(0, answer, index++, "LIST".equals(type) ? null : hint, List.copyOf(aliases)));
        }
        return answers;
    }

    private String normalize(String value, Set<String> accepted, String label) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!accepted.contains(normalized)) throw new IllegalArgumentException("Unsupported " + label + ".");
        return normalized;
    }

    private boolean looksLikeQuestionRequest(String prompt) {
        String normalized = prompt.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.matches(".*\\b(create|generate|make|give)\\b.*\\b(sample|random|a)? ?\\b(question|quiz|trivia)\\b.*")
                || normalized.matches(".*\\b(question|quiz|trivia)\\b.*\\b(about|on|for)\\b.*");
    }

    public record GeneratedQuestion(String category, String questionType, String prompt, String model, List<GeneratedAnswer> answers) { }
    public record GeneratedAnswer(String answer, int displayOrder, String hint, List<String> aliases) { }
    public record CreatedDraft(long draftId, String model, String prompt) { }
    public static final class QuestionDoesNotFitException extends IllegalArgumentException { public QuestionDoesNotFitException(String message) { super(message); } }
}

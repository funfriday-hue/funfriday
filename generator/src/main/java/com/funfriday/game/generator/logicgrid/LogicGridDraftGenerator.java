package com.funfriday.game.generator.logicgrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.funfriday.db.dao.LogicGridDraftDao;
import com.funfriday.game.generator.Generator;
import com.funfriday.game.generator.GeneratorSchedule;
import com.funfriday.llm.LlmJsonClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Generates a review-only Logic Grid draft every 168 hours. Publishing is always manual. */
@Slf4j
@Component
@RequiredArgsConstructor
@GeneratorSchedule(interval = "PT168H")
public class LogicGridDraftGenerator implements Generator {
  private static final List<String> TOPICS = List.of(
      "CRICKET", "BOLLYWOOD", "POKEMON", "GEOGRAPHY", "GENERAL_KNOWLEDGE",
      "HARRY_POTTER", "FLAGS", "POP_CULTURE", "TV_SERIES", "FRIENDS", "HIMYM",
      "SPORTS", "MYTHOLOGY", "HISTORY");

  private final LogicGridDraftDao drafts;
  private final LlmJsonClient llm;
  private final ObjectMapper json;
  private final Random random = new Random();

  private enum Difficulty { EASY, MEDIUM, HARD }

  @Override
  public void generate() throws Exception {
    if (!llm.isConfigured()) {
      log.warn("Logic Grid generation skipped: LLM_API_KEY is not configured.");
      return;
    }

    String category = TOPICS.get(random.nextInt(TOPICS.size()));
    Difficulty difficulty = Difficulty.values()[random.nextInt(Difficulty.values().length)];
    List<Map<String, Object>> references = drafts.randomPublishedReferences(5 + random.nextInt(4));
    String prompt = generationPrompt(category, difficulty, references);

    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        LlmJsonClient.Completion completion = llm.complete(
            "You are a careful puzzle editor. Return valid JSON only.", prompt, 0.35);
        JsonNode puzzle = completion.json();
        validate(puzzle);
        verifyIndependentlySolvable(puzzle);

        String key = "logic_" + category.toLowerCase(Locale.ROOT) + "_" + Instant.now().toEpochMilli();
        long id = drafts.create(key, category, puzzle.path("title").asText(), puzzle.path("story").asText(),
            json.writeValueAsString(puzzle.path("entities")),
            json.writeValueAsString(puzzle.path("attributes")),
            json.writeValueAsString(puzzle.path("clues")),
            json.writeValueAsString(puzzle.path("solution")), completion.model());
        log.info("Created {} Logic Grid draft {}: {} ({}) after checking {} published references.",
            difficulty, id, puzzle.path("title").asText(), category, references.size());
        return;
      } catch (Exception exception) {
        log.warn("Rejected Logic Grid generation attempt {}/3 for {} {}: {}",
            attempt, difficulty, category, exception.getMessage());
      }
    }
    log.error("Logic Grid generation produced no independently solvable {} draft after 3 attempts for {}",
        difficulty, category);
  }

  private String generationPrompt(String category, Difficulty difficulty, List<Map<String, Object>> references)
      throws Exception {
    List<Map<String, Object>> visibleReferences = new ArrayList<>();
    for (Map<String, Object> reference : references) {
      Map<String, Object> visible = new LinkedHashMap<>();
      visible.put("category", reference.get("category"));
      visible.put("title", reference.get("title"));
      visible.put("story", reference.get("story"));
      visible.put("entities", json.readTree((String) reference.get("entitiesJson")));
      visible.put("attributes", json.readTree((String) reference.get("attributesJson")));
      visible.put("clues", json.readTree((String) reference.get("cluesJson")));
      visibleReferences.add(visible);
    }
    return """
        Create one compact, solvable %s Logic Grid / Zebra puzzle about %s. Return JSON only:
        {"title":"...","story":"...","entities":["..."],"attributes":[{"key":"...","label":"...","values":["..."]}],"clues":["..."],"solution":{"Entity":{"attributeKey":"value"}}}.

        Use exactly 4 or 5 named entities and between 3 and 5 attributes. Every attribute must have exactly as many
        distinct values as entities. The answer key must assign each entity one unique value from every attribute.
        Write enough clear clues to make one final solution deducible from the clues alone, without revealing all
        assignments directly: 7–12 clues for three attributes, 9–15 for four attributes, and 11–18 for five
        attributes. Values must be short labels. Avoid ambiguous pronouns and rapidly changing facts.

        Difficulty rules: EASY normally uses 4 entities and 3 attributes with mostly direct relationships and short
        deduction chains; MEDIUM uses 4–5 entities and 3–4 attributes with several linked deductions and
        elimination; HARD uses 5 entities and 4–5 attributes with longer but still fair multi-step deductions. Do
        not require obscure external knowledge at any difficulty.

        NOVELTY RULE: the following are 5–8 randomly selected published puzzles. Do not repeat or lightly reword
        their title, story, subject matter, entity names, value sets, clue structure, or solution pattern. They are
        references to avoid; answer keys are deliberately omitted.
        Existing puzzle references:
        %s
        """.formatted(difficulty.name(), category.replace('_', ' '),
            visibleReferences.isEmpty() ? "(none yet)" : json.writeValueAsString(visibleReferences));
  }

  private void validate(JsonNode puzzle) {
    if (puzzle.path("title").asText().isBlank() || !puzzle.path("entities").isArray()
        || !puzzle.path("attributes").isArray() || !puzzle.path("clues").isArray()
        || !puzzle.path("solution").isObject()) {
      throw new IllegalArgumentException("Logic Grid response is missing required fields.");
    }
    int entityCount = puzzle.path("entities").size();
    int attributeCount = puzzle.path("attributes").size();
    if (entityCount < 4 || entityCount > 5 || attributeCount < 3 || attributeCount > 5) {
      throw new IllegalArgumentException("Logic Grid needs 4–5 entities and 3–5 attributes.");
    }
    Set<String> entities = new HashSet<>();
    puzzle.path("entities").forEach(node -> entities.add(node.asText()));
    if (entities.size() != entityCount || entities.contains("")) {
      throw new IllegalArgumentException("Logic Grid entities must be distinct.");
    }
    Set<String> keys = new HashSet<>();
    for (JsonNode attribute : puzzle.path("attributes")) {
      String key = attribute.path("key").asText();
      Set<String> values = new HashSet<>();
      attribute.path("values").forEach(value -> values.add(value.asText()));
      if (key.isBlank() || !keys.add(key) || values.size() != entityCount || values.contains("")) {
        throw new IllegalArgumentException("Every attribute needs one distinct, non-empty value per entity.");
      }
    }
    int minimumClues = switch (attributeCount) {
      case 3 -> 7;
      case 4 -> 9;
      case 5 -> 11;
      default -> throw new IllegalStateException("Validated attribute count is out of range.");
    };
    int maximumClues = switch (attributeCount) {
      case 3 -> 12;
      case 4 -> 15;
      case 5 -> 18;
      default -> throw new IllegalStateException("Validated attribute count is out of range.");
    };
    if (puzzle.path("clues").size() < minimumClues || puzzle.path("clues").size() > maximumClues) {
      throw new IllegalArgumentException("Logic Grid needs " + minimumClues + "–" + maximumClues
          + " clues for " + attributeCount + " attributes.");
    }
    for (String entity : entities) {
      for (String key : keys) {
        if (puzzle.path("solution").path(entity).path(key).asText().isBlank()) {
          throw new IllegalArgumentException("Every solution row needs every attribute.");
        }
      }
    }
    validateSolutionValues(puzzle, entities, keys);
  }

  /** Uses an answer-key-blind second LLM pass to cross-check that visible clues solve to the supplied key. */
  private void verifyIndependentlySolvable(JsonNode puzzle) throws Exception {
    Map<String, Object> visible = new LinkedHashMap<>();
    visible.put("title", puzzle.path("title").asText());
    visible.put("story", puzzle.path("story").asText());
    visible.put("entities", puzzle.path("entities"));
    visible.put("attributes", puzzle.path("attributes"));
    visible.put("clues", puzzle.path("clues"));
    String prompt = """
        Solve this logic-grid puzzle using only its visible story, entities, attribute values, and clues.
        Do not use external knowledge. Return JSON only: {"solution":{"Entity":{"attributeKey":"value"}}}.
        Every value for each attribute must be used once. If clues are inconsistent, incomplete, or allow more than
        one solution, return {"solvable":false}.
        Puzzle:
        %s
        """.formatted(json.writeValueAsString(visible));
    JsonNode solved = llm.complete("You are an independent logic-puzzle solver. Return valid JSON only.", prompt, 0.0).json();
    if (!solved.path("solution").isObject()) {
      throw new IllegalArgumentException("Independent solver could not derive one complete solution.");
    }
    Set<String> entities = new HashSet<>();
    puzzle.path("entities").forEach(node -> entities.add(node.asText()));
    Set<String> keys = new HashSet<>();
    puzzle.path("attributes").forEach(attribute -> keys.add(attribute.path("key").asText()));
    ObjectNode solverPuzzle = puzzle.deepCopy();
    solverPuzzle.set("solution", solved.path("solution"));
    validateSolutionValues(solverPuzzle, entities, keys);
    for (String entity : entities) {
      for (String key : keys) {
        if (!puzzle.path("solution").path(entity).path(key).asText()
            .equals(solved.path("solution").path(entity).path(key).asText())) {
          throw new IllegalArgumentException("Independent solver produced a different solution.");
        }
      }
    }
  }

  private void validateSolutionValues(JsonNode puzzle, Set<String> entities, Set<String> keys) {
    Map<String, Set<String>> allowed = new HashMap<>();
    puzzle.path("attributes").forEach(attribute -> {
      Set<String> values = new HashSet<>();
      attribute.path("values").forEach(value -> values.add(value.asText()));
      allowed.put(attribute.path("key").asText(), values);
    });
    for (String key : keys) {
      Set<String> used = new HashSet<>();
      for (String entity : entities) {
        String value = puzzle.path("solution").path(entity).path(key).asText();
        if (!allowed.getOrDefault(key, Set.of()).contains(value) || !used.add(value)) {
          throw new IllegalArgumentException("Solution must use each allowed " + key + " value exactly once.");
        }
      }
    }
  }
}

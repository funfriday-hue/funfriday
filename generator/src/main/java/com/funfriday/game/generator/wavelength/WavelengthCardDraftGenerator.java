package com.funfriday.game.generator.wavelength;

import com.fasterxml.jackson.databind.JsonNode;
import com.funfriday.db.dao.WavelengthCardDraftDao;
import com.funfriday.game.generator.Generator;
import com.funfriday.game.generator.GeneratorSchedule;
import com.funfriday.llm.LlmJsonClient;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Generates review-only Telepathy cards once weekly; a human explicitly publishes them. */
@Slf4j
@Component
@RequiredArgsConstructor
@GeneratorSchedule(interval = "PT168H")
public class WavelengthCardDraftGenerator implements Generator {
  private static final List<String> CATEGORIES = List.of("Everyday", "Food", "Culture", "Sport", "Travel", "Work", "Movies", "Relationships");
  private final WavelengthCardDraftDao drafts;
  private final LlmJsonClient llm;
  private final Random random = new Random();

  @Override public void generate() throws Exception {
    if (!llm.isConfigured()) { log.warn("Telepathy generation skipped: LLM_API_KEY is not configured."); return; }
    String category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
    List<Map<String,Object>> references = drafts.randomPublishedReferences(8);
    for (int attempt = 1; attempt <= 3; attempt++) try {
      LlmJsonClient.Completion completion = llm.complete("You are an inventive, family-friendly party-game editor. Return JSON only.", prompt(category, references), 0.7);
      JsonNode card = completion.json();
      String left = card.path("leftLabel").asText().trim();
      String right = card.path("rightLabel").asText().trim();
      int target = card.path("target").asInt(999);
      if (left.isBlank() || right.isBlank() || left.length() > 80 || right.length() > 80 || left.equalsIgnoreCase(right) || target < -100 || target > 100) throw new IllegalArgumentException("Invalid card format.");
      long id = drafts.create("wavelength_" + category.toLowerCase(Locale.ROOT).replace(' ', '_') + "_" + Instant.now().toEpochMilli(), category, left, right, target, completion.model());
      log.info("Created Telepathy draft {}: {} ↔ {}", id, left, right); return;
    } catch (Exception e) { log.warn("Rejected Telepathy generation {}/3: {}", attempt, e.getMessage()); }
    log.error("Telepathy generator produced no usable draft after 3 attempts.");
  }

  private String prompt(String category, List<Map<String,Object>> references) {
    return """
        Create exactly one fresh, family-friendly Telepathy spectrum card in the category %s. Return JSON only:
        {"leftLabel":"short endpoint","rightLabel":"short endpoint","target":integer}.
        A team gives one clue to help another team locate the secret target on the -100 (left) to 100 (right) scale.
        Endpoints must be natural opposites or a clear subjective continuum, under 80 characters, playable without obscure knowledge,
        and neither endpoint may reveal the target. Choose a target that is not 0 and avoids the extreme 10 points at either edge.
        Do not repeat, lightly reword, or reuse the same semantic spectrum as these existing cards:
        %s
        """.formatted(category, references.isEmpty() ? "(none)" : references);
  }
}

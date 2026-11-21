package com.funfriday.game.generator.crossword;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funfriday.db.dao.CrosswordDraftDao;
import com.funfriday.game.generator.Generator;
import com.funfriday.game.generator.GeneratorSchedule;
import com.funfriday.llm.LlmJsonClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Creates review-only compact crossword drafts; an admin explicitly publishes each one. */
@Slf4j
@Component
@RequiredArgsConstructor
@GeneratorSchedule(interval = "PT24H")
public class CrosswordDraftGenerator implements Generator {

  private static final int MAX_LAYOUT_ATTEMPTS = 3;

  private record PlacedEntry(int row, int column, String direction, String answer) {}

  private static final List<String> TOPICS =
      List.of(
          "CRICKET",
          "BOLLYWOOD",
          "POKEMON",
          "GEOGRAPHY",
          "GENERAL_KNOWLEDGE",
          "HARRY_POTTER",
          "FLAGS",
          "POP_CULTURE",
          "TV_SERIES",
          "FRIENDS",
          "HIMYM",
          "SPORTS",
          "MYTHOLOGY",
          "HISTORY",
          "MARVEL",
          "DC");


  private final CrosswordDraftDao drafts;
  private final LlmJsonClient llm;
  private final ObjectMapper json = new ObjectMapper();
  private final Random random = new Random();

  @Override
  public void generate() throws Exception {
    if (!llm.isConfigured()) {
      log.warn("Crossword generation skipped: LLM_API_KEY is not configured.");
      return;
    }

    String topic = TOPICS.get(random.nextInt(TOPICS.size()));
    String topicLabel = topic.replace('_', ' ');
    List<String> existingClues = drafts.randomPublishedClueReferences(topic, 10);
    String existingClueContext =
        existingClues.isEmpty()
            ? "No published crossword clues exist for this topic yet."
            : String.join("\n", existingClues);
    String prompt =
        """
        Create one compact %s themed crossword draft with 10 to 15 factual answers. Return JSON only:
        {"title":"...","width":number,"height":number,"grid":[[true]],"entries":[{"number":1,"direction":"ACROSS|DOWN","row":0,"column":0,"answer":"UPPERCASELETTERS","clue":"short factual clue"}]}.
        Build a genuine interlocked crossword, not a comb or spoke layout. Balance ACROSS and DOWN clues so their
        counts differ by no more than two. Every answer must cross another answer; most answers should cross two or
        more. Do not make one long answer the spine for nearly every other clue, and do not stack or merely touch
        answers without a proper crossing. Each crossing must be on an internal letter of both answers: never cross
        on the first or last letter of either answer. Grid true cells must exactly cover the answer letters. Starts must be
        distributed inside the grid: do not put every ACROSS answer on row 0 or every DOWN answer in column 0.
        Each answer must have a black cell or grid edge immediately before and after it in its own direction. Every
        answer must fit its start coordinate and direction, crossings must use the same letter, no spaces/punctuation
        in answers, and the grid must be 9–15 cells in each direction. Use one clear topic and validate every fact
        before responding.

        Here are up to 10 randomly selected clues already published for this topic:
        %s

        Do not reuse any listed answer. Do not write a clue about the same fact, character, event, or relationship
        as a listed clue. Choose meaningfully different facts and answers for this new crossword.
        """
            .formatted(topicLabel, existingClueContext);

    LlmJsonClient.Completion completion = null;
    JsonNode crossword = null;
    IllegalArgumentException layoutError = null;
    for (int attempt = 1; attempt <= MAX_LAYOUT_ATTEMPTS; attempt++) {
      completion =
          llm.complete(
              "You are a meticulous crossword editor. Return valid JSON only.", prompt, 0.2);
      crossword = completion.json();
      try {
        validate(crossword);
        break;
      } catch (IllegalArgumentException exception) {
        layoutError = exception;
        log.warn(
            "Rejected generated crossword layout for topic {} on attempt {}/{}: {}",
            topic,
            attempt,
            MAX_LAYOUT_ATTEMPTS,
            exception.getMessage());
        crossword = null;
      }
    }
    if (crossword == null || completion == null) {
      throw new IllegalArgumentException(
          "Unable to generate a valid interlocked crossword after "
              + MAX_LAYOUT_ATTEMPTS
              + " attempts.",
          layoutError);
    }

    String key =
        "llm_crossword_" + topic.toLowerCase(Locale.ROOT) + "_" + Instant.now().toEpochMilli();
    long draftId =
        drafts.create(
            key,
            topic,
            crossword.path("title").asText(),
            crossword.path("width").asInt(),
            crossword.path("height").asInt(),
            json.writeValueAsString(crossword.path("grid")),
            json.writeValueAsString(crossword.path("entries")),
            completion.model());
    log.info(
        "Created crossword draft {} for topic {} with {} clues using {}; awaiting /admin/crossword"
            + " review.",
        draftId,
        topic,
        crossword.path("entries").size(),
        completion.model());
  }

  private void validate(JsonNode crossword) {
    int width = crossword.path("width").asInt();
    int height = crossword.path("height").asInt();
    int clueCount = crossword.path("entries").size();
    if (width < 9
        || width > 15
        || height < 9
        || height > 15
        || clueCount < 10
        || clueCount > 15
        || !crossword.path("grid").isArray()
        || !crossword.path("entries").isArray()) {
      throw new IllegalArgumentException(
          "Generated crossword must include a 9–15 grid and 10–15 entries.");
    }

    int across = 0;
    int down = 0;
    boolean hasInteriorAcrossStart = false;
    boolean hasInteriorDownStart = false;
    boolean[][] coveredCells = new boolean[height][width];
    String[][] letters = new String[height][width];
    List<PlacedEntry> entries = new ArrayList<>();
    Map<String, List<Integer>> cellOwners = new HashMap<>();
    for (JsonNode entry : crossword.path("entries")) {
      boolean answerIsValid = entry.path("answer").asText().matches("[A-Z]{2,80}");
      String direction = entry.path("direction").asText();
      boolean directionIsValid = "ACROSS".equals(direction) || "DOWN".equals(direction);
      if (!answerIsValid || !directionIsValid) {
        throw new IllegalArgumentException("Generated crossword contains an invalid entry.");
      }
      String answer = entry.path("answer").asText();
      int row = entry.path("row").asInt(-1);
      int column = entry.path("column").asInt(-1);
      int lastRow = row + ("DOWN".equals(direction) ? answer.length() - 1 : 0);
      int lastColumn = column + ("ACROSS".equals(direction) ? answer.length() - 1 : 0);
      if (row < 0 || column < 0 || lastRow >= height || lastColumn >= width) {
        throw new IllegalArgumentException(
            "Generated crossword entry does not fit inside the grid.");
      }
      if ("ACROSS".equals(direction)) {
        across++;
        hasInteriorAcrossStart |= row > 0;
      } else {
        down++;
        hasInteriorDownStart |= column > 0;
      }
      int entryIndex = entries.size();
      entries.add(new PlacedEntry(row, column, direction, answer));
      for (int index = 0; index < answer.length(); index++) {
        int cellRow = row + ("DOWN".equals(direction) ? index : 0);
        int cellColumn = column + ("ACROSS".equals(direction) ? index : 0);
        String letter = String.valueOf(answer.charAt(index));
        if (letters[cellRow][cellColumn] != null && !letters[cellRow][cellColumn].equals(letter)) {
          throw new IllegalArgumentException(
              "Generated crossword has conflicting crossing letters.");
        }
        letters[cellRow][cellColumn] = letter;
        coveredCells[cellRow][cellColumn] = true;
        cellOwners
            .computeIfAbsent(cellRow + ":" + cellColumn, ignored -> new ArrayList<>())
            .add(entryIndex);
      }
    }
    if (across < 3
        || down < 3
        || Math.abs(across - down) > 2
        || !hasInteriorAcrossStart
        || !hasInteriorDownStart) {
      throw new IllegalArgumentException(
          "Generated crossword must contain balanced, distributed Across and Down entries.");
    }

    Set<String> crossingPairs = new HashSet<>();
    for (Map.Entry<String, List<Integer>> cell : cellOwners.entrySet()) {
      List<Integer> owners = cell.getValue();
      if (owners.size() > 2) {
        throw new IllegalArgumentException("Generated crossword has more than two answers in one cell.");
      }
      if (owners.size() == 2) {
        int first = owners.get(0);
        int second = owners.get(1);
        if (entries.get(first).direction().equals(entries.get(second).direction())) {
          throw new IllegalArgumentException("Generated crossword has overlapping parallel answers.");
        }
        String[] coordinates = cell.getKey().split(":");
        int row = Integer.parseInt(coordinates[0]);
        int column = Integer.parseInt(coordinates[1]);
        if (!isInteriorLetter(entries.get(first), row, column)
            || !isInteriorLetter(entries.get(second), row, column)) {
          throw new IllegalArgumentException(
              "Generated crossword has an endpoint crossing that makes adjacent answers hard to read.");
        }
        crossingPairs.add(Math.min(first, second) + ":" + Math.max(first, second));
      }
    }
    if (crossingPairs.size() < clueCount) {
      throw new IllegalArgumentException(
          "Generated crossword does not have enough crossings for a compact layout.");
    }
    int[] crossingsPerEntry = new int[entries.size()];
    for (String pair : crossingPairs) {
      String[] indexes = pair.split(":");
      crossingsPerEntry[Integer.parseInt(indexes[0])]++;
      crossingsPerEntry[Integer.parseInt(indexes[1])]++;
    }
    int wellConnected = 0;
    int maximumCrossings = Math.max(4, clueCount / 3);
    for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
      PlacedEntry entry = entries.get(entryIndex);
      if (crossingsPerEntry[entryIndex] == 0) {
        throw new IllegalArgumentException("Every crossword answer must cross another answer.");
      }
      if (crossingsPerEntry[entryIndex] >= 2) {
        wellConnected++;
      }
      if (crossingsPerEntry[entryIndex] > maximumCrossings) {
        throw new IllegalArgumentException("Generated crossword has a comb-shaped clue layout.");
      }
      int beforeRow = entry.row() - ("DOWN".equals(entry.direction()) ? 1 : 0);
      int beforeColumn = entry.column() - ("ACROSS".equals(entry.direction()) ? 1 : 0);
      int afterRow =
          entry.row() + ("DOWN".equals(entry.direction()) ? entry.answer().length() : 0);
      int afterColumn =
          entry.column() + ("ACROSS".equals(entry.direction()) ? entry.answer().length() : 0);
      if (isCovered(coveredCells, beforeRow, beforeColumn)
          || isCovered(coveredCells, afterRow, afterColumn)) {
        throw new IllegalArgumentException("Generated crossword has invalid answer boundaries.");
      }
    }
    if (wellConnected < Math.ceil(clueCount * 0.6)) {
      throw new IllegalArgumentException("Generated crossword is not interlocked enough.");
    }
    validateNoAccidentalTouches(coveredCells, cellOwners, entries);
    validateCompactShape(coveredCells, clueCount);
    for (int row = 0; row < height; row++) {
      JsonNode gridRow = crossword.path("grid").path(row);
      if (!gridRow.isArray() || gridRow.size() != width) {
        throw new IllegalArgumentException("Generated crossword grid has invalid dimensions.");
      }
      for (int column = 0; column < width; column++) {
        if (gridRow.path(column).asBoolean() != coveredCells[row][column]) {
          throw new IllegalArgumentException(
              "Generated crossword grid must exactly match its answer entries.");
        }
      }
    }
  }

  private boolean isCovered(boolean[][] coveredCells, int row, int column) {
    return row >= 0
        && row < coveredCells.length
        && column >= 0
        && column < coveredCells[row].length
        && coveredCells[row][column];
  }

  private boolean isInteriorLetter(PlacedEntry entry, int row, int column) {
    int index =
        "ACROSS".equals(entry.direction()) ? column - entry.column() : row - entry.row();
    return index > 0 && index < entry.answer().length() - 1;
  }

  private void validateNoAccidentalTouches(
      boolean[][] coveredCells, Map<String, List<Integer>> cellOwners, List<PlacedEntry> entries) {
    for (int row = 0; row < coveredCells.length; row++) {
      for (int column = 0; column < coveredCells[row].length; column++) {
        if (!coveredCells[row][column]) {
          continue;
        }
        if (isCovered(coveredCells, row, column + 1)
            && !shareDirection(cellOwners, entries, row, column, row, column + 1, "ACROSS")) {
          throw new IllegalArgumentException(
              "Generated crossword has adjacent answers touching horizontally without crossing.");
        }
        if (isCovered(coveredCells, row + 1, column)
            && !shareDirection(cellOwners, entries, row, column, row + 1, column, "DOWN")) {
          throw new IllegalArgumentException(
              "Generated crossword has adjacent answers touching vertically without crossing.");
        }
      }
    }
  }

  private boolean shareDirection(
      Map<String, List<Integer>> cellOwners,
      List<PlacedEntry> entries,
      int firstRow,
      int firstColumn,
      int secondRow,
      int secondColumn,
      String direction) {
    List<Integer> firstOwners = cellOwners.get(firstRow + ":" + firstColumn);
    List<Integer> secondOwners = cellOwners.get(secondRow + ":" + secondColumn);
    return firstOwners.stream()
        .filter(index -> direction.equals(entries.get(index).direction()))
        .anyMatch(secondOwners::contains);
  }

  private void validateCompactShape(boolean[][] coveredCells, int clueCount) {
    int minRow = coveredCells.length;
    int maxRow = -1;
    int minColumn = coveredCells[0].length;
    int maxColumn = -1;
    int coveredCount = 0;
    for (int row = 0; row < coveredCells.length; row++) {
      for (int column = 0; column < coveredCells[row].length; column++) {
        if (!coveredCells[row][column]) {
          continue;
        }
        coveredCount++;
        minRow = Math.min(minRow, row);
        maxRow = Math.max(maxRow, row);
        minColumn = Math.min(minColumn, column);
        maxColumn = Math.max(maxColumn, column);
      }
    }
    int boundingArea = (maxRow - minRow + 1) * (maxColumn - minColumn + 1);
    double density = (double) coveredCount / boundingArea;
    if (density < 0.30 || boundingArea > clueCount * 13) {
      throw new IllegalArgumentException("Generated crossword is too sparse to be a compact grid.");
    }
  }
}

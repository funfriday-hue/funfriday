package com.funfriday.games.crossword;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funfriday.db.dao.CrosswordDao;
import com.funfriday.db.model.CrosswordEntry;
import com.funfriday.db.model.CrosswordPuzzle;
import com.funfriday.model.*;
import com.funfriday.service.GameLogic;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrosswordGame implements GameLogic {
  private final CrosswordDao crosswordDao;
  private final ObjectMapper objectMapper;

  @Override
  public GameData<?> initializeData(GameConfiguration raw) {
    CrosswordConfiguration c = (CrosswordConfiguration) raw;
    try {
      CrosswordPuzzle p =
          (c.getPuzzleId() == null
                  ? crosswordDao.selectRandomActive()
                  : crosswordDao.selectActiveById(c.getPuzzleId()))
              .orElseThrow(
                  () -> new IllegalStateException("No published crossword puzzle is available."));
      boolean[][] playable = objectMapper.readValue(p.gridJson(), new TypeReference<>() {});
      String[][] solution = new String[p.height()][p.width()];
      for (CrosswordEntry entry : p.entries())
        for (int i = 0; i < entry.answer().length(); i++) {
          int row = entry.row() + ("DOWN".equals(entry.direction()) ? i : 0),
              col = entry.column() + ("ACROSS".equals(entry.direction()) ? i : 0);
          if (row >= 0 && row < p.height() && col >= 0 && col < p.width())
            solution[row][col] = String.valueOf(Character.toUpperCase(entry.answer().charAt(i)));
        }
      CrosswordData data =
          new CrosswordData(
              p.id(),
              p.puzzleKey(),
              p.category(),
              p.title(),
              p.width(),
              p.height(),
              playable,
              solution,
              p.entries());
      // Keep crossword consistent with the other timed mind games: players see
      // the board first, then everyone starts together after five seconds.
      data.setPlayStartsAtMillis(System.currentTimeMillis() + 5000);
      return data;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to load crossword puzzle", e);
    }
  }

  @Override
  public void processMove(GameAction action, GameData<?> raw) {
    if (!"CROSSWORD_SYNC".equals(action.getType()) && !"CROSSWORD_GIVE_UP".equals(action.getType()))
      return;
    CrosswordData data = (CrosswordData) raw;
    // The browser also disables the board during the countdown, but the game
    // server remains authoritative when a client sends a move early.
    if (System.currentTimeMillis() < data.getPlayStartsAtMillis()) return;
    CrosswordPlayerStats stats =
        (CrosswordPlayerStats) data.getScoreBoard().get(action.getPlayerId());
    if ("CROSSWORD_GIVE_UP".equals(action.getType())) {
      if (stats != null) stats.setStatus(PlayerStatus.GIVEN_UP);
      return;
    }
    String[][] incoming = ((CrosswordAction) action).getBoard();
    if (incoming == null) return;
    String[][] clean = new String[data.getHeight()][data.getWidth()];
    for (int r = 0; r < data.getHeight(); r++)
      for (int c = 0; c < data.getWidth(); c++)
        if (data.getPlayableCells()[r][c]
            && r < incoming.length
            && incoming[r] != null
            && c < incoming[r].length
            && incoming[r][c] != null) {
          String letter = incoming[r][c].trim().toUpperCase(Locale.ROOT);
          if (letter.matches("[A-Z]")) clean[r][c] = letter;
        }
    data.getPlayerBoards().put(action.getPlayerId(), clean);
  }

  @Override
  public void updateStats(PlayerStats raw, GameData<?> generic) {
    CrosswordPlayerStats stats = (CrosswordPlayerStats) raw;
    CrosswordData data = (CrosswordData) generic;
    String[][] board = data.getPlayerBoards().get(stats.getPlayerId());
    if (board == null) return;
    int total = 0, correct = 0;
    boolean complete = true;
    for (int r = 0; r < data.getHeight(); r++)
      for (int c = 0; c < data.getWidth(); c++)
        if (data.getPlayableCells()[r][c]) {
          total++;
          if (Objects.equals(data.getSolution()[r][c], board[r][c])) correct++;
          else complete = false;
        }
    int cluesSolved = 0;
    for (CrosswordEntry entry : data.getEntries()) {
      boolean clueSolved = true;
      for (int index = 0; index < entry.answer().length(); index++) {
        int row = entry.row() + ("DOWN".equals(entry.direction()) ? index : 0);
        int column = entry.column() + ("ACROSS".equals(entry.direction()) ? index : 0);
        if (!Objects.equals(data.getSolution()[row][column], board[row][column])) {
          clueSolved = false;
          break;
        }
      }
      if (clueSolved) cluesSolved++;
    }
    double pct = total == 0 ? 0 : (correct * 100.0 / total);
    stats.setProgress(pct);
    stats.setCluesSolved(cluesSolved);
    stats.setScore(cluesSolved);
    long elapsed = Math.max(0, System.currentTimeMillis() - data.getPlayStartsAtMillis());
    stats.setTotalTimeMillis(elapsed);
    if (complete) stats.setStatus(PlayerStatus.COMPLETED);
  }

  @Override
  public boolean isGameOver(GameData<?> data) {
    return data.getScoreBoard().values().stream()
        .allMatch(
            s -> s.getStatus() == PlayerStatus.COMPLETED || s.getStatus() == PlayerStatus.GIVEN_UP);
  }

  @Override
  public PlayerStats createInitialStats(GamePlayer p, boolean host) {
    return new CrosswordPlayerStats(p, host);
  }

  @Override
  public GameConfiguration parseConfiguration(Map<String, Object> props) {
    Object id = props.get("puzzleId");
    Long puzzleId =
        id instanceof Number n
            ? n.longValue()
            : id instanceof String s && s.matches("\\d+") ? Long.parseLong(s) : null;
    return new CrosswordConfiguration(puzzleId);
  }
}

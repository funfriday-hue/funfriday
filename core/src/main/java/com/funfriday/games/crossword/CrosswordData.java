package com.funfriday.games.crossword;

import com.funfriday.db.model.CrosswordEntry;
import com.funfriday.model.GameData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrosswordData extends GameData<CrosswordConfiguration> {
  private long puzzleId;
  private String puzzleKey;
  private String category;
  private String title;
  private int width;
  private int height;
  private boolean[][] playableCells;
  private String[][] solution;
  private List<CrosswordEntry> entries;
  private Map<String, String[][]> playerBoards = new ConcurrentHashMap<>();

  public CrosswordData(
      long puzzleId,
      String key,
      String category,
      String title,
      int width,
      int height,
      boolean[][] playable,
      String[][] solution,
      List<CrosswordEntry> entries) {
    this.puzzleId = puzzleId;
    this.puzzleKey = key;
    this.category = category;
    this.title = title;
    this.width = width;
    this.height = height;
    this.playableCells = playable;
    this.solution = solution;
    this.entries = entries;
  }

  public void initializePlayer(String playerId) {
    playerBoards.put(playerId, new String[height][width]);
  }
}

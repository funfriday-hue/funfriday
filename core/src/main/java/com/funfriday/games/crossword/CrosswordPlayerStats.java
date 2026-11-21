package com.funfriday.games.crossword;

import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrosswordPlayerStats extends PlayerStats {
  private double progress;
  private int cluesSolved;
  private long totalTimeMillis;

  public CrosswordPlayerStats(GamePlayer player, boolean host) {
    super(player, host);
  }

  @Override
  public double getRankingMetric() {
    return cluesSolved;
  }
}

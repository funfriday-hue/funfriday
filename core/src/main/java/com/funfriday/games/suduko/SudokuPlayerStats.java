package com.funfriday.games.suduko;

import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SudokuPlayerStats extends PlayerStats {
    private int rowsSolved;    // 0 to 9
    private int columnsSolved; // 0 to 9
    private int boxesSolved;   // 0 to 9
    private double progress;   // (totalSolved / 27.0) * 100

    private long totalTimeMillis = 0;

    public SudokuPlayerStats(GamePlayer player, boolean isHost) {
        super(player, isHost);
        this.rowsSolved = 0;
        this.columnsSolved = 0;
        this.boxesSolved = 0;
    }

    @Override
    public double getRankingMetric() {
        return this.progress;
    }

}
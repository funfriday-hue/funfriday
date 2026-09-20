package com.funfriday.games.wordle;

import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WordlePlayerStats extends PlayerStats {
    // Tracks total words completed in the rush (0 to 10)
    private int wordsCleared;

    // Tracks attempts for the CURRENT word only
    private int currentWordAttempts;

    // Final time taken (optional, for tie-breaking)
    private long timeTakenMillis;

    private int totalAttempts; // <--- The tie-breaker field

    public WordlePlayerStats(GamePlayer player, boolean isHost) {
        super(player, isHost);
        this.wordsCleared = 0;
        this.currentWordAttempts = 0;
        this.totalAttempts = 0;
    }

    @Override
    public double getRankingMetric() {
        return (double) this.totalAttempts;
    }
}
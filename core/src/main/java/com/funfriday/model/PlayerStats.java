package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.funfriday.games.suduko.SudokuPlayerStats;
import com.funfriday.games.wordle.WordlePlayerStats;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
// These Jackson annotations allow the Frontend to know which subclass it's receiving
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = WordlePlayerStats.class, name = "WORDLE"),
        @JsonSubTypes.Type(value = SudokuPlayerStats.class, name = "SUDOKU")
})
public abstract class PlayerStats {
    private GamePlayer player;
    private long timeInSeconds;
    private int score;
    private PlayerStatus status;
    private int sessionsCompleted;

    public PlayerStats(GamePlayer player, boolean isHost) {
        this.player = player;
        this.score = 0;
        this.status = PlayerStatus.ACTIVE;
    }

    public String getPlayerId() {
        return this.player.getId();
    }

    public String getPlayerName() {
        return this.player != null ? this.player.getName() : "Unknown";
    }

    /**
     * Every game must provide a numeric value for ranking.
     * Wordle returns attempts, Quiz returns points.
     */
    public abstract double getRankingMetric();

}
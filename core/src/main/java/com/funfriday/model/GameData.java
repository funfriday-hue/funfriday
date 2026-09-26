package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.funfriday.games.dobble.DobbleData;
import com.funfriday.games.quizroyale.QuizRoyaleData;
import com.funfriday.games.suduko.SudokuData;
import com.funfriday.games.wordle.WordleData;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(value = {
        @JsonSubTypes.Type(value = WordleData.class, name = "WORDLE"),
        @JsonSubTypes.Type(value = SudokuData.class, name = "SUDOKU"),
        @JsonSubTypes.Type(value = DobbleData.class, name = "DOBBLE"),
        @JsonSubTypes.Type(value = QuizRoyaleData.class, name = "QUIZ_ROYALE")
})
public abstract class GameData<T extends GameConfiguration> {
    // The central map: Player Name -> Their Stats
    private Map<String, PlayerStats> scoreBoard = new ConcurrentHashMap<>();
    private T gameConfiguration;

    private volatile boolean finished = false;
    private GamePlayer winner;
    private volatile long startTime;
    /** Server timestamp at which player moves are allowed; supports synchronized pre-game countdowns. */
    private volatile long playStartsAtMillis;
    private volatile long endTimeMillis; // server authoritative end time in epoch millis

    public GameData() {
        this.startTime = System.currentTimeMillis();
        this.playStartsAtMillis = this.startTime;
    }

    // convenience helper
    public long getRemainingSeconds() {
        long remaining = getEndTimeMillis() - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);

    }

}

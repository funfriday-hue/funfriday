package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
        @JsonSubTypes.Type(value = SudokuData.class, name = "SUDOKU")
})
public abstract class GameData<T extends GameConfiguration> {
    // The central map: Player Name -> Their Stats
    private Map<String, PlayerStats> scoreBoard = new ConcurrentHashMap<>();
    private T gameConfiguration;

    private volatile boolean finished = false;
    private GamePlayer winner;
    private volatile long startTime;
    private volatile long endTimeMillis; // server authoritative end time in epoch millis

    public GameData() {
        this.startTime = System.currentTimeMillis();
    }

    // convenience helper
    public long getRemainingSeconds() {
        long remaining = getEndTimeMillis() - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);

    }

}
package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.funfriday.games.suduko.SudokuData;
import com.funfriday.games.wordle.WordleData;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(value = {
        @JsonSubTypes.Type(value = WordleData.class, name = "WORDLE"),
        @JsonSubTypes.Type(value = SudokuData.class, name = "SUDOKU")
})
public abstract class GameData {
    // The central map: Player Name -> Their Stats
    private Map<String, PlayerStats> scoreBoard = new HashMap<>();

    private boolean finished = false;
    private GamePlayer winner;
    private long startTime;

    public GameData() {
        this.startTime = System.currentTimeMillis();
    }
}
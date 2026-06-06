package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.funfriday.games.suduko.SudokuAction;
import com.funfriday.games.wordle.WordleAction;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WordleAction.class, name = "WORDLE_GUESS"),
        @JsonSubTypes.Type(value = SudokuAction.class, name = "SUDOKU_SYNC"),
        @JsonSubTypes.Type(value = SudokuAction.class, name = "SUDOKU_RESET"),
        @JsonSubTypes.Type(value = SudokuAction.class, name = "SUDOKU_GIVE_UP")
})
public abstract class GameAction {
    private String type;
    private String playerId;
    private long clientTimestamp = System.currentTimeMillis(); // Client submission time
}
package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.funfriday.games.dobble.DobbleAction;
import com.funfriday.games.quizroyale.QuizRoyaleAction;
import com.funfriday.games.suduko.SudokuAction;
import com.funfriday.games.wordle.WordleAction;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WordleAction.class, name = "WORDLE_GUESS"),
        @JsonSubTypes.Type(value = SudokuAction.class, name = "SUDOKU_SYNC"),
        @JsonSubTypes.Type(value = SudokuAction.class, name = "SUDOKU_RESET"),
        @JsonSubTypes.Type(value = SudokuAction.class, name = "SUDOKU_GIVE_UP"),
        @JsonSubTypes.Type(value = DobbleAction.class, name = "DOBBLE_MATCH"),
        @JsonSubTypes.Type(value = DobbleAction.class, name = "DOBBLE_NEXT_CARD")
        ,@JsonSubTypes.Type(value = QuizRoyaleAction.class, name = "QUIZ_ANSWER")
        ,@JsonSubTypes.Type(value = QuizRoyaleAction.class, name = "QUIZ_TIMEOUT")
        ,@JsonSubTypes.Type(value = QuizRoyaleAction.class, name = "QUIZ_PASS")
        ,@JsonSubTypes.Type(value = QuizRoyaleAction.class, name = "QUIZ_START_QUESTION")
})
public abstract class GameAction {
    private String type;
    private String playerId;
    private long clientTimestamp = System.currentTimeMillis(); // Client submission time
}

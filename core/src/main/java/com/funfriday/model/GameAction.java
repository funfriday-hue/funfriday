package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.funfriday.games.dobble.DobbleAction;
import com.funfriday.games.quizroyale.QuizRoyaleAction;
import com.funfriday.games.suduko.SudokuAction;
import com.funfriday.games.wordle.WordleAction;
import com.funfriday.games.crossword.CrosswordAction;
import com.funfriday.games.logicgrid.LogicGridAction;
import com.funfriday.games.wavelength.WavelengthAction;
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
        ,@JsonSubTypes.Type(value = CrosswordAction.class, name = "CROSSWORD_SYNC")
        ,@JsonSubTypes.Type(value = CrosswordAction.class, name = "CROSSWORD_GIVE_UP")
        ,@JsonSubTypes.Type(value = LogicGridAction.class, name = "LOGIC_GRID_SYNC")
        ,@JsonSubTypes.Type(value = LogicGridAction.class, name = "LOGIC_GRID_GIVE_UP")
        ,@JsonSubTypes.Type(value = WavelengthAction.class, name = "WAVELENGTH_SUBMIT_HINT")
        ,@JsonSubTypes.Type(value = WavelengthAction.class, name = "WAVELENGTH_SET_DIAL")
        ,@JsonSubTypes.Type(value = WavelengthAction.class, name = "WAVELENGTH_SUBMIT_GUESS")
        ,@JsonSubTypes.Type(value = WavelengthAction.class, name = "WAVELENGTH_PHASE_TIMEOUT")
        ,@JsonSubTypes.Type(value = WavelengthAction.class, name = "WAVELENGTH_START_NEXT_ROUND")
})
public abstract class GameAction {
    private String type;
    private String playerId;
    private long clientTimestamp = System.currentTimeMillis(); // Client submission time
}

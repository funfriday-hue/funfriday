package com.funfriday.factory;

import com.funfriday.games.suduko.SudokuGameHandler;
import com.funfriday.games.wordle.WordleGame;
import com.funfriday.service.GameLogic;
import org.springframework.stereotype.Component;

@Component
public class GameFactory {

    private final WordleGame wordleGame;
    private final SudokuGameHandler sudokuGameHandler;

    public GameFactory(WordleGame wordleGame, SudokuGameHandler sudokuGameHandler) {
        this.wordleGame = wordleGame;
        this.sudokuGameHandler = sudokuGameHandler;
    }

    public enum GameType {
        WORDLE, SUDOKU
    }

    public GameLogic createGame(GameType type) {
        return switch (type) {
            case WORDLE -> wordleGame;
            case SUDOKU -> sudokuGameHandler;
            default -> throw new IllegalArgumentException("Unknown game type: " + type);
        };
    }
}

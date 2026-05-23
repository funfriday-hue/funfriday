package com.funfriday.factory;

import com.funfriday.games.suduko.SudokuGameHandler;
import com.funfriday.games.wordle.WordleGame;
import com.funfriday.service.GameLogic;

public class GameFactory {

    public enum GameType {
        WORDLE, SUDOKU
    }

    public static GameLogic createGame(GameType type) {
        switch (type) {
            case WORDLE:
                return new WordleGame(); // Your Wordle implementation
            case SUDOKU:
                return new SudokuGameHandler();
            default:
                throw new IllegalArgumentException("Unknown game type: " + type);
        }
    }
}

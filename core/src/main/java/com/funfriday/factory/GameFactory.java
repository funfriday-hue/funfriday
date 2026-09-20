package com.funfriday.factory;

import com.funfriday.games.dobble.DobbleGame;
import com.funfriday.games.quizroyale.QuizRoyaleGame;
import com.funfriday.games.suduko.SudokuGameHandler;
import com.funfriday.games.wordle.WordleGame;
import com.funfriday.service.GameLogic;
import org.springframework.stereotype.Component;

@Component
public class GameFactory {

    private final WordleGame wordleGame;
    private final SudokuGameHandler sudokuGameHandler;
    private final DobbleGame dobbleGame;
    private final QuizRoyaleGame quizRoyaleGame;

    public GameFactory(WordleGame wordleGame, SudokuGameHandler sudokuGameHandler, DobbleGame dobbleGame, QuizRoyaleGame quizRoyaleGame) {
        this.wordleGame = wordleGame;
        this.sudokuGameHandler = sudokuGameHandler;
        this.dobbleGame = dobbleGame;
        this.quizRoyaleGame = quizRoyaleGame;
    }

    public enum GameType {
        WORDLE, SUDOKU, DOBBLE, QUIZ_ROYALE
    }

    public GameLogic createGame(GameType type) {
        return switch (type) {
            case WORDLE -> wordleGame;
            case SUDOKU -> sudokuGameHandler;
            case DOBBLE -> dobbleGame;
            case QUIZ_ROYALE -> quizRoyaleGame;
            default -> throw new IllegalArgumentException("Unknown game type: " + type);
        };
    }
}

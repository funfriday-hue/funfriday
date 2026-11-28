package com.funfriday.factory;

import com.funfriday.games.dobble.DobbleGame;
import com.funfriday.games.quizroyale.QuizRoyaleGame;
import com.funfriday.games.suduko.SudokuGameHandler;
import com.funfriday.games.wordle.WordleGame;
import com.funfriday.games.crossword.CrosswordGame;
import com.funfriday.games.logicgrid.LogicGridGame;
import com.funfriday.games.wavelength.WavelengthGame;
import com.funfriday.service.GameLogic;
import org.springframework.stereotype.Component;

@Component
public class GameFactory {

    private final WordleGame wordleGame;
    private final SudokuGameHandler sudokuGameHandler;
    private final DobbleGame dobbleGame;
    private final QuizRoyaleGame quizRoyaleGame;
    private final CrosswordGame crosswordGame;
    private final LogicGridGame logicGridGame;
    private final WavelengthGame wavelengthGame;

    public GameFactory(WordleGame wordleGame, SudokuGameHandler sudokuGameHandler, DobbleGame dobbleGame, QuizRoyaleGame quizRoyaleGame, CrosswordGame crosswordGame, LogicGridGame logicGridGame, WavelengthGame wavelengthGame) {
        this.wordleGame = wordleGame;
        this.sudokuGameHandler = sudokuGameHandler;
        this.dobbleGame = dobbleGame;
        this.quizRoyaleGame = quizRoyaleGame;
        this.crosswordGame = crosswordGame;
        this.logicGridGame = logicGridGame;
        this.wavelengthGame = wavelengthGame;
    }

    public enum GameType {
        WORDLE, SUDOKU, DOBBLE, QUIZ_ROYALE, CROSSWORD, LOGIC_GRID, WAVELENGTH
    }

    public GameLogic createGame(GameType type) {
        return switch (type) {
            case WORDLE -> wordleGame;
            case SUDOKU -> sudokuGameHandler;
            case DOBBLE -> dobbleGame;
            case QUIZ_ROYALE -> quizRoyaleGame;
            case CROSSWORD -> crosswordGame;
            case LOGIC_GRID -> logicGridGame;
            case WAVELENGTH -> wavelengthGame;
            default -> throw new IllegalArgumentException("Unknown game type: " + type);
        };
    }
}

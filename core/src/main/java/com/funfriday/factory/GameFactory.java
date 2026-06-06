package com.funfriday.factory;

import com.funfriday.games.suduko.SudokuGameHandler;
import com.funfriday.games.wordle.WordleGame;
import com.funfriday.games.wordle.WordleDictionary;
import com.funfriday.service.GameLogic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameFactory {

    private final WordleDictionary wordleDictionary;

    public enum GameType {
        WORDLE, SUDOKU
    }

    public GameLogic createGame(GameType type) {
        return switch (type) {
            case WORDLE -> new WordleGame(wordleDictionary);
            case SUDOKU -> new SudokuGameHandler();
            default -> throw new IllegalArgumentException("Unknown game type: " + type);
        };
    }
}
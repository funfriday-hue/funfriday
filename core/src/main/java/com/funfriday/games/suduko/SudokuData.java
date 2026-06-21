package com.funfriday.games.suduko;

import com.funfriday.model.GameData;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Setter
@Getter
public class SudokuData extends GameData<SudokuConfiguration> {
    // Stores the board for each player (size depends on mode: 9x9 or 6x6)
    private Map<String, int[][]> playerBoards = new ConcurrentHashMap<>();

    // The initial board provided at the start (to know which cells are immutable)
    private int[][] initialBoard;

    // The solved board for the puzzle
    private int[][] solution;

    // Board size (9 or 6)
    private int boardSize;

    public SudokuData(int[][] puzzle, int[][] solution, int boardSize) {
        this.initialBoard = puzzle;
        this.solution = solution;
        this.boardSize = boardSize;
    }

    public void initializePlayer(String playerId) {
        // Deep copy the initial board for the new player
        int[][] boardCopy = new int[boardSize][boardSize];
        for (int i = 0; i < boardSize; i++) {
            boardCopy[i] = initialBoard[i].clone();
        }
        playerBoards.put(playerId, boardCopy);
    }

}

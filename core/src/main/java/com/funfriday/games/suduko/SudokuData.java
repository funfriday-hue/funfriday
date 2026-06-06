package com.funfriday.games.suduko;

import com.funfriday.model.GameData;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Setter
@Getter
public class SudokuData extends GameData<SudokuConfiguration> {
    // Stores the 9x9 board for each player
    private Map<String, int[][]> playerBoards = new ConcurrentHashMap<>();

    // The initial board provided at the start (to know which cells are immutable)
    private int[][] initialBoard;

    public SudokuData(int[][] puzzle) {
        this.initialBoard = puzzle;
    }

    public void initializePlayer(String playerId) {
        // Deep copy the initial board for the new player
        int[][] boardCopy = new int[9][9];
        for (int i = 0; i < 9; i++) {
            boardCopy[i] = initialBoard[i].clone();
        }
        playerBoards.put(playerId, boardCopy);
    }

}
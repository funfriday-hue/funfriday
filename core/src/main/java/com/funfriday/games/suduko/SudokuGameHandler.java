package com.funfriday.games.suduko;
import com.funfriday.model.*;
import com.funfriday.service.GameLogic;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SudokuGameHandler implements GameLogic {

    @Override
    public GameData initializeData(GameConfiguration configuration) {
        int[][] puzzle = {
                {5, 3, 0, 0, 7, 0, 0, 0, 0},
                {6, 0, 0, 1, 9, 5, 0, 0, 0},
                {0, 9, 8, 0, 0, 0, 0, 6, 0},
                {8, 0, 0, 0, 6, 0, 0, 0, 3},
                {4, 0, 0, 8, 0, 3, 0, 0, 1},
                {7, 0, 0, 0, 2, 0, 0, 0, 6},
                {0, 6, 0, 0, 0, 0, 2, 8, 0},
                {0, 0, 0, 4, 1, 9, 0, 0, 5},
                {0, 0, 0, 0, 8, 0, 0, 7, 9}
        };
        return new SudokuData(puzzle);
    }

// Inside your SudokuGameHandler.java class

    @Override
    public void processMove(GameAction action, GameData data) {
        SudokuAction sAction = (SudokuAction) action;
        SudokuData sData = (SudokuData) data;
        String playerId = action.getPlayerId();

        if ("SUDOKU_SYNC".equals(sAction.getType())) {
            if (sAction.getBoard() != null) {
                int[][] incoming = sAction.getBoard();
                int[][] initial = sData.getInitialBoard();
                int[][] validated = new int[9][9];
                for (int r = 0; r < 9; r++) {
                    for (int c = 0; c < 9; c++) {
                        // Prevent players from changing pre-filled server cells
                        validated[r][c] = (initial[r][c] != 0) ? initial[r][c] : incoming[r][c];
                    }
                }
                sData.getPlayerBoards().put(playerId, validated);
            }
        }
        else if ("SUDOKU_RESET".equals(sAction.getType())) {
            sData.initializePlayer(playerId);
            // Reset player status to ACTIVE if they were GIVEN_UP
            sData.getScoreBoard().get(playerId).setStatus(PlayerStatus.ACTIVE);
        }
        else if ("SUDOKU_GIVE_UP".equals(sAction.getType())) {
            PlayerStats stats = sData.getScoreBoard().get(playerId);
            if (stats != null) {
                stats.setTimeInSeconds(System.currentTimeMillis() - data.getStartTime());
                stats.setStatus(PlayerStatus.GIVEN_UP);
                System.out.println("PLAYER_EXIT: " + playerId + " conceded match.");
            }
        }
    }

    @Override
    public void updateStats(PlayerStats stats, GameData data) {
        SudokuPlayerStats sStats = (SudokuPlayerStats) stats;
        SudokuData sData = (SudokuData) data;
        String playerId = sStats.getPlayerId();

        int[][] board = sData.getPlayerBoards().get(playerId);
        if (board == null) return;

        // 1. Calculate each metric individually
        int rows = countSolvedRows(board);
        int cols = countSolvedCols(board);
        int boxes = countSolvedBoxes(board);
        int totalSolvedUnits = rows + cols + boxes;

        // 2. Set the individual fields so they show up in your UI/Leaderboard
        sStats.setRowsSolved(rows);
        sStats.setColumnsSolved(cols);
        sStats.setBoxesSolved(boxes);

        // 3. Update progress and score
        double progressPercentage = (totalSolvedUnits / 27.0) * 100.0;
        sStats.setProgress(progressPercentage);
        sStats.setScore((int) progressPercentage);

        // 4. Handle game completion
        if (totalSolvedUnits == 27) {
            sStats.setStatus(PlayerStatus.COMPLETED);
        }

        // Store raw milliseconds
        if (sStats.getStatus() == PlayerStatus.ACTIVE) {
            sStats.setTotalTimeMillis(System.currentTimeMillis() - data.getStartTime());
        }
    }

// Add these helpers to your SudokuGameHandler class:

    private int countSolvedRows(int[][] board) {
        int count = 0;
        for (int r = 0; r < 9; r++) {
            if (SudokuLogicEngine.isUnitValid(board[r])) count++;
        }
        return count;
    }

    private int countSolvedCols(int[][] board) {
        int count = 0;
        for (int c = 0; c < 9; c++) {
            int[] column = new int[9];
            for (int r = 0; r < 9; r++) column[r] = board[r][c];
            if (SudokuLogicEngine.isUnitValid(column)) count++;
        }
        return count;
    }

    private int countSolvedBoxes(int[][] board) {
        int count = 0;
        for (int b = 0; b < 9; b++) {
            int[] box = new int[9];
            int rOffset = (b / 3) * 3;
            int cOffset = (b % 3) * 3;
            int idx = 0;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    box[idx++] = board[rOffset + r][cOffset + c];
                }
            }
            if (SudokuLogicEngine.isUnitValid(box)) count++;
        }
        return count;
    }

    @Override
    public boolean isGameOver(GameData data) {
        return data.isFinished();
    }

    @Override
    public PlayerStats createInitialStats(GamePlayer player, boolean isHost) {
        return new SudokuPlayerStats(player, isHost);
    }

    @Override
    public GameConfiguration parseConfiguration(Map<String, Object> payload) {
        return new SudokuConfiguration();
    }
}
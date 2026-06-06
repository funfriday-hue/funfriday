package com.funfriday.games.suduko;

import com.funfriday.dto.GameModeDTO;
import com.funfriday.model.*;
import com.funfriday.service.GameLogic;
import com.funfriday.service.GameModeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SudokuGameHandler implements GameLogic, GameModeProvider {

    // Sample 9x9 puzzle
    private static final int[][] PUZZLE_9x9 = {
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

    // Sample 6x6 puzzle
// Grid layout (0 represents empty)
    private static final int[][] PUZZLE_6x6 = {
            {6, 2, 0, 5, 0, 3},
            {0, 0, 0, 0, 0, 0},
            {5, 0, 0, 0, 3, 0},
            {0, 6, 0, 0, 2, 0},
            {0, 0, 0, 3, 4, 6},
            {3, 0, 6, 0, 0, 0}
    };

    @Override
    public GameData<?> initializeData(GameConfiguration configuration) {
        SudokuConfiguration sConfig = (SudokuConfiguration) configuration;
        SudokuGameMode mode = sConfig.getGameMode();

        int[][] puzzle = mode == SudokuGameMode.SUDOKU_6X6 ? PUZZLE_6x6 : PUZZLE_9x9;
        SudokuData data = new SudokuData(puzzle, mode.getSize());
        return data;
    }

    @Override
    public void processMove(GameAction action, GameData<?> data) {
        SudokuAction sAction = (SudokuAction) action;
        SudokuData sData = (SudokuData) data;
        String playerId = action.getPlayerId();
        int boardSize = sData.getBoardSize();

        if ("SUDOKU_SYNC".equals(sAction.getType())) {
            if (sAction.getBoard() != null) {
                int[][] incoming = sAction.getBoard();
                int[][] initial = sData.getInitialBoard();
                int[][] validated = new int[boardSize][boardSize];
                for (int r = 0; r < boardSize; r++) {
                    for (int c = 0; c < boardSize; c++) {
                        // Prevent players from changing pre-filled server cells
                        validated[r][c] = (initial[r][c] != 0) ? initial[r][c] : incoming[r][c];
                    }
                }
                sData.getPlayerBoards().put(playerId, validated);
            }
        } else if ("SUDOKU_RESET".equals(sAction.getType())) {
            sData.initializePlayer(playerId);
            // Reset player status to ACTIVE if they were GIVEN_UP
            PlayerStats stats = sData.getScoreBoard().get(playerId);
            if (stats != null) {
                stats.setStatus(PlayerStatus.ACTIVE);
            }
        } else if ("SUDOKU_GIVE_UP".equals(sAction.getType())) {
            PlayerStats stats = sData.getScoreBoard().get(playerId);
            if (stats != null) {
                stats.setTimeInSeconds((System.currentTimeMillis() - data.getStartTime()) / 1000);
                stats.setStatus(PlayerStatus.GIVEN_UP);
                log.info("PLAYER_EXIT: {} conceded match", playerId);
            }
        }
    }

    @Override
    public void updateStats(PlayerStats stats, GameData<?> data) {
        SudokuPlayerStats sStats = (SudokuPlayerStats) stats;
        SudokuData sData = (SudokuData) data;
        String playerId = sStats.getPlayerId();
        int boardSize = sData.getBoardSize();

        int[][] board = sData.getPlayerBoards().get(playerId);
        if (board == null) return;

        // 1. Calculate metrics specific to board size
        int rows = countSolvedRows(board, boardSize);
        int cols = countSolvedCols(board, boardSize);
        int boxes = countSolvedBoxes(board, boardSize, sData.getGameConfiguration().getGameMode());
        int totalSolvedUnits = rows + cols + boxes;

        // 2. Set individual fields
        sStats.setRowsSolved(rows);
        sStats.setColumnsSolved(cols);
        sStats.setBoxesSolved(boxes);

        // 3. Update progress and score based on board mode
        SudokuGameMode mode = sData.getGameConfiguration().getGameMode();
        int maxUnits = mode.getTotalUnits();
        double progressPercentage = (totalSolvedUnits / (double) maxUnits) * 100.0;
        sStats.setProgress(progressPercentage);
        sStats.setScore((int) progressPercentage);

        // 4. Handle game completion
        if (totalSolvedUnits == maxUnits) {
            sStats.setStatus(PlayerStatus.COMPLETED);
        }

        // Store raw milliseconds
        if (sStats.getStatus() == PlayerStatus.ACTIVE) {
            sStats.setTotalTimeMillis(System.currentTimeMillis() - data.getStartTime());
        }
    }

    private int countSolvedRows(int[][] board, int boardSize) {
        int count = 0;
        for (int r = 0; r < boardSize; r++) {
            if (SudokuLogicEngine.isUnitValid(board[r], boardSize)) count++;
        }
        return count;
    }

    private int countSolvedCols(int[][] board, int boardSize) {
        int count = 0;
        for (int c = 0; c < boardSize; c++) {
            int[] column = new int[boardSize];
            for (int r = 0; r < boardSize; r++) column[r] = board[r][c];
            if (SudokuLogicEngine.isUnitValid(column, boardSize)) count++;
        }
        return count;
    }

    private int countSolvedBoxes(int[][] board, int boardSize, SudokuGameMode mode) {
        int row = mode.getRow();
        int col = mode.getCol();
        int count = 0;
        for (int b = 0; b < boardSize; b++) {
            int[] box = new int[boardSize];
            int rOffset = (b / row) * row;
            int cOffset = (b % row) * col;
            int idx = 0;
            for (int r = 0; r < row; r++) {
                for (int c = 0; c < col; c++) {
                    box[idx++] = board[rOffset + r][cOffset + c];
                }
            }
            if (SudokuLogicEngine.isUnitValid(box, boardSize)) count++;
        }
        return count;
    }

    @Override
    public boolean isGameOver(GameData<?> data) {
        return data.isFinished();
    }

    @Override
    public PlayerStats createInitialStats(GamePlayer player, boolean isHost) {
        return new SudokuPlayerStats(player, isHost);
    }

    @Override
    public GameConfiguration parseConfiguration(Map<String, Object> payload) {
        String rawMode = (String) payload.getOrDefault("gameMode", "SUDOKU_9x9");
        try {
            SudokuGameMode mode = SudokuGameMode.valueOf(rawMode.toUpperCase());
            return new SudokuConfiguration(mode);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid Sudoku mode: {}, defaulting to 9x9", rawMode);
            return new SudokuConfiguration(SudokuGameMode.SUDOKU_9X9);
        }
    }

    @Override
    public List<GameModeDTO.ModeOption> getAvailableModes() {
            return Arrays.stream(SudokuGameMode.values())
                    .map(mode -> new GameModeDTO.ModeOption(mode.name(), mode.getDisplayName()))
                    .toList();
    }
}
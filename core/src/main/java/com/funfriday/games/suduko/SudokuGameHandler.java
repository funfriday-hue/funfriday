package com.funfriday.games.suduko;

import com.funfriday.db.dao.SudokuDao;
import com.funfriday.db.model.SudokuPuzzle;
import com.funfriday.dto.GameModeDTO;
import com.funfriday.model.*;
import com.funfriday.service.GameLogic;
import com.funfriday.service.GameModeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SudokuGameHandler implements GameLogic, GameModeProvider {

    private final SudokuDao sudokuDao;

    public SudokuGameHandler(SudokuDao sudokuDao) {
        this.sudokuDao = sudokuDao;
    }

    @Override
    public GameData<?> initializeData(GameConfiguration configuration) {
        SudokuConfiguration sConfig = (SudokuConfiguration) configuration;
        SudokuGameMode mode = sConfig.getGameMode();
        int boardSize = mode.getSize();

        try {
            SudokuPuzzle puzzle = sudokuDao.selectRandomBySize(boardSize)
                    .orElseThrow(() -> new IllegalStateException("No Sudoku puzzle found for size " + boardSize));
            return new SudokuData(
                    parseGrid(puzzle.getGrid(), boardSize),
                    parseGrid(puzzle.getSolution(), boardSize),
                    boardSize
            );
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load Sudoku puzzle for size " + boardSize, e);
        }
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

        int[][] solution = sData.getSolution();

        // 1. Calculate metrics against the puzzle solution
        int rows = countSolvedRows(board, solution, boardSize);
        int cols = countSolvedCols(board, solution, boardSize);
        int boxes = countSolvedBoxes(board, solution, boardSize, sData.getGameConfiguration().getGameMode());
        int digits = countDigitSolved(board, solution, boardSize);
        int totalSolvedUnits = rows + cols + boxes + digits;

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

    private int countDigitSolved(int[][] board, int[][] solution, int boardSize) {
        int count = 0;
        for (int i = 0; i < boardSize; i++) {
            int curr = 0;
            for (int r = 0; r < boardSize; r++) {
                for (int c = 0; c < boardSize; c++) {
                    if (board[r][c] == solution[r][c] && board[r][c] == i + 1) {
                        curr++;
                    }
                }
            }
            if (curr == boardSize) {
                count++;
            }
        }

        return count;
    }

    private int countSolvedRows(int[][] board, int[][] solution, int boardSize) {
        int count = 0;
        for (int r = 0; r < boardSize; r++) {
            boolean solved = true;
            for (int c = 0; c < boardSize; c++) {
                if (board[r][c] != solution[r][c]) {
                    solved = false;
                    break;
                }
            }
            if (solved) count++;
        }
        return count;
    }

    private int countSolvedCols(int[][] board, int[][] solution, int boardSize) {
        int count = 0;
        for (int c = 0; c < boardSize; c++) {
            boolean solved = true;
            for (int r = 0; r < boardSize; r++) {
                if (board[r][c] != solution[r][c]) {
                    solved = false;
                    break;
                }
            }
            if (solved) count++;
        }
        return count;
    }

    private int countSolvedBoxes(int[][] board, int[][] solution, int boardSize, SudokuGameMode mode) {
        int row = mode.getRow();
        int col = mode.getCol();
        int count = 0;
        for (int b = 0; b < boardSize; b++) {
            int rOffset = (b / row) * row;
            int cOffset = (b % row) * col;
            boolean solved = true;
            for (int r = 0; r < row; r++) {
                for (int c = 0; c < col; c++) {
                    if (board[rOffset + r][cOffset + c] != solution[rOffset + r][cOffset + c]) {
                        solved = false;
                        break;
                    }
                }
                if (!solved) break;
            }
            if (solved) count++;
        }
        return count;
    }

    private int[][] parseGrid(String encodedGrid, int boardSize) {
        int expectedLength = boardSize * boardSize;
        if (encodedGrid == null || encodedGrid.length() != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected Sudoku grid length " + expectedLength + " for size " + boardSize
            );
        }

        int[][] grid = new int[boardSize][boardSize];
        for (int i = 0; i < expectedLength; i++) {
            char value = encodedGrid.charAt(i);
            if (!Character.isDigit(value)) {
                throw new IllegalArgumentException("Sudoku grid contains non-digit value at index " + i);
            }
            grid[i / boardSize][i % boardSize] = Character.digit(value, 10);
        }
        return grid;
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

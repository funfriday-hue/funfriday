package com.funfriday.game.generator.sudoku;

import com.funfriday.game.generator.Generator;
import com.funfriday.game.generator.GeneratorSchedule;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("sudokuGenerator")
@GeneratorSchedule(interval = "PT12H")
public class SudokuGenerator implements Generator {

    private static final String URL = "jdbc:mysql://localhost:3306/GameData";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    @Override
    public void generate() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            log.info("Starting sudoku generation cycle");
            generateAndInsert(conn, 6);
            generateAndInsert(conn, 9);
            log.info("Completed sudoku generation cycle");
        } catch (Exception e) {
            log.error("Sudoku generation cycle failed", e);
            throw e;
        }
    }

    private static void generateAndInsert(Connection conn, int size) throws Exception {
        String sql = "INSERT INTO sudoku_puzzles(size, grid, solution) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try {
                log.info("Generating sudoku puzzle size: " + size);
                int[][] solution = generateSolvedBoard(size);
                int[][] puzzle = createPuzzle(solution, size);

                ps.setInt(1, size);
                ps.setString(2, boardToString(puzzle));
                ps.setString(3, boardToString(solution));
                ps.executeUpdate();
                log.info("Inserted sudoku puzzle size: " + size);
            } catch (Exception e) {
                log.error("Failed to insert sudoku puzzle size " + size, e);
                throw e;
            }
        }
    }

    private static int[][] generateSolvedBoard(int size) {
        int[][] board = new int[size][size];
        solve(board, size, new Random());
        return board;
    }

    private static boolean solve(int[][] board, int size, Random rnd) {
        int[] empty = findEmpty(board, size);
        if (empty == null) return true;

        List<Integer> nums = new ArrayList<>();
        for (int i = 1; i <= size; i++) nums.add(i);
        Collections.shuffle(nums, rnd);

        int r = empty[0], c = empty[1];
        for (int n : nums) {
            if (isValid(board, r, c, n, size)) {
                board[r][c] = n;
                if (solve(board, size, rnd)) return true;
                board[r][c] = 0;
            }
        }
        return false;
    }

    private static int[][] createPuzzle(int[][] solution, int size) {
        int[][] puzzle = copy(solution);
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                cells.add(new int[]{r, c});
            }
        }
        Collections.shuffle(cells);

        for (int[] cell : cells) {
            int r = cell[0], c = cell[1];
            int backup = puzzle[r][c];
            puzzle[r][c] = 0;

            int[][] tmp = copy(puzzle);
            if (countSolutions(tmp, size, 2) != 1) {
                puzzle[r][c] = backup;
            }
        }
        return puzzle;
    }

    private static int countSolutions(int[][] board, int size, int limit) {
        int[] empty = findEmpty(board, size);
        if (empty == null) return 1;

        int r = empty[0], c = empty[1];
        int count = 0;

        for (int n = 1; n <= size; n++) {
            if (isValid(board, r, c, n, size)) {
                board[r][c] = n;
                count += countSolutions(board, size, limit);
                board[r][c] = 0;

                if (count >= limit) return count;
            }
        }
        return count;
    }

    private static boolean isValid(int[][] board, int row, int col, int num, int size) {
        for (int i = 0; i < size; i++) {
            if (board[row][i] == num || board[i][col] == num) return false;
        }

        int boxRows = size == 9 ? 3 : 2;
        int boxCols = size == 9 ? 3 : 3;

        int sr = row - row % boxRows;
        int sc = col - col % boxCols;

        for (int r = sr; r < sr + boxRows; r++) {
            for (int c = sc; c < sc + boxCols; c++) {
                if (board[r][c] == num) return false;
            }
        }
        return true;
    }

    private static int[] findEmpty(int[][] board, int size) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] == 0) return new int[]{r, c};
            }
        }
        return null;
    }

    private static int[][] copy(int[][] src) {
        int[][] out = new int[src.length][src[0].length];
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, out[i], 0, src[i].length);
        }
        return out;
    }

    private static String boardToString(int[][] board) {
        StringBuilder sb = new StringBuilder();
        for (int[] row : board) {
            for (int v : row) sb.append(v);
        }
        return sb.toString();
    }
}

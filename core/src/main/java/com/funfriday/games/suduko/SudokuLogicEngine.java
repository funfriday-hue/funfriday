package com.funfriday.games.suduko;
import java.util.HashSet;
import java.util.Set;

public class SudokuLogicEngine {

    public static int countSolvedUnits(int[][] board) {
        int solved = 0;
        for (int i = 0; i < 9; i++) {
            if (isUnitValid(board[i])) solved++; // Rows

            int[] col = new int[9];
            for (int r = 0; r < 9; r++) col[r] = board[r][i];
            if (isUnitValid(col)) solved++; // Cols
        }

        for (int b = 0; b < 9; b++) { // 3x3 Boxes
            int[] box = new int[9];
            int rO = (b / 3) * 3, cO = (b % 3) * 3, idx = 0;
            for (int r = 0; r < 3; r++)
                for (int c = 0; c < 3; c++)
                    box[idx++] = board[rO + r][cO + c];
            if (isUnitValid(box)) solved++;
        }
        return solved;
    }

    public static boolean isUnitValid(int[] unit) {
        Set<Integer> seen = new HashSet<>();
        for (int val : unit) {
            if (val < 1 || val > 9 || !seen.add(val)) return false;
        }
        return seen.size() == 9;
    }
}
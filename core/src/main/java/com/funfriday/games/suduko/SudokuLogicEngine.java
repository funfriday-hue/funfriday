package com.funfriday.games.suduko;
import java.util.HashSet;
import java.util.Set;

public class SudokuLogicEngine {

    public static boolean isUnitValid(int[] unit, int boardSize) {
        Set<Integer> seen = new HashSet<>();
        for (int val : unit) {
            if (val < 1 || val > boardSize || !seen.add(val)) return false;
        }
        return seen.size() == boardSize;
    }
}
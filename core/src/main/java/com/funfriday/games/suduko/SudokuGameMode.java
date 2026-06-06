package com.funfriday.games.suduko;

import lombok.Getter;

@Getter
public enum SudokuGameMode {
    SUDOKU_9X9(9, 3, 3,27),
    SUDOKU_6X6(6, 2, 3, 18);

    private final int size;
    // 9 or 6
    private final int row;
    private final int col;
    private final int totalUnits;     // rows + cols + boxes (9+9+9=27 or 6+6+6=18)

    SudokuGameMode(int size, int row, int col, int totalUnits) {
        this.size = size;
        this.row = row;
        this.col = col;
        this.totalUnits = totalUnits;
    }

    public String getDisplayName() {
        return switch (this) {
            case SUDOKU_9X9 -> "9x9 Classic Sudoku";
            case SUDOKU_6X6 -> "6x6 Mini Sudoku";
        };
    }
}
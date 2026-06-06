package com.funfriday.games.suduko;

import com.funfriday.model.GameConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SudokuConfiguration implements GameConfiguration {
    private SudokuGameMode gameMode = SudokuGameMode.SUDOKU_9X9; // Default to 9x9
}
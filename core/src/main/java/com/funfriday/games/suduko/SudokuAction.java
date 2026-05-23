package com.funfriday.games.suduko;
import com.funfriday.model.GameAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SudokuAction extends GameAction {
    private int[][] board;  // The full 9x9 state
}
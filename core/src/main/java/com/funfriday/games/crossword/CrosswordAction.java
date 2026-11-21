package com.funfriday.games.crossword;

import com.funfriday.model.GameAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CrosswordAction extends GameAction {
  private String[][] board;
}

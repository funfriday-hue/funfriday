package com.funfriday.games.crossword;

import com.funfriday.model.GameConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrosswordConfiguration implements GameConfiguration {
  private Long puzzleId;
}

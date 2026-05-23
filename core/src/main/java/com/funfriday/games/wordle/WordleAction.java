package com.funfriday.games.wordle;

import com.funfriday.model.GameAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WordleAction extends GameAction {
    private String guess;
}
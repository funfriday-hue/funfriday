package com.funfriday.games.dobble;

import com.funfriday.model.GameAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class DobbleAction extends GameAction {
    private String selectedSymbol;
    private List<String> selectedCardIds;
}

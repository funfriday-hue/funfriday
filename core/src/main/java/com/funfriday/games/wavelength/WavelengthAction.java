package com.funfriday.games.wavelength;

import com.funfriday.model.GameAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WavelengthAction extends GameAction {
    private String hint;
    private Integer dialPosition;
}

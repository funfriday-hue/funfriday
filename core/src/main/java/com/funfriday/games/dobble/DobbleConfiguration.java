package com.funfriday.games.dobble;

import com.funfriday.model.GameConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DobbleConfiguration implements GameConfiguration {
    private DobbleGameMode gameMode;
}

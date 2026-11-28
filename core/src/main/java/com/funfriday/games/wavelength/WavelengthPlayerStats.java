package com.funfriday.games.wavelength;

import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WavelengthPlayerStats extends PlayerStats {
    private String teamId;

    public WavelengthPlayerStats(GamePlayer player, boolean host, String teamId) {
        super(player, host);
        this.teamId = teamId;
    }

    @Override
    public double getRankingMetric() {
        return getScore();
    }
}

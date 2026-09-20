package com.funfriday.games.dobble;

import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DobblePlayerStats extends PlayerStats {
    private long totalResponseTimeMillis;
    private int matchesFound;

    public DobblePlayerStats(GamePlayer player, boolean isHost) {
        super(player, isHost);
    }

    @Override
    public double getRankingMetric() {
        // Higher points win; lower response time wins a tie.
        return (getScore() * 1_000_000_000d) - totalResponseTimeMillis;
    }
}

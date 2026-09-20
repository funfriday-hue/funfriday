package com.funfriday.games.quizroyale;

import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class QuizRoyalePlayerStats extends PlayerStats {
    private int strikes;
    private int correctAnswers;
    public QuizRoyalePlayerStats(GamePlayer player, boolean isHost) { super(player, isHost); }
    @Override public double getRankingMetric() { return getScore(); }
}

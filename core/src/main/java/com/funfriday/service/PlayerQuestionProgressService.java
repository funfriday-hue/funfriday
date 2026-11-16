package com.funfriday.service;

import com.funfriday.db.dao.PlayerQuestionProgressDao;
import com.funfriday.games.quizroyale.QuizRoyaleData;
import com.funfriday.games.quizroyale.QuizQuestionResult;
import com.funfriday.model.GameRoom;
import com.funfriday.model.PlayerStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Persists each player's best result for every Quiz Royale question completed in a game. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerQuestionProgressService {
    private final PlayerQuestionProgressDao progressDao;

    public void recordFinishedSelectedQuestion(GameRoom room) {
        if (!(room.getGameData() instanceof QuizRoyaleData data)) return;

        for (QuizQuestionResult result : data.getQuestionResults()) {
            String questionKey = result.getQuestionKey();
            int answerCount = result.getAnswers().size();
            if (questionKey == null || questionKey.isBlank() || answerCount == 0) continue;

            for (PlayerStats playerStats : data.getScoreBoard().values()) {
                int correctAnswers = result.getPlayerCorrectAnswers().getOrDefault(playerStats.getPlayerId(), 0);
                try {
                    progressDao.recordAttempt(playerStats.getPlayerId(), questionKey, correctAnswers, answerCount);
                } catch (Exception exception) {
                    // Game completion must remain available even if the progress write fails.
                    log.warn("Unable to save question progress for player {} and question {}", playerStats.getPlayerId(), questionKey, exception);
                }
            }
        }
    }
}

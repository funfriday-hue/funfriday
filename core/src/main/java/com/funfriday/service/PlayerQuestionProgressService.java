package com.funfriday.service;

import com.funfriday.db.dao.PlayerQuestionProgressDao;
import com.funfriday.games.quizroyale.QuizRoyaleData;
import com.funfriday.games.quizroyale.QuizRoyalePlayerStats;
import com.funfriday.model.GameRoom;
import com.funfriday.model.PlayerStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Persists a player's best result when an explicitly chosen catalogue question finishes. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerQuestionProgressService {
    private final PlayerQuestionProgressDao progressDao;

    public void recordFinishedSelectedQuestion(GameRoom room) {
        if (!(room.getGameData() instanceof QuizRoyaleData data)
                || data.getGameConfiguration().getSelectedQuestionKey() == null
                || data.getQuestions().size() != 1) return;

        String questionKey = data.getQuestions().get(0).getId();
        int answerCount = data.getQuestions().get(0).getAnswers().size();
        if (answerCount == 0) return;

        for (PlayerStats playerStats : data.getScoreBoard().values()) {
            if (!(playerStats instanceof QuizRoyalePlayerStats quizStats)) continue;
            try {
                progressDao.recordAttempt(quizStats.getPlayerId(), questionKey, quizStats.getCorrectAnswers(), answerCount);
            } catch (Exception exception) {
                // Game completion must remain available even if the progress write fails.
                log.warn("Unable to save question progress for player {} and question {}", quizStats.getPlayerId(), questionKey, exception);
            }
        }
    }
}

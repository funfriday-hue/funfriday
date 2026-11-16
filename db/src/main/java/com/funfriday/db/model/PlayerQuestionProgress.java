package com.funfriday.db.model;

/** The best recorded result for one player on one catalogue question. */
public record PlayerQuestionProgress(
        String questionKey,
        int bestCorrectAnswers,
        int answerCount,
        boolean completed
) {
}

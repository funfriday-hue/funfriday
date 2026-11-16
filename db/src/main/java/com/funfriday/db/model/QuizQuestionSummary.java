package com.funfriday.db.model;

/** Public quiz-catalog view. Answers are intentionally excluded. */
public record QuizQuestionSummary(
        String questionKey,
        String questionType,
        String prompt
) {
}

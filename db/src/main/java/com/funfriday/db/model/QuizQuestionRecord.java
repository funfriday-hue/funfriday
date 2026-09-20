package com.funfriday.db.model;

import java.util.List;

public record QuizQuestionRecord(
        long id,
        String questionKey,
        String category,
        String questionType,
        String prompt,
        List<QuizAnswerRecord> answers
) {
}

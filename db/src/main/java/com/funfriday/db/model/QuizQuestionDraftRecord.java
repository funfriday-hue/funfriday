package com.funfriday.db.model;

import java.time.Instant;
import java.util.List;

public record QuizQuestionDraftRecord(
        long id,
        String questionKey,
        String category,
        String questionType,
        String prompt,
        String status,
        String model,
        Instant createdAt,
        Instant reviewedAt,
        List<QuizDraftAnswerRecord> answers
) {
}

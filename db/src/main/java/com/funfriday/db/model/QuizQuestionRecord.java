package com.funfriday.db.model;

import java.util.List;
import java.time.LocalDate;

public record QuizQuestionRecord(
        long id,
        String questionKey,
        String category,
        String questionType,
        String prompt,
        LocalDate lastSyncedAt,
        List<QuizAnswerRecord> answers
) {
}

package com.funfriday.db.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record QuizQuestionDraftRecord(
        long id,
        String questionKey,
        String category,
        String questionType,
        String prompt,
        LocalDate lastSyncedAt,
        String status,
        String model,
        Instant createdAt,
        Instant reviewedAt,
        List<QuizDraftAnswerRecord> answers,
        String similarQuestionKey,
        String similarQuestionPrompt,
        Double similarityScore
) {
}

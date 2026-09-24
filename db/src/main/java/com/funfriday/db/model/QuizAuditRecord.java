package com.funfriday.db.model;

import java.time.Instant;

public record QuizAuditRecord(
        long id,
        long questionId,
        String questionKey,
        String category,
        String questionType,
        String prompt,
        String status,
        String model,
        String suggestionsJson,
        String declineReason,
        Instant createdAt,
        Instant reviewedAt
) { }

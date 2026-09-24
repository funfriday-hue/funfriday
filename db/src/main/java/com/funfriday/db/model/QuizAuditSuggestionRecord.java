package com.funfriday.db.model;

import java.util.List;

public record QuizAuditSuggestionRecord(
        String action,
        String canonicalAnswer,
        int displayOrder,
        String hint,
        List<String> aliases,
        String reason
) { }

package com.funfriday.db.model;

import java.util.List;

public record QuizDraftAnswerRecord(
        long id,
        String canonicalAnswer,
        int displayOrder,
        String hint,
        List<String> aliases
) {
}

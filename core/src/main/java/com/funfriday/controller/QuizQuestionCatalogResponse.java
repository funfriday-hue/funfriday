package com.funfriday.controller;

import com.funfriday.db.model.QuizQuestionSummary;

import java.util.List;

/** Public catalogue payload. Logged-out visitors receive five questions and locked placeholders. */
public record QuizQuestionCatalogResponse(
        List<QuizQuestionSummary> questions,
        int lockedQuestionCount,
        boolean signedIn
) {
}

package com.funfriday.games.quizroyale;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Immutable answer-review snapshot for one completed Quiz Royale question. */
@Data
@AllArgsConstructor
public class QuizQuestionResult {
    private int questionNumber;
    private String prompt;
    private QuizCategory category;
    private QuizQuestionType questionType;
    private List<String> answers;
    private List<String> hints;
    private List<Integer> answeredAnswerIndexes;
}

package com.funfriday.games.quizroyale;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class QuizQuestion {
    private String id;
    private QuizCategory category;
    private QuizQuestionType type;
    private String prompt;
    private List<QuizAnswer> answers;
    private List<String> chronologyHints;
}

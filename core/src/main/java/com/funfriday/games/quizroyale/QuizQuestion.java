package com.funfriday.games.quizroyale;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor
public class QuizQuestion {
    private String id;
    private QuizCategory category;
    private QuizQuestionType type;
    private String prompt;
    private List<QuizAnswer> answers;
    private List<String> chronologyHints;
    /** For live ranking questions this is the date the leaderboard was last verified. */
    private LocalDate lastSyncedAt;
}

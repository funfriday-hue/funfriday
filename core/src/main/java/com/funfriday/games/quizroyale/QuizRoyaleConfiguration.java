package com.funfriday.games.quizroyale;

import com.funfriday.model.GameConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class QuizRoyaleConfiguration implements GameConfiguration {
    private QuizCategory category;
    private QuizQuestionType questionType;
    private QuizRoyalePlayMode playMode;
    private int strikeLimit;
    private int turnSeconds;
    private int questionCount;
}

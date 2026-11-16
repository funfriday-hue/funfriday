package com.funfriday.games.quizroyale;

import com.funfriday.model.GameData;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data @EqualsAndHashCode(callSuper = true)
public class QuizRoyaleData extends GameData<QuizRoyaleConfiguration> {
    private QuizQuestion question;
    private List<QuizQuestion> questions = new ArrayList<>();
    private List<QuizQuestionResult> questionResults = new ArrayList<>();
    private int questionIndex;
    private List<String> playerOrder = new ArrayList<>();
    private boolean questionActive;
    private long questionTransitionEndsAtMillis;
    private List<String> turnOrder = new ArrayList<>();
    private int currentPlayerIndex;
    private int chronologyIndex;
    private List<String> chronologyEligiblePlayerIds = new ArrayList<>();
    private List<String> chronologyPassedPlayerIds = new ArrayList<>();
    private List<String> acceptedAnswers = new ArrayList<>();
    private List<Integer> answeredAnswerIndexes = new ArrayList<>();
    private List<Integer> revealedAnswerIndexes = new ArrayList<>();
    /** Correct answers earned by each player on the current question only. */
    private Map<String, Integer> questionCorrectAnswers = new LinkedHashMap<>();
    private long turnStartedAtMillis;
    private String lastEvent;
    private List<String> allPlayAnsweredPlayerIds = new ArrayList<>();
}

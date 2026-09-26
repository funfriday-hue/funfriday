package com.funfriday.games.quizroyale;

import com.funfriday.model.GameData;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.ArrayList;
import java.util.List;

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
    private long turnStartedAtMillis;
    private String lastEvent;
    private List<String> allPlayAnsweredPlayerIds = new ArrayList<>();
}

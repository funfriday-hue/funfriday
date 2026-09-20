package com.funfriday.games.quizroyale;

import com.funfriday.model.GameData;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.ArrayList;
import java.util.List;

@Data @EqualsAndHashCode(callSuper = true)
public class QuizRoyaleData extends GameData<QuizRoyaleConfiguration> {
    private QuizQuestion question;
    private List<String> turnOrder = new ArrayList<>();
    private int currentPlayerIndex;
    private int chronologyIndex;
    private List<String> chronologyEligiblePlayerIds = new ArrayList<>();
    private List<String> chronologyPassedPlayerIds = new ArrayList<>();
    private List<String> acceptedAnswers = new ArrayList<>();
    private long turnStartedAtMillis;
    private String lastEvent;
    private List<String> allPlayAnsweredPlayerIds = new ArrayList<>();
}

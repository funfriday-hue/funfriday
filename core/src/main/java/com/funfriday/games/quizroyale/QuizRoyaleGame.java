package com.funfriday.games.quizroyale;

import com.funfriday.dto.GameModeDTO;
import com.funfriday.db.dao.QuizQuestionDao;
import com.funfriday.db.model.QuizQuestionRecord;
import com.funfriday.exception.InvalidGameMoveException;
import com.funfriday.model.*;
import com.funfriday.service.GameLogic;
import com.funfriday.service.GameModeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/** Seed-ready Quiz Royale engine. Replace sampleQuestion() with a repository-backed question provider. */
@Component @RequiredArgsConstructor
public class QuizRoyaleGame implements GameLogic, GameModeProvider {
    private final AnswerMatcher answerMatcher;
    private final QuizQuestionDao quizQuestionDao;

    @Override public GameData<?> initializeData(GameConfiguration rawConfiguration) {
        QuizRoyaleConfiguration configuration = (QuizRoyaleConfiguration) rawConfiguration;
        QuizRoyaleData data = new QuizRoyaleData();
        data.setQuestion(sampleQuestion(configuration.getCategory(), configuration.getQuestionType()));
        data.setTurnStartedAtMillis(System.currentTimeMillis());
        return data;
    }

    @Override public void processMove(GameAction rawAction, GameData<?> rawData) {
        QuizRoyaleAction action = (QuizRoyaleAction) rawAction;
        QuizRoyaleData data = (QuizRoyaleData) rawData;
        if (data.getGameConfiguration().getPlayMode() == QuizRoyalePlayMode.ALL_PLAY) {
            processAllPlay(action, data);
            return;
        }
        String activePlayerId = currentPlayerId(data);
        if (!Objects.equals(activePlayerId, action.getPlayerId())) throw new InvalidGameMoveException("It is not your turn.", "NOT_YOUR_TURN");
        if ("QUIZ_TIMEOUT".equals(action.getType()) || turnExpired(data)) {
            if (data.getQuestion().getType() == QuizQuestionType.CHRONOLOGY) processChronologyMiss(data, action.getPlayerId(), "Time expired.");
            else applyStrike(data, action.getPlayerId(), "Time expired.");
            return;
        }
        if ("QUIZ_PASS".equals(action.getType())) {
            if (data.getQuestion().getType() == QuizQuestionType.CHRONOLOGY) processChronologyMiss(data, action.getPlayerId(), "Passed the turn.");
            else applyStrike(data, action.getPlayerId(), "Passed the turn.");
            return;
        }
        if (!"QUIZ_ANSWER".equals(action.getType())) throw new InvalidGameMoveException("Unknown Quiz Royale action.", "INVALID_QUIZ_ACTION");

        QuizAnswer expected = expectedAnswer(data, action.getAnswer());
        if (expected == null) {
            data.setLastEvent("Incorrect answer — keep trying.");
            return;
        }
        if (data.getQuestion().getType() != QuizQuestionType.CHRONOLOGY && data.getAcceptedAnswers().contains(expected.getValue())) {
            data.setLastEvent("That answer was already used — keep trying.");
            return;
        }

        data.getAcceptedAnswers().add(expected.getValue());
        QuizRoyalePlayerStats stats = (QuizRoyalePlayerStats) data.getScoreBoard().get(action.getPlayerId());
        stats.setCorrectAnswers(stats.getCorrectAnswers() + 1);
        stats.setScore(stats.getCorrectAnswers());
        data.setLastEvent(stats.getPlayerName() + " found " + expected.getValue());
        if (data.getQuestion().getType() == QuizQuestionType.CHRONOLOGY) data.setChronologyIndex(data.getChronologyIndex() + 1);
        if (data.getAcceptedAnswers().size() == data.getQuestion().getAnswers().size()) { finish(data); return; }
        advanceTurn(data);
        if (data.getQuestion().getType() == QuizQuestionType.CHRONOLOGY) beginNextChronologyStep(data);
    }

    private void processAllPlay(QuizRoyaleAction action, QuizRoyaleData data) {
        if ("QUIZ_TIMEOUT".equals(action.getType()) || turnExpired(data)) {
            activePlayerIds(data).stream().filter(id -> !data.getAllPlayAnsweredPlayerIds().contains(id)).forEach(id -> applyTimeoutStrike(data, id));
            if (shouldFinishAfterElimination(data)) { finish(data); return; }
            data.getAllPlayAnsweredPlayerIds().clear();
            data.setTurnStartedAtMillis(System.currentTimeMillis());
            data.setLastEvent("Time expired — a new All Play round has started.");
            return;
        }
        if ("QUIZ_PASS".equals(action.getType())) {
            if (data.getQuestion().getType() != QuizQuestionType.CHRONOLOGY) {
                throw new InvalidGameMoveException("Passing is only available for chronology questions.", "PASS_NOT_AVAILABLE");
            }
            processAllPlayChronologyPass(data, action.getPlayerId());
            return;
        }
        if (!"QUIZ_ANSWER".equals(action.getType())) throw new InvalidGameMoveException("Unknown Quiz Royale action.", "INVALID_QUIZ_ACTION");
        QuizAnswer expected = expectedAnswer(data, action.getAnswer());
        if (expected == null) { applyAllPlayStrike(data, action.getPlayerId(), "Incorrect answer."); return; }
        data.getAcceptedAnswers().add(expected.getValue());
        if (!data.getAllPlayAnsweredPlayerIds().contains(action.getPlayerId())) data.getAllPlayAnsweredPlayerIds().add(action.getPlayerId());
        QuizRoyalePlayerStats stats = (QuizRoyalePlayerStats) data.getScoreBoard().get(action.getPlayerId());
        stats.setCorrectAnswers(stats.getCorrectAnswers() + 1);
        stats.setScore(stats.getCorrectAnswers());
        data.setLastEvent(stats.getPlayerName() + " found " + expected.getValue());
        if (data.getQuestion().getType() == QuizQuestionType.CHRONOLOGY) data.setChronologyIndex(data.getChronologyIndex() + 1);
        if (data.getAcceptedAnswers().size() == data.getQuestion().getAnswers().size()) finish(data);
        else {
            if (data.getQuestion().getType() == QuizQuestionType.CHRONOLOGY) beginNextChronologyStep(data);
            resetTimer(data);
        }
    }

    private void processAllPlayChronologyPass(QuizRoyaleData data, String playerId) {
        if (data.getChronologyEligiblePlayerIds().isEmpty()) beginNextChronologyStep(data);
        if (data.getChronologyPassedPlayerIds().contains(playerId)) {
            data.setLastEvent("You have already passed this item.");
            return;
        }

        data.getChronologyPassedPlayerIds().add(playerId);
        recordStrike(data, playerId, "Passed this item.");
        boolean everyonePassed = data.getChronologyPassedPlayerIds().containsAll(data.getChronologyEligiblePlayerIds());
        if (!everyonePassed) {
            if (shouldFinishAfterElimination(data)) finish(data);
            return;
        }

        revealChronologyAnswer(data, "All players passed");
        if (data.getAcceptedAnswers().size() == data.getQuestion().getAnswers().size() || shouldFinishAfterElimination(data)) {
            finish(data);
            return;
        }
        beginNextChronologyStep(data);
        resetTimer(data);
    }

    private void applyStrike(QuizRoyaleData data, String playerId, String reason) {
        recordStrike(data, playerId, reason);
        // Round Robin only ends once every player has exhausted their strikes.
        if (activePlayerIds(data).isEmpty()) { finish(data); return; }
        advanceTurn(data);
    }

    private void processChronologyMiss(QuizRoyaleData data, String playerId, String reason) {
        if (data.getChronologyEligiblePlayerIds().isEmpty()) beginNextChronologyStep(data);
        if (!data.getChronologyPassedPlayerIds().contains(playerId)) data.getChronologyPassedPlayerIds().add(playerId);
        recordStrike(data, playerId, reason);

        boolean everyonePassed = !data.getChronologyEligiblePlayerIds().isEmpty()
                && data.getChronologyPassedPlayerIds().containsAll(data.getChronologyEligiblePlayerIds());
        if (everyonePassed) {
            revealChronologyAnswer(data, "All players passed");
            if (data.getAcceptedAnswers().size() == data.getQuestion().getAnswers().size() || activePlayerIds(data).isEmpty()) {
                finish(data);
                return;
            }
            advanceTurn(data);
            beginNextChronologyStep(data);
            return;
        }

        if (activePlayerIds(data).isEmpty()) { finish(data); return; }
        advanceTurn(data);
    }

    private void recordStrike(QuizRoyaleData data, String playerId, String reason) {
        QuizRoyalePlayerStats stats = (QuizRoyalePlayerStats) data.getScoreBoard().get(playerId);
        stats.setStrikes(stats.getStrikes() + 1);
        data.setLastEvent(stats.getPlayerName() + " — " + reason);
        if (stats.getStrikes() >= data.getGameConfiguration().getStrikeLimit()) stats.setStatus(PlayerStatus.ELIMINATED);
    }

    private void applyTimeoutStrike(QuizRoyaleData data, String playerId) {
        QuizRoyalePlayerStats stats = (QuizRoyalePlayerStats) data.getScoreBoard().get(playerId);
        stats.setStrikes(stats.getStrikes() + 1);
        if (stats.getStrikes() >= data.getGameConfiguration().getStrikeLimit()) stats.setStatus(PlayerStatus.ELIMINATED);
    }

    private void applyAllPlayStrike(QuizRoyaleData data, String playerId, String reason) {
        QuizRoyalePlayerStats stats = (QuizRoyalePlayerStats) data.getScoreBoard().get(playerId);
        stats.setStrikes(stats.getStrikes() + 1);
        data.setLastEvent(stats.getPlayerName() + " — " + reason);
        if (stats.getStrikes() >= data.getGameConfiguration().getStrikeLimit()) stats.setStatus(PlayerStatus.ELIMINATED);
        if (shouldFinishAfterElimination(data)) { finish(data); return; }
        resetTimer(data);
    }

    private void advanceTurn(QuizRoyaleData data) {
        List<String> active = activePlayerIds(data);
        int current = active.indexOf(currentPlayerId(data));
        data.setTurnOrder(active);
        data.setCurrentPlayerIndex((current + 1) % active.size());
        resetTimer(data);
    }
    private void beginNextChronologyStep(QuizRoyaleData data) {
        data.setChronologyEligiblePlayerIds(new ArrayList<>(activePlayerIds(data)));
        data.getChronologyPassedPlayerIds().clear();
        data.getAllPlayAnsweredPlayerIds().clear();
    }
    private void revealChronologyAnswer(QuizRoyaleData data, String reason) {
        QuizAnswer expected = data.getQuestion().getAnswers().get(data.getChronologyIndex());
        String hint = data.getChronologyIndex() < data.getQuestion().getChronologyHints().size()
                ? data.getQuestion().getChronologyHints().get(data.getChronologyIndex())
                : "this item";
        data.getAcceptedAnswers().add(expected.getValue());
        data.setChronologyIndex(data.getChronologyIndex() + 1);
        data.setLastEvent(reason + " — " + expected.getValue() + " revealed for " + hint + ".");
    }
    private void resetTimer(QuizRoyaleData data) { data.setTurnStartedAtMillis(System.currentTimeMillis()); }
    private boolean shouldFinishAfterElimination(QuizRoyaleData data) {
        int activePlayers = activePlayerIds(data).size();
        return activePlayers == 0 || (data.getTurnOrder().size() > 1 && activePlayers == 1);
    }
    private String currentPlayerId(QuizRoyaleData data) { return data.getTurnOrder().isEmpty() ? null : data.getTurnOrder().get(data.getCurrentPlayerIndex()); }
    private List<String> activePlayerIds(QuizRoyaleData data) { return data.getTurnOrder().stream().filter(id -> data.getScoreBoard().get(id).getStatus() == PlayerStatus.ACTIVE).toList(); }
    private boolean turnExpired(QuizRoyaleData data) { return System.currentTimeMillis() - data.getTurnStartedAtMillis() > data.getGameConfiguration().getTurnSeconds() * 1000L; }
    private QuizAnswer expectedAnswer(QuizRoyaleData data, String submittedAnswer) {
        if (data.getQuestion().getType() == QuizQuestionType.CHRONOLOGY) {
            QuizAnswer expected = data.getChronologyIndex() < data.getQuestion().getAnswers().size() ? data.getQuestion().getAnswers().get(data.getChronologyIndex()) : null;
            return expected != null && answerMatcher.matches(submittedAnswer, expected) ? expected : null;
        }
        return data.getQuestion().getAnswers().stream().filter(answer -> !data.getAcceptedAnswers().contains(answer.getValue())).filter(answer -> answerMatcher.matches(submittedAnswer, answer)).findFirst().orElse(null);
    }
    private void finish(QuizRoyaleData data) { data.setFinished(true); data.getScoreBoard().values().forEach(stats -> { if (stats.getStatus() == PlayerStatus.ACTIVE) stats.setStatus(PlayerStatus.COMPLETED); }); }

    @Override public void updateStats(PlayerStats stats, GameData<?> data) { }
    @Override public boolean isGameOver(GameData<?> data) { return data.isFinished(); }
    @Override public PlayerStats createInitialStats(GamePlayer player, boolean isHost) { return new QuizRoyalePlayerStats(player, isHost); }
    @Override public GameConfiguration parseConfiguration(Map<String, Object> payload) {
        QuizCategory category = QuizCategory.valueOf(String.valueOf(payload.getOrDefault("gameMode", "CRICKET")).toUpperCase());
        int strikeLimit;
        try {
            strikeLimit = Integer.parseInt(String.valueOf(payload.getOrDefault("strikeLimit", 2)));
        } catch (NumberFormatException exception) {
            strikeLimit = 2;
        }
        int turnSeconds;
        try {
            turnSeconds = Integer.parseInt(String.valueOf(payload.getOrDefault("turnSeconds", 60)));
        } catch (NumberFormatException exception) {
            turnSeconds = 60;
        }
        turnSeconds = turnSeconds == 10 || turnSeconds == 30 || turnSeconds == 60 || turnSeconds == 300 ? turnSeconds : 60;
        QuizRoyalePlayMode playMode;
        try { playMode = QuizRoyalePlayMode.valueOf(String.valueOf(payload.getOrDefault("playMode", "ROUND_ROBIN")).toUpperCase()); }
        catch (IllegalArgumentException exception) { playMode = QuizRoyalePlayMode.ROUND_ROBIN; }
        return new QuizRoyaleConfiguration(category, QuizQuestionType.LIST, playMode, Math.max(1, Math.min(5, strikeLimit)), turnSeconds);
    }
    @Override public List<GameModeDTO.ModeOption> getAvailableModes() {
        return List.of(
                new GameModeDTO.ModeOption("CRICKET", "Cricket"),
                new GameModeDTO.ModeOption("FOOTBALL", "Football"),
                new GameModeDTO.ModeOption("BOLLYWOOD", "Bollywood"),
                new GameModeDTO.ModeOption("WWE", "WWE")
        );
    }

    private QuizQuestion sampleQuestion(QuizCategory category, QuizQuestionType type) {
        try {
            QuizQuestionRecord record = quizQuestionDao.selectRandomActiveByCategory(category.name())
                    .orElseThrow(() -> new IllegalStateException("No active " + category.name() + " Quiz Royale questions found in MySQL."));
            if (record.answers().isEmpty()) {
                throw new IllegalStateException("Quiz Royale question " + record.questionKey() + " has no answers.");
            }
            return new QuizQuestion(
                    record.questionKey(),
                    QuizCategory.valueOf(record.category()),
                    QuizQuestionType.valueOf(record.questionType()),
                    record.prompt(),
                    record.answers().stream().map(answer -> new QuizAnswer(answer.canonicalAnswer(), answer.aliases())).toList(),
                    record.answers().stream().map(answer -> answer.hint()).toList()
            );
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to load Quiz Royale question from MySQL.", exception);
        }
    }
}

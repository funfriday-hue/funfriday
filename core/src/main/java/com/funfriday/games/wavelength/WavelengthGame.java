package com.funfriday.games.wavelength;

import com.funfriday.model.GameAction;
import com.funfriday.model.GameConfiguration;
import com.funfriday.model.GameData;
import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;
import com.funfriday.service.GameLogic;
import com.funfriday.db.dao.WavelengthCardDao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WavelengthGame implements GameLogic {
    private static final WavelengthCard LEFT_EDGE_CARD =
        new WavelengthCard("Absolutely no", "Absolutely yes", "Opinions", -100);
    private static final WavelengthCard RIGHT_EDGE_CARD =
        new WavelengthCard("Never again", "Every single day", "Habits", 100);
    private static final List<WavelengthCard> CARD_LIBRARY = List.of(
        new WavelengthCard("Harmless hobby", "Life-changing obsession", "Everyday", 32),
        new WavelengthCard("Tiny inconvenience", "Total nightmare", "Everyday", -28),
        new WavelengthCard("Bad first date", "Perfect first date", "Relationships", 47),
        new WavelengthCard("Overrated", "Underrated", "Culture", -14),
        new WavelengthCard("Worst travel snack", "Best travel snack", "Food", 21),
        new WavelengthCard("Not scary", "Terrifying", "Movies", 63),
        new WavelengthCard("Easy to learn", "Takes a lifetime", "Skills", -44),
        new WavelengthCard("Would never rewatch", "Endlessly rewatchable", "Culture", 8),
        new WavelengthCard("Terrible superpower", "Amazing superpower", "Imaginary", 55),
        new WavelengthCard("Barely a sport", "Peak athleticism", "Sports", -4),
        // Edge targets intentionally let the 4-point band touch either end of the dial.
        // The outer 2/3-point wedges are clipped by the physical semicircle.
        LEFT_EDGE_CARD,
        RIGHT_EDGE_CARD
    );
    private final WavelengthCardDao cards;

    @Override
    public GameData<?> initializeData(GameConfiguration raw) {
        WavelengthConfiguration configuration = (WavelengthConfiguration) raw;
        List<WavelengthCard> deck = activeDeck();
        java.util.Collections.shuffle(deck);
        WavelengthData data = new WavelengthData(configuration, deck);
        data.setPlayStartsAtMillis(System.currentTimeMillis());
        data.setClueGiverId(nextClueGiver(data, "A"));
        return data;
    }

    private List<WavelengthCard> activeDeck() {
        try {
            List<WavelengthCard> published = cards.randomActive(20).stream()
                .map(card -> new WavelengthCard(card.leftLabel(), card.rightLabel(), card.category(), card.target()))
                .toList();
            if (!published.isEmpty()) return new ArrayList<>(published);
        } catch (Exception ignored) {
            // A live room remains playable during an isolated content-database outage.
        }
        return new ArrayList<>(CARD_LIBRARY);
    }

    @Override
    public void processMove(GameAction raw, GameData<?> generic) {
        WavelengthData data = (WavelengthData) generic;
        WavelengthAction action = (WavelengthAction) raw;
        String type = action.getType();
        if ("WAVELENGTH_PHASE_TIMEOUT".equals(type)) {
            advanceForTimeout(data);
            return;
        }
        if (data.getPhase() == WavelengthData.Phase.COMPLETE) return;
        String playerTeam = data.getTeamAssignments().get(action.getPlayerId());
        if ("WAVELENGTH_SUBMIT_HINT".equals(type)) {
            if (data.getPhase() != WavelengthData.Phase.CLUE || !Objects.equals(action.getPlayerId(), data.getClueGiverId())) {
                throw new IllegalStateException("Only the assigned clue giver can submit the hint.");
            }
            String hint = action.getHint() == null ? "" : action.getHint().trim();
            if (hint.isBlank() || hint.length() > 50) throw new IllegalStateException("Hints must contain 1 to 50 characters.");
            data.setHint(hint);
            beginGuess(data);
        } else if ("WAVELENGTH_SET_DIAL".equals(type)) {
            if (data.getPhase() != WavelengthData.Phase.GUESS
                    || !Objects.equals(playerTeam, data.guessingTeamId())
                    || Objects.equals(action.getPlayerId(), data.getClueGiverId())) {
                throw new IllegalStateException("Only the clue giver's teammates can move the dial.");
            }
            data.setDialPosition(Math.max(-100, Math.min(100, action.getDialPosition() == null ? 0 : action.getDialPosition())));
        } else if ("WAVELENGTH_SUBMIT_GUESS".equals(type)) {
            if (data.getPhase() != WavelengthData.Phase.GUESS
                    || !Objects.equals(playerTeam, data.guessingTeamId())
                    || Objects.equals(action.getPlayerId(), data.getClueGiverId())) {
                throw new IllegalStateException("Only the clue giver's teammates can lock in the dial.");
            }
            reveal(data);
        } else if ("WAVELENGTH_START_NEXT_ROUND".equals(type)) {
            if (data.getPhase() != WavelengthData.Phase.REVEAL) {
                throw new IllegalStateException("The next round can only be started while the score is revealed.");
            }
            nextRound(data);
        }
    }

    private void advanceForTimeout(WavelengthData data) {
        if (data.getPhase() == WavelengthData.Phase.CLUE) beginGuess(data);
        else if (data.getPhase() == WavelengthData.Phase.GUESS) reveal(data);
        else if (data.getPhase() == WavelengthData.Phase.REVEAL) nextRound(data);
        else if (data.getPhase() == WavelengthData.Phase.BREAK) nextRound(data);
    }

    private void beginGuess(WavelengthData data) {
        data.setPhase(WavelengthData.Phase.GUESS);
        data.setPhaseEndsAtMillis(System.currentTimeMillis() + 75_000L);
    }

    private void reveal(WavelengthData data) {
        int distance = Math.abs(data.getDialPosition() - data.getCurrentCard().target());
        // The -100…100 dial spans 180°. The 25° target contains five equal 5° wedges:
        // 2, 3, 4, 3, 2. The integer dial steps are 0.9° each.
        double distanceDegrees = distance * 180.0 / 200.0;
        int points = distanceDegrees <= 2.5 ? 4 : distanceDegrees <= 7.5 ? 3 : distanceDegrees <= 12.5 ? 2 : 0;
        String guessing = data.guessingTeamId();
        data.getTeamScores().put(guessing, data.getTeamScores().getOrDefault(guessing, 0) + points);
        data.setAwardedPoints(points);
        data.getRoundResults().add(new WavelengthRoundResult(
            data.getRoundNumber(),
            guessing,
            data.getHint() == null || data.getHint().isBlank() ? "No clue submitted" : data.getHint(),
            points
        ));
        data.setPhase(WavelengthData.Phase.REVEAL);
        data.setPhaseEndsAtMillis(System.currentTimeMillis() + 300_000L);
    }

    private void nextRound(WavelengthData data) {
        if (hasCompletedMatch(data)) {
            complete(data);
            return;
        }
        data.setRoundNumber(data.getRoundNumber() + 1);
        data.setActiveTeamId("A".equals(data.getActiveTeamId()) ? "B" : "A");
        data.setCurrentCard(data.getCards().get((data.getRoundNumber() - 1) % data.getCards().size()));
        data.setHint(null);
        data.setDialPosition(0);
        data.setAwardedPoints(null);
        data.setPhase(WavelengthData.Phase.CLUE);
        data.setPhaseEndsAtMillis(System.currentTimeMillis() + 30_000L);
        data.setClueGiverId(nextClueGiver(data, data.getActiveTeamId()));
    }

    private boolean hasCompletedMatch(WavelengthData data) {
        int teamAScore = data.getTeamScores().getOrDefault("A", 0);
        int teamBScore = data.getTeamScores().getOrDefault("B", 0);

        // Team A always plays before Team B. If A reaches 10 on its turn, B gets
        // the matching turn to catch up: 10+ for both teams is a draw, otherwise
        // A wins. A B turn already follows an A turn, so its result is final.
        if (teamBScore >= 10) return true;
        if (teamAScore >= 10) return "B".equals(data.getActiveTeamId());

        // The normal ten-round cap still ends the match after both teams have
        // received five turns.
        return data.getRoundNumber() >= 10;
    }

    private void complete(WavelengthData data) {
        data.setPhase(WavelengthData.Phase.COMPLETE);
        data.setFinished(true);
        data.setPhaseEndsAtMillis(0);
    }

    private String nextClueGiver(WavelengthData data, String team) {
        List<String> members = data.getTeamAssignments().entrySet().stream()
            .filter(entry -> team.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .toList();
        if (members.isEmpty()) return null;

        String previous = data.getLastClueGiverByTeam().get(team);
        int previousIndex = members.indexOf(previous);
        String next = members.get((previousIndex + 1 + members.size()) % members.size());
        data.getLastClueGiverByTeam().put(team, next);
        return next;
    }

    @Override public void updateStats(PlayerStats stats, GameData<?> data) {
        WavelengthData wavelength = (WavelengthData) data;
        WavelengthPlayerStats playerStats = (WavelengthPlayerStats) stats;
        playerStats.setScore(wavelength.getTeamScores().getOrDefault(playerStats.getTeamId(), 0));
    }
    @Override public boolean isGameOver(GameData<?> data) { return ((WavelengthData) data).getPhase() == WavelengthData.Phase.COMPLETE; }
    @Override public PlayerStats createInitialStats(GamePlayer player, boolean host) { return new WavelengthPlayerStats(player, host, ""); }

    @Override
    public GameConfiguration parseConfiguration(Map<String, Object> payload) {
        Map<String, String> assignments = asStringMap(payload.get("teamAssignments"));
        Map<String, String> names = asStringMap(payload.get("teamNames"));
        return new WavelengthConfiguration(assignments, names);
    }
    private Map<String, String> asStringMap(Object input) {
        Map<String, String> result = new LinkedHashMap<>();
        if (input instanceof Map<?, ?> map) map.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
        return result;
    }
}

package com.funfriday.games.wavelength;

import com.funfriday.model.GameData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WavelengthData extends GameData<WavelengthConfiguration> {
    public enum Phase { CLUE, GUESS, REVEAL, BREAK, COMPLETE }

    private Map<String, String> teamNames = new LinkedHashMap<>();
    private Map<String, String> teamAssignments = new LinkedHashMap<>();
    private Map<String, Integer> teamScores = new LinkedHashMap<>();
    private List<WavelengthCard> cards = new ArrayList<>();
    private int roundNumber = 1;
    private String activeTeamId = "A";
    private String clueGiverId;
    /** Last clue giver per team, used to rotate clue duties fairly within each team. */
    private Map<String, String> lastClueGiverByTeam = new LinkedHashMap<>();
    private Phase phase = Phase.CLUE;
    private long phaseEndsAtMillis;
    private String hint;
    private int dialPosition = 0;
    private Integer awardedPoints;
    private WavelengthCard currentCard;
    private List<WavelengthRoundResult> roundResults = new ArrayList<>();

    public WavelengthData(WavelengthConfiguration configuration, List<WavelengthCard> cards) {
        setGameConfiguration(configuration);
        this.teamAssignments.putAll(configuration.getTeamAssignments());
        this.teamNames.put("A", configuration.getTeamNames().getOrDefault("A", "Team A"));
        this.teamNames.put("B", configuration.getTeamNames().getOrDefault("B", "Team B"));
        this.teamScores.put("A", 0);
        this.teamScores.put("B", 0);
        this.cards.addAll(cards);
        this.currentCard = cards.get(0);
        this.phaseEndsAtMillis = System.currentTimeMillis() + 30_000L;
    }

    /** The clue giver's own teammates discuss and set the dial. */
    public String guessingTeamId() { return activeTeamId; }
    public long phaseRemainingSeconds() { return Math.max(0, (phaseEndsAtMillis - System.currentTimeMillis() + 999) / 1000); }
}

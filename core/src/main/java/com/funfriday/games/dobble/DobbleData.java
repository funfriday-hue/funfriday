package com.funfriday.games.dobble;

import com.funfriday.model.GameData;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class DobbleData extends GameData<DobbleConfiguration> {
    private List<DobbleCard> centerCards;
    private List<DobbleCard> outerCards;
    private int currentCardIndex;
    private boolean currentCardSolved;
    private long currentCardOpenedAtMillis;
    private String lastMatchPlayerId;
    private String lastMatchSymbol;
    private DobbleGameMode gameMode;
    private List<DobbleCard> pairCards;
    private int currentRoundIndex;
    private List<DobbleCard> tripleBoard;
    private List<DobbleCard> tripleDeck;

    public DobbleCard getCurrentCard() {
        return currentCardIndex < outerCards.size() ? outerCards.get(currentCardIndex) : null;
    }

    public DobbleCard getCenterCard() {
        int roundIndex = currentCardIndex / DobbleGame.CARDS_PER_ROUND;
        return roundIndex < centerCards.size() ? centerCards.get(roundIndex) : null;
    }
}

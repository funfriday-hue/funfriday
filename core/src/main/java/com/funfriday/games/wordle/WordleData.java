package com.funfriday.games.wordle;


import com.funfriday.model.GameData;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@EqualsAndHashCode(callSuper = true)
public class WordleData extends GameData {
    private List<String> targetWords; // The 10 words for this session
    private Map<String, Integer> playerProgress; // Tracks which word index (0-9) the player is on
    private Map<String, List<WordleAttempt>> playerAttempts;

    // Helper to get current word for a specific player
    public String getCurrentTargetForPlayer(String playerId) {
        int index = playerProgress.getOrDefault(playerId, 0);
        return targetWords.get(index);
    }
}
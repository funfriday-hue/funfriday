package com.funfriday.games.wordle;


import com.funfriday.model.*;
import com.funfriday.service.GameLogic;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WordleGame implements GameLogic {

    private static final int TOTAL_WORDS_RUSH = 10;
    private static final List<String> WORD_POOL = Arrays.asList(
            "REACT", "STORM", "CLOUD", "LIGHT", "FRAME",
            "GHOST", "BLADE", "POWER", "NIGHT", "SPACE"
    );

    @Override
    public void processMove(GameAction action, GameData data) {
        WordleAction wAction = (WordleAction) action;
        WordleData wData = (WordleData) data;
        String playerId = action.getPlayerId();
        String guess = wAction.getGuess().toUpperCase();

        String target = wData.getCurrentTargetForPlayer(playerId);
        WordleGuessResult[] results = calculateColors(guess, target);
        WordleAttempt attempt = new WordleAttempt(playerId, guess, results, System.currentTimeMillis());

        wData.getPlayerAttempts()
                .computeIfAbsent(playerId, k -> new ArrayList<>())
                .add(attempt);

        if (guess.equals(target)) {
            int currentProgress = wData.getPlayerProgress().getOrDefault(playerId, 0);
            wData.getPlayerProgress().put(playerId, currentProgress + 1);

            // FIX: Clear attempts so the next phase starts with an empty board
            // We use a small trick: clear it here, or the frontend can trigger the clear.
            // Let's clear it here so the 'GameData' sent back is fresh.
            wData.getPlayerAttempts().put(playerId, new ArrayList<>());
        }
    }

    private WordleGuessResult[] calculateColors(String guess, String target) {
        WordleGuessResult[] results = new WordleGuessResult[5];
        boolean[] targetUsed = new boolean[5];
        boolean[] guessMatched = new boolean[5];

        // Pass 1: Greens
        for (int i = 0; i < 5; i++) {
            if (guess.charAt(i) == target.charAt(i)) {
                results[i] = WordleGuessResult.GREEN;
                targetUsed[i] = true;
                guessMatched[i] = true;
            }
        }

        // Pass 2: Yellows/Grays
        for (int i = 0; i < 5; i++) {
            if (guessMatched[i]) continue;
            for (int j = 0; j < 5; j++) {
                if (!targetUsed[j] && guess.charAt(i) == target.charAt(j)) {
                    results[i] = WordleGuessResult.YELLOW;
                    targetUsed[j] = true;
                    break;
                }
            }
            if (results[i] == null) results[i] = WordleGuessResult.GRAY;
        }
        return results;
    }

    @Override
    public void updateStats(PlayerStats stats, GameData data) {
        WordlePlayerStats wStats = (WordlePlayerStats) stats;
        WordleData wData = (WordleData) data;
        String playerId = wStats.getPlayerId();

        // 1. Pull actual progress from data
        int currentProgress = wData.getPlayerProgress().getOrDefault(playerId, 0);
        wStats.setWordsCleared(currentProgress);

        // 2. Increment TOTAL attempts (for the global leaderboard)
        wStats.setTotalAttempts(wStats.getTotalAttempts() + 1);

        // 3. Get attempts for the CURRENT word only
        List<WordleAttempt> currentWordAttempts = wData.getPlayerAttempts().getOrDefault(playerId, new ArrayList<>());
        int attemptsOnCurrentWord = currentWordAttempts.size();

        // 4. Set Status based on CURRENT word attempts (e.g., 6 tries per word)
        // If they use 6 attempts and haven't solved the word, they fail.
        if (attemptsOnCurrentWord >= 12 && currentProgress < TOTAL_WORDS_RUSH) {
            // Double check if the last attempt was actually a success
            // (to prevent failing on the exact move you solve it)
            boolean lastAttemptWasSuccess = !currentWordAttempts.isEmpty() &&
                    Arrays.stream(currentWordAttempts.get(attemptsOnCurrentWord - 1).getResult())
                            .allMatch(r -> r == WordleGuessResult.GREEN);

            if (!lastAttemptWasSuccess) {
                wStats.setStatus(PlayerStatus.FAILED);
            }
        } else if (currentProgress >= TOTAL_WORDS_RUSH) {
            wStats.setStatus(PlayerStatus.COMPLETED);
        }
    }

    @Override
    public GameData initializeData() {
        WordleData data = new WordleData();

        // Initialize 10 random words (or a fixed list)
        List<String> rushWords = new ArrayList<>(WORD_POOL);
        Collections.shuffle(rushWords);
        data.setTargetWords(rushWords.subList(0, TOTAL_WORDS_RUSH));

        // Initialize maps
        data.setPlayerProgress(new HashMap<>());
        data.setPlayerAttempts(new HashMap<>());
        data.setFinished(false);

        return data;
    }

    @Override
    public boolean isGameOver(GameData data) {
        return data.isFinished();
    }

    @Override
    public PlayerStats createInitialStats(GamePlayer player, boolean isHost) {
        return new WordlePlayerStats(player, isHost);
    }
}
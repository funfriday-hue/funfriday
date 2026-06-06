package com.funfriday.games.wordle;

import com.funfriday.dto.GameModeDTO;
import com.funfriday.exception.InvalidGameMoveException;
import com.funfriday.model.*;
import com.funfriday.service.GameLogic;
import com.funfriday.service.GameModeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WordleGame implements GameLogic, GameModeProvider {

    private static final int TOTAL_WORDS_RUSH = 10;

    private final WordleDictionary dictionary;

    @Override
    public void processMove(GameAction action, GameData<?> data) {
        WordleAction wAction = (WordleAction) action;
        WordleData wData = (WordleData) data;
        String playerId = action.getPlayerId();
        String guess = wAction.getGuess().toUpperCase();

        // Validate guess against comprehensive validation dictionary
        if (guess.length() != 5 || !dictionary.isValidWord(guess)) {
            throw new InvalidGameMoveException("The word '" + guess + "' is not in the dictionary.", "NOT_A_VALID_WORD");
        }

        // Process valid guess
        String target = wData.getCurrentTargetForPlayer(playerId);
        WordleGuessResult[] results = calculateColors(guess, target);
        WordleAttempt attempt = new WordleAttempt(playerId, guess, results, System.currentTimeMillis());

        wData.getPlayerAttempts()
                .computeIfAbsent(playerId, k -> new ArrayList<>())
                .add(attempt);

        if (guess.equals(target)) {
            int currentProgress = wData.getPlayerProgress().getOrDefault(playerId, 0);
            wData.getPlayerProgress().put(playerId, currentProgress + 1);
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
    public void updateStats(PlayerStats stats, GameData<?> data) {
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

        // 4. Set fallback default if no mode was explicitly passed during initialization
        WordleGameMode activeMode = wData.getGameMode() != null ? wData.getGameMode() : WordleGameMode.WORD_10;

        // 5. Evaluate Strike-out Failure Conditions (Max 12 attempts per word)
        if (attemptsOnCurrentWord >= 12) {
            boolean lastAttemptWasSuccess = !currentWordAttempts.isEmpty() &&
                    Arrays.stream(currentWordAttempts.get(attemptsOnCurrentWord - 1).getResult())
                            .allMatch(r -> r == WordleGuessResult.GREEN);

            if (!lastAttemptWasSuccess) {
                wStats.setStatus(PlayerStatus.FAILED);
                return;
            }
        }

        // 6. Evaluate End-Game Status using type-safe Enum properties
        if (activeMode.getRuleType() == WordleGameMode.RuleType.TIME_ATTACK) {
            // --- TIME ATTACK ENGINE PROTOCOL ---
            // Keeps players running infinitely until the central room clock loop hits zero.
            if (wStats.getStatus() != PlayerStatus.FAILED) {
                wStats.setStatus(PlayerStatus.ACTIVE);
            }
        } else {
            // --- WORD COUNT SPRINT ENGINE PROTOCOL ---
            // Normal target race. First player to clear the required words wins.
            if (currentProgress >= activeMode.getTargetValue()) {
                wStats.setStatus(PlayerStatus.COMPLETED);
            }
        }
    }

    @Override
    public GameData<?> initializeData(GameConfiguration configuration) {
        WordleData data = new WordleData();

        // Cast configuration to WordleConfiguration to access game mode
        WordleConfiguration wordleConfig = (WordleConfiguration) configuration;
        WordleGameMode gameMode = wordleConfig.getGameMode();

        // Use the configured game mode
        data.setGameMode(gameMode);

        // Use dictionary to get random game words
        int wordCount = gameMode.getTargetValue();
        List<String> selectedWords = dictionary.getRandomGameWords(wordCount);
        data.setTargetWords(selectedWords);

        data.setPlayerProgress(new ConcurrentHashMap<>());
        data.setPlayerAttempts(new ConcurrentHashMap<>());
        data.setFinished(false);

        if (gameMode.getRuleType() == WordleGameMode.RuleType.TIME_ATTACK) {
            data.setEndTimeMillis(System.currentTimeMillis() + (gameMode.getTargetValue() * 1000L));
        }

        return data;
    }

    @Override
    public GameConfiguration parseConfiguration(Map<String, Object> payload) {
        // Fetch the raw string sent from the UI (e.g., "TIME_3") safely from the request map
        String rawMode = (String) payload.getOrDefault("gameMode", "WORD_10");

        WordleGameMode mode = WordleGameMode.valueOf(rawMode.toUpperCase());
        return new WordleConfiguration(mode);
    }

    @Override
    public boolean isGameOver(GameData data) {
        return data.isFinished();
    }

    @Override
    public PlayerStats createInitialStats(GamePlayer player, boolean isHost) {
        return new WordlePlayerStats(player, isHost);
    }

    @Override
    public List<GameModeDTO.ModeOption> getAvailableModes() {
        return Arrays.stream(WordleGameMode.values())
                .map(mode -> new GameModeDTO.ModeOption(mode.name(), mode.getDisplayName()))
                .collect(Collectors.toList());
    }

}
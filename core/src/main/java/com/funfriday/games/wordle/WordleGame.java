package com.funfriday.games.wordle;


import com.funfriday.exception.InvalidGameMoveException;
import com.funfriday.model.*;
import com.funfriday.service.GameLogic;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class WordleGame implements GameLogic {

    private static final int TOTAL_WORDS_RUSH = 10;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000; // 2 seconds delay between retries

    private static List<String> globalWordPool = new ArrayList<>();

    private static final List<String> WORD_POOL = Arrays.asList(
            "REACT", "STORM", "CLOUD", "LIGHT", "FRAME",
            "GHOST", "BLADE", "POWER", "NIGHT", "SPACE"
    );

    @PostConstruct
    public void loadDictionary() {
        boolean apiSuccess = false;
        int attempt = 0;

        // Step 1: Retry Loop for Remote API
        while (attempt < MAX_RETRIES && !apiSuccess) {
            attempt++;
            try {
                System.out.println("🔄 [" + attempt + "/" + MAX_RETRIES + "] Attempting to connect to Wordle API...");
                RestTemplate restTemplate = new RestTemplate();
                String url = "https://raw.githubusercontent.com/tabatkins/wordle-list/master/words";
                String response = restTemplate.getForObject(url, String.class);

                if (response != null && !response.isEmpty()) {
                    this.globalWordPool = Arrays.stream(response.split("\\r?\\n"))
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .filter(word -> word.length() == 5)
                            .collect(Collectors.toList());

                    System.out.println("🚀 Success! Loaded " + globalWordPool.size() + " words dynamically from API on attempt " + attempt);
                    apiSuccess = true;
                }
            } catch (Exception e) {
                System.err.println("⚠️ Connection attempt " + attempt + " failed: " + e.getMessage());

                // If we haven't reached max retries, wait a bit before trying again
                if (attempt < MAX_RETRIES) {
                    try {
                        System.out.println("Sleeping for " + (RETRY_DELAY_MS / 1000) + " seconds before next retry...");
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        System.err.println("❌ Retry sleep interrupted.");
                        break;
                    }
                }
            }
        }

        // Step 2: Fallback to local resource file if all retries failed
        if (this.globalWordPool.isEmpty()) {
            System.out.println("📦 All API retries exhausted. Engaging fallback protocol: Loading wordle_dictionary.txt...");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new ClassPathResource("wordle_dictionary.txt").getInputStream()))) {

                this.globalWordPool = reader.lines()
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .filter(word -> word.length() == 5)
                        .collect(Collectors.toList());

                System.out.println("✅ Backup complete. Loaded " + globalWordPool.size() + " words from local text resource.");
            } catch (Exception fileEx) {
                System.err.println("❌ Critical Error: Local fallback asset failed to parse! " + fileEx.getMessage());
                // Emergency hardcoded fallback array so your application never fails to launch under any circumstance
                this.globalWordPool = WORD_POOL;
            }
        }
    }

    @Override
    public void processMove(GameAction action, GameData data) {
        WordleAction wAction = (WordleAction) action;
        WordleData wData = (WordleData) data;
        String playerId = action.getPlayerId();
        String guess = wAction.getGuess().toUpperCase();

        // 1. STRICTOR VALIDATION: Length and Dictionary Pool verification
        if (guess.length() != 5 || !globalWordPool.contains(guess)) {
            throw new InvalidGameMoveException("The word '" + guess + "' is not in the dictionary.", "NOT_A_VALID_WORD");
        }


        // 2. Process valid guess (rest of your logic remains completely safe)
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

        List<String> roomSelection = new ArrayList<>(this.globalWordPool);
        Collections.shuffle(roomSelection);
        data.setTargetWords(roomSelection.subList(0, Math.min(TOTAL_WORDS_RUSH, roomSelection.size())));

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
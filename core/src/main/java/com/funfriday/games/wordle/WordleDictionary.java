package com.funfriday.games.wordle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages word dictionaries for Wordle game.
 * - gameWordPool: curated words for game initialization
 * - validationWordPool: comprehensive dictionary for move validation
 */
@Slf4j
@Component
public class WordleDictionary {

    // Fallback words if loading fails
    private static final List<String> FALLBACK_WORDS = List.of(
            "REACT", "STORM", "CLOUD", "LIGHT", "FRAME",
            "GHOST", "BLADE", "POWER", "NIGHT", "SPACE"
    );

    // Pool of words used for initializing game sessions (curated/smaller set)
    private volatile List<String> gameWordPool;

    // Full dictionary for validating player guesses (comprehensive set)
    private volatile List<String> validationWordPool;

    public WordleDictionary() {
        // Initialize on construction
        loadDictionaries();
    }

    /**
     * Load both word pools from resource files.
     */
    public void loadDictionaries() {
        gameWordPool = loadGameWordPool();
        validationWordPool = loadValidationWordPool();
    }

    /**
     * Load curated word pool used for initializing game sessions.
     * Source: game_wordle.txt (smaller, curated set)
     */
    private List<String> loadGameWordPool() {
        try {
            log.info("Loading game word pool from game_wordle.txt...");
            List<String> words = new BufferedReader(
                    new InputStreamReader(new ClassPathResource("game_wordle.txt").getInputStream()))
                    .lines()
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(word -> !word.isEmpty() && word.length() == 5)
                    .collect(Collectors.toList());

            if (!words.isEmpty()) {
                log.info("✅ Successfully loaded {} words from game_wordle.txt", words.size());
                return Collections.unmodifiableList(words);
            } else {
                log.warn("⚠️ game_wordle.txt is empty, using fallback words");
                return Collections.unmodifiableList(FALLBACK_WORDS);
            }
        } catch (Exception e) {
            log.error("❌ Failed to load game_wordle.txt: {}", e.getMessage());
            log.info("Using fallback word pool");
            return Collections.unmodifiableList(FALLBACK_WORDS);
        }
    }

    /**
     * Load comprehensive word pool used for validating player guesses.
     * Source: wordle_dictionary.txt (full dictionary)
     */
    private List<String> loadValidationWordPool() {
        try {
            log.info("Loading validation word pool from wordle_dictionary.txt...");
            List<String> words = new BufferedReader(
                    new InputStreamReader(new ClassPathResource("wordle_dictionary.txt").getInputStream()))
                    .lines()
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(word -> !word.isEmpty() && word.length() == 5)
                    .collect(Collectors.toList());

            if (!words.isEmpty()) {
                log.info("✅ Successfully loaded {} words from wordle_dictionary.txt", words.size());
                return Collections.unmodifiableList(words);
            } else {
                log.warn("⚠️ wordle_dictionary.txt is empty, using game word pool as fallback");
                return gameWordPool;
            }
        } catch (Exception e) {
            log.error("❌ Failed to load wordle_dictionary.txt: {}", e.getMessage());
            log.info("Using game word pool as validation fallback");
            return gameWordPool;
        }
    }

    /**
     * Get the pool of words for game initialization.
     */
    public List<String> getGameWordPool() {
        return gameWordPool;
    }

    /**
     * Get the comprehensive dictionary for move validation.
     */
    public List<String> getValidationWordPool() {
        return validationWordPool;
    }

    /**
     * Check if a word is valid (exists in validation pool).
     */
    public boolean isValidWord(String word) {
        return validationWordPool.contains(word.toUpperCase());
    }

    /**
     * Get a random subset of game words for session initialization.
     */
    public List<String> getRandomGameWords(int count) {
        List<String> words = new ArrayList<>(gameWordPool);
        Collections.shuffle(words);
        return Collections.unmodifiableList(words.subList(0, Math.min(count, words.size())));
    }
}
package com.funfriday.games.wordle;

public enum WordleGuessResult {
    GREEN,  // Correct letter, correct spot
    YELLOW, // Correct letter, wrong spot
    GRAY    // Not in word
}
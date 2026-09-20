package com.funfriday.games.wordle;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WordleAttempt {
    private String playerName;
    private String guess;
    private WordleGuessResult[] result; // Typed array instead of String[]
    private long timestamp;
}
package com.funfriday.games.wordle;

import com.funfriday.model.GameConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordleConfiguration implements GameConfiguration {

    // 🎯 Captures your type-safe 6-mode enum natively (WORD_3, TIME_5, etc.)
    private WordleGameMode gameMode;

    // You can easily add other Wordle-specific configurations here later!
    // For example:
    // private boolean hardMode = false;
    // private String dictionaryLanguage = "EN";
}
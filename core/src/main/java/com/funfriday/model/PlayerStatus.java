package com.funfriday.model;

public enum PlayerStatus {
    ACTIVE,      // Currently playing
    COMPLETED,   // Finished all 10 levels
    FAILED,      // Reached 12 attempts on a single word
    ELIMINATED,   // For battle-royale style games (optional)
    GIVEN_UP
}
package com.funfriday.games.dobble;

public enum DobbleGameMode {
    CLASSIC("Classic · 9 Rounds"),
    PAIR_RUSH("Pair Rush · 25 Rounds"),
    TRIPLE_HUNT("Triple Hunt · 12 Rounds");

    private final String displayName;

    DobbleGameMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package com.funfriday.games.wordle;

import lombok.Getter;

@Getter
public enum WordleGameMode {
    // Word-Count Race Engines
    WORD_3(RuleType.WORD_COUNT, 3),
    WORD_5(RuleType.WORD_COUNT, 5),
    WORD_10(RuleType.WORD_COUNT, 10),

    // Time-Attack Blitz Engines (values in seconds)
    TIME_2(RuleType.TIME_ATTACK, 120),
    TIME_3(RuleType.TIME_ATTACK, 180),
    TIME_5(RuleType.TIME_ATTACK, 300);

    public enum RuleType { WORD_COUNT, TIME_ATTACK; }

    private final RuleType ruleType;
    private final int targetValue;

    WordleGameMode(RuleType ruleType, int targetValue) {
        this.ruleType = ruleType;
        this.targetValue = targetValue;
    }

    public String getDisplayName() {
        return switch (this) {
            case WORD_3 -> "3-Word Race";
            case WORD_5 -> "5-Word Race";
            case WORD_10 -> "10-Word Marathon";
            case TIME_2 -> "2 Min Sprint";
            case TIME_3 -> "3 Min Blitz";
            case TIME_5 -> "5 Min Endurance";
            default -> this.name();
        };
    }

}
package com.funfriday.db.model;

public record WavelengthCardRecord(long id, String cardKey, String category, String leftLabel,
                                   String rightLabel, int target) { }

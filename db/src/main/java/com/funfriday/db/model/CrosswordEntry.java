package com.funfriday.db.model;

/** Server-side crossword entry. Answers are never returned by catalogue APIs. */
public record CrosswordEntry(
    int clueNumber, String direction, int row, int column, String answer, String clue) {}

package com.funfriday.db.model;

/**
 * Catalogue-safe crossword metadata. Grid cells, clues and answers are loaded only when a room
 * starts.
 */
public record CrosswordPuzzleSummary(
    long id, String puzzleKey, String category, String title, int clueCount) {}

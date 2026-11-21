package com.funfriday.db.model;

import java.util.List;

public record CrosswordPuzzle(
    long id,
    String puzzleKey,
    String category,
    String title,
    int width,
    int height,
    String gridJson,
    List<CrosswordEntry> entries) {}

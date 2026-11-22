package com.funfriday.db.model;
public record LogicGridPuzzle(long id, String puzzleKey, String category, String title, String story,
    String entitiesJson, String attributesJson, String cluesJson, String solutionJson) {}

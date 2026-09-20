package com.funfriday.db.model;

import java.time.LocalDateTime;

public class SudokuPuzzle {
    private final int id;
    private final int size;
    private final String grid;
    private final String solution;
    private final LocalDateTime createdAt;

    public SudokuPuzzle(int id, int size, String grid, String solution, LocalDateTime createdAt) {
        this.id = id;
        this.size = size;
        this.grid = grid;
        this.solution = solution;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getSize() {
        return size;
    }

    public String getGrid() {
        return grid;
    }

    public String getSolution() {
        return solution;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

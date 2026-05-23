package com.funfriday.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class GameState {
    private String status; // WAITING, IN_PROGRESS, FINISHED
    private int currentRound;
    private Map<String, Integer> scoreBoard; // PlayerName -> Score
    private Object gameSpecificData; // Holds Wordle grids, Mafia roles, etc.

    // Constructor, Getters, and Setters
    public GameState(String status, int currentRound, Map<String, Integer> scoreBoard, Object data) {
        this.status = status;
        this.currentRound = currentRound;
        this.scoreBoard = scoreBoard;
        this.gameSpecificData = data;
    }

}
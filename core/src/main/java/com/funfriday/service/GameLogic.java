package com.funfriday.service;

import com.funfriday.model.*;

import java.util.Map;

public interface GameLogic {
    GameData<?> initializeData(GameConfiguration config);

    // Now uses GameAction instead of Map
    void processMove(GameAction action, GameData<?> data);

    void updateStats(PlayerStats stats, GameData<?> data);

    boolean isGameOver(GameData<?> data);

    PlayerStats createInitialStats(GamePlayer player, boolean isHost);

    GameConfiguration parseConfiguration(Map<String, Object> payload);
}
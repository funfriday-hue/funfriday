package com.funfriday.service;

import com.funfriday.model.GameAction;
import com.funfriday.model.GameData;
import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;

public interface GameLogic {
    GameData initializeData();

    // Now uses GameAction instead of Map
    void processMove(GameAction action, GameData data);

    void updateStats(PlayerStats stats, GameData data);

    boolean isGameOver(GameData data);

    PlayerStats createInitialStats(GamePlayer player, boolean isHost);
}
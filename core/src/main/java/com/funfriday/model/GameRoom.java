package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.funfriday.factory.GameFactory;
import com.funfriday.service.GameLogic;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class GameRoom {
    private String roomId;
    private GameStatus status;
    @JsonIgnore
    private GameLogic gameLogic;
    private GameData gameData;
    private Long startTime;
    private GamePlayer host;
    private GameFactory.GameType type;
    Map<String, GamePlayer> playerMap;

    public GameRoom(String roomId, String hostId, String hostname, GameLogic logic, GameFactory.GameType gameType) {
        this.roomId = roomId;
        this.gameLogic = logic;
        this.startTime = System.currentTimeMillis();
        this.status = GameStatus.WAITING;
        this.type = gameType;
        this.playerMap = new HashMap<>();

        // Let the specific game set up its data requirements
        gameData = this.gameLogic.initializeData();
        this.host = this.addPlayer(hostId, hostname, true);
    }

    public GamePlayer addPlayer(String id, String name, boolean isHost) {
        if (name == null) {
            throw new IllegalStateException("name is required");
        }
        if (this.playerMap.containsKey(id)) {
            return playerMap.get(id);
        }
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        GamePlayer player = new GamePlayer(id, name, isHost);
        this.playerMap.put(id, player);
        this.getGameData().getScoreBoard().put(id, this.gameLogic.createInitialStats(player, isHost));
        return player;
    }

    public void handlePlayerAction(GameAction action) {
        // 1. Validate the game is in a playable state
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Game is not currently active.");
        }

        if (!this.playerMap.containsKey(action.getPlayerId())) {
            throw new IllegalStateException("Invalid User");
        }


        // 2. Process the move using the Stateless Logic
        // This updates the 'gameData' object (e.g., adds an attempt to WordleData)
        this.gameLogic.processMove(action, this.gameData);


        // 3. Update the scoreboard for the player who performed the action
        PlayerStats stats = this.getGameData().getScoreBoard().get(action.getPlayerId());
        if (stats != null) {
            this.gameLogic.updateStats(stats, this.gameData);
            long elapsedMillis = System.currentTimeMillis() - this.startTime;
            stats.setTimeInSeconds(elapsedMillis / 1000);
        }

        // 4. Check if this move ended the game
        if (this.gameLogic.isGameOver(this.gameData)) {
            this.status = GameStatus.FINISHED;
            this.gameData.setFinished(true);
        }
    }

    public String getType() {
        return type.name();
    }


}
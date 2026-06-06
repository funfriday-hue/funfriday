package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.funfriday.dto.GameModeDTO;
import com.funfriday.factory.GameFactory;
import com.funfriday.service.GameLogic;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Getter
@Setter
public class GameRoom {
    private String roomId;
    private GameStatus status;
    @JsonIgnore
    private GameLogic gameLogic;
    private GameData<?> gameData;
    private Long startTime;
    private GamePlayer host;
    private GameFactory.GameType type;
    private List<GameModeDTO.ModeOption> availableModes;
    private final Map<String, GamePlayer> playerMap = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public GameRoom(String roomId, String hostId, String hostname, GameLogic logic, GameFactory.GameType gameType) {
        this.roomId = roomId;
        this.gameLogic = logic;
        this.startTime = System.currentTimeMillis();
        this.status = GameStatus.WAITING;
        this.type = gameType;
        this.host = this.addPlayer(hostId, hostname, true);
    }

    public GamePlayer addPlayer(String id, String name, boolean isHost) {
        if (name == null) {
            throw new IllegalStateException("name is required");
        }
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        GamePlayer newPlayer = new GamePlayer(id, name, isHost);
        GamePlayer existing = playerMap.putIfAbsent(id, newPlayer);
        return existing != null ? existing : newPlayer;
    }

    /**
     * 🎯 Encapsulated Domain Logic: Switches room status from WAITING to IN_PROGRESS
     * and sets up the concrete GameData with initial participant statistics.
     */
    public void startGame(String requestingPlayerId, String gameMode, Map<String, Object> genericProperties) {
        lock.writeLock().lock();
        try {
            if (!this.host.getId().equals(requestingPlayerId)) {
                throw new IllegalStateException("Only the room host can start the game.");
            }
            if (this.status != GameStatus.WAITING) {
                throw new IllegalStateException("Game cannot be started from its current state: " + this.status);
            }

            Map<String, Object> parsePayload = new HashMap<>(genericProperties != null ? genericProperties : new HashMap<>());
            parsePayload.put("gameMode", gameMode);

            GameConfiguration config = this.gameLogic.parseConfiguration(parsePayload);
            GameData<?> initialGameData = this.gameLogic.initializeData(config);
            ((GameData<GameConfiguration>) initialGameData).setGameConfiguration(config);

            Map<String, PlayerStats> freshScoreboard = new ConcurrentHashMap<>();
            for (GamePlayer player : this.playerMap.values()) {
                PlayerStats stats = this.gameLogic.createInitialStats(player, player.isHost());
                freshScoreboard.put(player.getId(), stats);
            }
            initialGameData.setScoreBoard(freshScoreboard);

            this.gameData = initialGameData;
            this.startTime = System.currentTimeMillis();
            this.status = GameStatus.IN_PROGRESS;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void handlePlayerAction(GameAction action) {
        lock.writeLock().lock();
        try {
            if (this.status != GameStatus.IN_PROGRESS) {
                throw new IllegalStateException("Game is not currently active.");
            }

            if (this.gameData != null && this.gameData.getEndTimeMillis() > 0) {
                long playerTime = action.getClientTimestamp();
                if (playerTime >= this.gameData.getEndTimeMillis()) {
                    throw new IllegalStateException("Game time has expired. No more moves allowed.");
                }
            }

            if (!this.playerMap.containsKey(action.getPlayerId())) {
                throw new IllegalStateException("Invalid User");
            }

            // process the move and update stats while holding the write lock
            this.gameLogic.processMove(action, this.gameData);

            PlayerStats stats = this.gameData.getScoreBoard().get(action.getPlayerId());
            if (stats != null) {
                this.gameLogic.updateStats(stats, this.gameData);
                long elapsedMillis = System.currentTimeMillis() - this.startTime;
                stats.setTimeInSeconds(elapsedMillis / 1000);
            }

            if (this.gameLogic.isGameOver(this.gameData)) {
                this.status = GameStatus.FINISHED;
                this.gameData.setFinished(true);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getType() {
        return type.name();
    }


}
package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.funfriday.dto.GameModeDTO;
import com.funfriday.factory.GameFactory;
import com.funfriday.games.quizroyale.QuizRoyaleData;
import com.funfriday.games.wavelength.WavelengthData;
import com.funfriday.games.wavelength.WavelengthPlayerStats;
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
    private String initialGameMode;
    /** Lobby-only Wavelength team choices. They are copied into immutable match data at start. */
    private final Map<String, String> wavelengthTeamAssignments = new ConcurrentHashMap<>();
    private final Map<String, String> wavelengthTeamNames = new ConcurrentHashMap<>();
    @JsonIgnore
    private String originalHostId;
    private final Map<String, GamePlayer> playerMap = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public GameRoom(String roomId, String hostId, String hostname, GameLogic logic, GameFactory.GameType gameType) {
        this.roomId = roomId;
        this.gameLogic = logic;
        this.startTime = System.currentTimeMillis();
        this.status = GameStatus.WAITING;
        this.type = gameType;
        this.wavelengthTeamNames.put("A", "Team A");
        this.wavelengthTeamNames.put("B", "Team B");
        this.host = this.addPlayer(hostId, hostname, true);
        this.originalHostId = this.host.getId();
    }

    public GamePlayer addPlayer(String id, String name, boolean isHost) {
        if (name == null) {
            throw new IllegalStateException("name is required");
        }
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
            if (type == GameFactory.GameType.WAVELENGTH && !playerMap.containsKey(id) && playerMap.size() >= 14) {
            throw new IllegalStateException("A Telepathy room can have at most 14 players.");
        }
        GamePlayer newPlayer = new GamePlayer(id, name, isHost);
        GamePlayer existing = playerMap.putIfAbsent(id, newPlayer);
        return existing != null ? existing : newPlayer;
    }

    public void setPlayerConnection(String playerId, String sessionId, boolean connected) {
        lock.writeLock().lock();
        try {
            GamePlayer player = playerMap.get(playerId);
            if (player != null) player.setSessionConnected(sessionId, connected);
            if (type == GameFactory.GameType.WAVELENGTH) rebalanceWavelengthHost();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setPlayerMicMuted(String playerId, boolean muted) {
        lock.writeLock().lock();
        try {
            GamePlayer player = playerMap.get(playerId);
            if (player == null) throw new IllegalStateException("Player is not in this room.");
            player.setMicMuted(muted);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void assignWavelengthTeam(String playerId, String teamId) {
        lock.writeLock().lock();
        try {
            if (type != GameFactory.GameType.WAVELENGTH || status != GameStatus.WAITING) throw new IllegalStateException("Teams can only be changed in a Telepathy lobby.");
            if (!playerMap.containsKey(playerId)) throw new IllegalStateException("Player is not in this room.");
            if (!"A".equals(teamId) && !"B".equals(teamId)) throw new IllegalStateException("Choose one of the two teams.");
            long existing = wavelengthTeamAssignments.entrySet().stream().filter(entry -> teamId.equals(entry.getValue()) && !playerId.equals(entry.getKey())).count();
            if (existing >= 7) throw new IllegalStateException("That team is already full.");
            wavelengthTeamAssignments.put(playerId, teamId);
        } finally { lock.writeLock().unlock(); }
    }

    public void renameWavelengthTeam(String playerId, String teamId, String teamName) {
        lock.writeLock().lock();
        try {
            if (type != GameFactory.GameType.WAVELENGTH || status != GameStatus.WAITING) throw new IllegalStateException("Teams can only be renamed in a Telepathy lobby.");
            if (!playerMap.containsKey(playerId) || (!teamId.equals(wavelengthTeamAssignments.get(playerId)) && !playerId.equals(host.getId()))) throw new IllegalStateException("Join this team or be the host before renaming it.");
            String clean = teamName == null ? "" : teamName.trim();
            if (clean.length() < 2 || clean.length() > 24) throw new IllegalStateException("Team names must be 2 to 24 characters.");
            wavelengthTeamNames.put(teamId, clean);
        } finally { lock.writeLock().unlock(); }
    }

    private void rebalanceWavelengthHost() {
        GamePlayer original = playerMap.get(originalHostId);
        java.util.List<GamePlayer> connectedPlayers = playerMap.values().stream()
            .filter(GamePlayer::isConnected).toList();
        GamePlayer next = original != null && original.isConnected() ? original
            : connectedPlayers.isEmpty() ? host
            : connectedPlayers.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(connectedPlayers.size()));
        if (next == null || next == host) return;
        playerMap.values().forEach(candidate -> candidate.setHost(candidate.getId().equals(next.getId())));
        host = next;
    }

    public void kickPlayer(String requestingPlayerId, String playerId) {
        lock.writeLock().lock();
        try {
            if (!this.host.getId().equals(requestingPlayerId)) {
                throw new IllegalStateException("Only the room host can remove a player.");
            }
            if (this.status != GameStatus.WAITING) {
                throw new IllegalStateException("Players can only be removed from the lobby.");
            }
            GamePlayer player = playerMap.get(playerId);
            if (player == null) throw new IllegalStateException("Player is no longer in this room.");
            if (player.isHost()) throw new IllegalStateException("The host cannot be removed from the room.");
            playerMap.remove(playerId);
        } finally {
            lock.writeLock().unlock();
        }
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
            if (type == GameFactory.GameType.WAVELENGTH) {
                validateWavelengthTeams();
                parsePayload.put("teamAssignments", new HashMap<>(wavelengthTeamAssignments));
                parsePayload.put("teamNames", new HashMap<>(wavelengthTeamNames));
            }

            GameConfiguration config = this.gameLogic.parseConfiguration(parsePayload);
            initializeNewMatch(config);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Starts a fresh match in the same room using the just-finished match configuration. */
    public void restartGame(String requestingPlayerId) {
        lock.writeLock().lock();
        try {
            if (!this.host.getId().equals(requestingPlayerId)) {
                throw new IllegalStateException("Only the room host can restart the game.");
            }
            if (this.status != GameStatus.FINISHED || this.gameData == null || this.gameData.getGameConfiguration() == null) {
                throw new IllegalStateException("Only a finished game can be restarted.");
            }
            initializeNewMatch(this.gameData.getGameConfiguration());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Returns a completed room to its lobby while preserving its room code and connected players. */
    public void returnToLobby(String requestingPlayerId) {
        lock.writeLock().lock();
        try {
            if (!this.host.getId().equals(requestingPlayerId)) {
                throw new IllegalStateException("Only the room host can return the room to the lobby.");
            }
            if (this.status != GameStatus.FINISHED) {
                throw new IllegalStateException("Only a finished game can return to the lobby.");
            }
            this.gameData = null;
            this.startTime = System.currentTimeMillis();
            this.status = GameStatus.WAITING;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private void initializeNewMatch(GameConfiguration config) {
        GameData<?> initialGameData = this.gameLogic.initializeData(config);
        ((GameData<GameConfiguration>) initialGameData).setGameConfiguration(config);

        Map<String, PlayerStats> freshScoreboard = new ConcurrentHashMap<>();
        for (GamePlayer player : this.playerMap.values()) {
            PlayerStats stats = this.gameLogic.createInitialStats(player, player.isHost());
            freshScoreboard.put(player.getId(), stats);
        }
        if (initialGameData instanceof WavelengthData wavelengthData) {
            freshScoreboard.values().forEach(stats -> {
                if (stats instanceof WavelengthPlayerStats wavelengthStats) {
                    wavelengthStats.setTeamId(wavelengthData.getTeamAssignments().getOrDefault(wavelengthStats.getPlayerId(), ""));
                    wavelengthStats.setScore(wavelengthData.getTeamScores().getOrDefault(wavelengthStats.getTeamId(), 0));
                }
            });
        }
        initialGameData.setScoreBoard(freshScoreboard);
        if (initialGameData instanceof QuizRoyaleData quizData) {
            quizData.setTurnOrder(new java.util.ArrayList<>(this.playerMap.keySet()));
            quizData.setPlayerOrder(new java.util.ArrayList<>(this.playerMap.keySet()));
            quizData.setCurrentPlayerIndex(0);
            quizData.setChronologyEligiblePlayerIds(new java.util.ArrayList<>(this.playerMap.keySet()));
        }

        this.gameData = initialGameData;
        this.startTime = System.currentTimeMillis();
        this.status = GameStatus.IN_PROGRESS;
    }

    private void validateWavelengthTeams() {
        int total = playerMap.size();
        if (total < 4 || total > 14) throw new IllegalStateException("Telepathy needs 4 to 14 players.");
        long a = wavelengthTeamAssignments.values().stream().filter("A"::equals).count();
        long b = wavelengthTeamAssignments.values().stream().filter("B"::equals).count();
        if (wavelengthTeamAssignments.size() != total || a < 2 || b < 2 || Math.abs(a - b) > 1) {
            throw new IllegalStateException("Everyone must choose a balanced team with at least two players per team.");
        }
    }

    public void handlePlayerAction(GameAction action) {
        lock.writeLock().lock();
        try {
            if (this.status != GameStatus.IN_PROGRESS) {
                throw new IllegalStateException("Game is not currently active.");
            }

            if (this.gameData != null && this.gameData.getEndTimeMillis() > 0) {
                if (System.currentTimeMillis() >= this.gameData.getEndTimeMillis()) {
                    throw new IllegalStateException("Game time has expired. No more moves allowed.");
                }
            }
            if (this.gameData != null && System.currentTimeMillis() < this.gameData.getPlayStartsAtMillis()) {
                throw new IllegalStateException("The game is about to start.");
            }

            if (!this.playerMap.containsKey(action.getPlayerId())) {
                throw new IllegalStateException("Invalid User");
            }
            if ("WAVELENGTH_START_NEXT_ROUND".equals(action.getType())
                    && !this.host.getId().equals(action.getPlayerId())) {
                throw new IllegalStateException("Only the room host can start the next Telepathy round.");
            }

            // process the move and update stats while holding the write lock
            this.gameLogic.processMove(action, this.gameData);

            PlayerStats stats = this.gameData.getScoreBoard().get(action.getPlayerId());
            if (stats != null) {
                this.gameLogic.updateStats(stats, this.gameData);
                long elapsedMillis = Math.max(0, System.currentTimeMillis() - this.gameData.getPlayStartsAtMillis());
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

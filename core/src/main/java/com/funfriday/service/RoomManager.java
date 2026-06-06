package com.funfriday.service;

import com.funfriday.factory.GameFactory;
import com.funfriday.model.GameAction;
import com.funfriday.model.GameData;
import com.funfriday.model.GameRoom;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages active rooms and per-room executors.
 * The cache is created in {@link #init()} so the removal listener can safely reference injected fields.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomManager {

    // Initialized in init() so removalListener can safely use timerManager and roomExecutors.
    private Cache<String, GameRoom> activeRooms;

    // Map of per-room single-thread executors
    private final Map<String, ExecutorService> roomExecutors = new ConcurrentHashMap<>();

    // Injected dependencies
    private final GameFactory gameFactory;
    private final TimerGameManager timerManager;

    private ExecutorService createRoomExecutor(String roomId) {
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("room-" + roomId);
            t.setDaemon(true);
            return t;
        };
        return Executors.newSingleThreadExecutor(tf);
    }

    @PostConstruct
    public void init() {
        this.activeRooms = Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .removalListener((String roomId, GameRoom room, RemovalCause cause) -> {
                    if (roomId == null) return;

                    // 1) Cancel scheduled timer for this room (no-op if none)
                    try {
                        timerManager.cancelTimer(roomId);
                    } catch (Exception e) {
                        log.warn("Error cancelling timer for room {}: {}", roomId, e.getMessage());
                    }

                    // 2) Remove and shut down the executor for that room
                    ExecutorService exec = roomExecutors.remove(roomId);
                    if (exec != null) {
                        exec.shutdown(); // allow running tasks to complete
                        try {
                            if (!exec.awaitTermination(2, TimeUnit.SECONDS)) {
                                exec.shutdownNow();
                            }
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            exec.shutdownNow();
                        }
                    }

                    log.debug("Room {} cleaned up due to {}", roomId, cause);
                })
                .build();
    }

    public GameRoom createRoom(GameFactory.GameType gameType, String playerId, String hostName) {
        String roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        GameLogic logic = gameFactory.createGame(gameType); // Use injected factory instance
        GameRoom room = new GameRoom(roomId, playerId, hostName, logic, gameType);
        activeRooms.put(roomId, room);
        roomExecutors.put(roomId, createRoomExecutor(roomId));
        return room;
    }

    /**
     * Retrieves an active room by its ID.
     */
    public GameRoom getRoom(String roomId) {
        return activeRooms.getIfPresent(roomId);
    }

    public CompletableFuture<GameRoom> submitPlayerAction(String roomId, GameAction action) {
        ExecutorService exec = roomExecutors.get(roomId);
        if (exec == null) {
            CompletableFuture<GameRoom> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("No executor for room: " + roomId));
            return failed;
        }

        CompletableFuture<GameRoom> cf = new CompletableFuture<>();
        exec.submit(() -> {
            try {
                GameRoom room = getRoom(roomId);
                if (room == null) {
                    cf.completeExceptionally(new IllegalStateException("Room not found: " + roomId));
                    return;
                }
                room.handlePlayerAction(action);
                cf.complete(room);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    public CompletableFuture<GameRoom> submitStartGame(String roomId, String requestingPlayerId, String gameMode, Map<String, Object> props) {
        ExecutorService exec = roomExecutors.get(roomId);
        if (exec == null) {
            CompletableFuture<GameRoom> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("No executor for room: " + roomId));
            return failed;
        }

        CompletableFuture<GameRoom> cf = new CompletableFuture<>();
        exec.submit(() -> {
            try {
                GameRoom room = getRoom(roomId);
                if (room == null) {
                    cf.completeExceptionally(new IllegalStateException("Room not found: " + roomId));
                    return;
                }

                room.startGame(requestingPlayerId, gameMode, props);

                // If TIME_ATTACK or other timed mode, schedule the timer
                GameData<?> gameData = room.getGameData();
                if (gameData != null && gameData.getEndTimeMillis() > 0) {
                    timerManager.scheduleGameTimer(roomId, room, exec, roomExecutors);
                }

                cf.complete(room);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    public void removeRoom(String roomId) {
        // Invalidate the cache entry -> triggers removalListener which does cleanup
        activeRooms.invalidate(roomId);

        // Also proactively clean executor & timers (in case caller wants immediate cleanup)
        ExecutorService exec = roomExecutors.remove(roomId);
        if (exec != null) {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(1, TimeUnit.SECONDS)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                exec.shutdownNow();
            }
        }
        timerManager.cancelTimer(roomId);
    }
}
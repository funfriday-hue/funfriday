package com.funfriday.service;

import com.funfriday.model.GameData;
import com.funfriday.model.GameRoom;
import com.funfriday.model.GameStatus;
import com.funfriday.model.PlayerStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimerGameManager {

    private final SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            createTimerThreadFactory()
    );

    private final Map<String, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();

    private ThreadFactory createTimerThreadFactory() {
        return r -> {
            Thread t = new Thread(r);
            t.setName("timer-manager");
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * Schedule a timer for TIME_ATTACK mode. Broadcasts room state every second
     * and finalizes the game when time expires.
     *
     * @param roomId           Room identifier
     * @param room             GameRoom instance
     * @param roomExecutor     Single-threaded executor for the room
     * @param roomExecutors    Map of all room executors (to check if room still exists)
     */
    public void scheduleGameTimer(String roomId, GameRoom room, ExecutorService roomExecutor, Map<String, ExecutorService> roomExecutors) {
        GameData<?> gameData = room.getGameData();

        if (gameData == null || gameData.getEndTimeMillis() <= 0) {
            log.warn("Timer not scheduled for room {} - endTimeMillis not set", roomId);
            return;
        }

        log.info("🕐 Scheduling timer for room {} - ends at {}", roomId, gameData.getEndTimeMillis());

        ScheduledFuture<?> sf = scheduler.scheduleAtFixedRate(() -> handleTimerTick(roomId, room, roomExecutor, roomExecutors), 0, 2, TimeUnit.SECONDS);

        ScheduledFuture<?> previous = roomTimers.put(roomId, sf);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    /**
     * Execute timer tick: broadcast state and finalize if time expired.
     * This is submitted to the scheduler thread pool; actual mutations must run on room executor.
     */
    private void handleTimerTick(String roomId, GameRoom room, ExecutorService roomExecutor, Map<String, ExecutorService> roomExecutors) {
        ExecutorService roomExec = roomExecutors.get(roomId);
        if (roomExec == null || roomExec.isShutdown()) {
            log.debug("Room {} executor not available, cancelling timer", roomId);
            cancelTimer(roomId);
            return;
        }

        roomExec.submit(() -> {
            try {
                GameData<?> d = room.getGameData();
                if (d == null) {
                    log.warn("GameData is null for room {}", roomId);
                    cancelTimer(roomId);
                    return;
                }

                long remaining = d.getRemainingSeconds();

                // Broadcast current state
//                messagingTemplate.convertAndSend("/topic/room/" + roomId, room);

                // Check if time expired
                if (remaining <= 0) {
                    finalizeGame(roomId, room, d);
                }
            } catch (Exception e) {
                log.error("Error in timer tick for room {}: {}", roomId, e.getMessage(), e);
            }
        });
    }

    /**
     * Finalize the game when time expires: mark finished, update stats, broadcast, cancel timer.
     */
    private void finalizeGame(String roomId, GameRoom room, GameData<?> gameData) {
        log.info("⏱ TIME_ATTACK expired for room {}", roomId);

        // Mark game as finished
        gameData.setFinished(true);
        room.setStatus(GameStatus.FINISHED);

        // Update stats for all players
        try {
            for (PlayerStats stats : gameData.getScoreBoard().values()) {
                if (stats != null) {
                    room.getGameLogic().updateStats(stats, gameData);
                }
            }
        } catch (Exception e) {
            log.error("Error updating stats for room {}: {}", roomId, e.getMessage(), e);
        }

        // Broadcast final state
        messagingTemplate.convertAndSend("/topic/room/" + roomId, room);

        // Cancel the timer
        cancelTimer(roomId);
    }

    /**
     * Cancel the scheduled timer for a room.
     */
    public void cancelTimer(String roomId) {
        ScheduledFuture<?> sf = roomTimers.remove(roomId);
        if (sf != null) {
            sf.cancel(false);
            log.debug("Timer cancelled for room {}", roomId);
        }
    }

    /**
     * Shutdown all timers and scheduler (call on application shutdown).
     */
    public void shutdown() {
        roomTimers.values().forEach(sf -> {
            if (sf != null) sf.cancel(false);
        });
        roomTimers.clear();
        scheduler.shutdown();
        log.info("TimerManager shutdown complete");
    }
}
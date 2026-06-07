package com.funfriday.service;

import com.funfriday.dto.RoomPublicView;
import com.funfriday.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Handles scheduling per-room timers (TIME_ATTACK mode).
 * The scheduler submits finalization and public broadcasts to the room's single-thread executor
 * to preserve ordering and thread-safety.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimerGameManager {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomViewFactory viewFactory;

    // Scheduler used to enqueue periodic ticks (runs independently from room executors)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r);
                t.setName("timer-manager");
                t.setDaemon(true);
                return t;
            }
    );

    // Maps roomId -> ScheduledFuture so we can cancel when needed
    private final Map<String, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();

    /**
     * Schedule a per-room timer: it will post periodic ticks that submit tasks on the provided roomExecutor.
     *
     * @param roomId        room id
     * @param room          current GameRoom instance (captures reference to be used on room executor)
     * @param roomExecutor  the single-thread executor that owns the room state
     * @param roomExecutors map of room executors (used to check if the room executor still exists)
     */
    public void scheduleGameTimer(String roomId, GameRoom room, ExecutorService roomExecutor, Map<String, ExecutorService> roomExecutors) {
        GameData<?> gd = room.getGameData();
        if (gd == null  || gd.getEndTimeMillis() <= 0) {
            log.warn("Not scheduling timer for room {} — no endTimeMillis set", roomId);
            return;
        }

        // If an existing timer is present, cancel it first
        ScheduledFuture<?> prev = roomTimers.remove(roomId);
        if (prev != null) prev.cancel(false);

        ScheduledFuture<?> sf = scheduler.scheduleAtFixedRate(() -> {
            // quick check: if the room executor is gone, cancel timer
            ExecutorService exec = roomExecutors.get(roomId);
            if (exec == null || exec.isShutdown()) {
                cancelTimer(roomId);
                return;
            }

            // Submit actual tick work onto the room's single-thread executor
            exec.submit(() -> {
                try {
                    GameData<?> data = room.getGameData();
                    if (data == null) {
                        cancelTimer(roomId);
                        return;
                    }

                    long remaining = data.getRemainingSeconds();

                    // Broadcast public view to all subscribers (no private fields)
//                    RoomPublicView publicView = viewFactory.buildPublicView(room);
//                    messagingTemplate.convertAndSend("/topic/room/" + roomId, publicView);

                    if (remaining <= 0) {
                        // Mark finished and compute winner (based on current stats) — do not call per-player updateStats
                        data.setFinished(true);
                        room.getGameData().getScoreBoard().keySet().forEach(pid -> {;
                            PlayerStats stats = data.getScoreBoard().get(pid);
                            stats.setStatus(PlayerStatus.COMPLETED);
                        });
                        room.setStatus(GameStatus.FINISHED);


                        // Compute winner deterministically from current scoreboard (ranking metric)
                        Optional<PlayerStats> winnerOpt = data.getScoreBoard().values().stream()
                                .filter(s -> s != null && s.getPlayer() != null)
                                .max((a, b) -> Double.compare(a.getRankingMetric(), b.getRankingMetric()));

                        winnerOpt.ifPresent(w -> data.setWinner(w.getPlayer()));

                        // Broadcast final public view
                        RoomPublicView finalView = viewFactory.buildPublicView(room);
                        messagingTemplate.convertAndSend("/topic/room/" + roomId, finalView);

                        // Cancel timer after finalization
                        cancelTimer(roomId);
                    }
                } catch (Throwable t) {
                    log.error("Error in timer tick for room {}: {}", roomId, t.getMessage(), t);
                }
            });

        }, 0, 1, TimeUnit.SECONDS); // 1-second ticks; adjust as needed

        roomTimers.put(roomId, sf);
        log.info("Scheduled TIME_ATTACK timer for room {}, ends at {}", roomId, gd.getEndTimeMillis());
    }

    /**
     * Cancel any scheduled timer for a room.
     */
    public void cancelTimer(String roomId) {
        ScheduledFuture<?> sf = roomTimers.remove(roomId);
        if (sf != null) {
            sf.cancel(false);
            log.debug("Cancelled timer for room {}", roomId);
        }
    }

    /**
     * Shutdown scheduler (call on application shutdown).
     */
    public void shutdown() {
        roomTimers.values().forEach(sf -> {
            if (sf != null) sf.cancel(false);
        });
        roomTimers.clear();
        scheduler.shutdown();
        log.info("TimerGameManager shutdown");
    }
}
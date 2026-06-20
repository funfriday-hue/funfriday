package com.funfriday.service;

import com.funfriday.dto.RoomPrivateView;
import com.funfriday.dto.RoomPublicView;
import com.funfriday.games.suduko.SudokuData;
import com.funfriday.games.suduko.SudokuPlayerStats;
import com.funfriday.games.wordle.WordleData;
import com.funfriday.games.wordle.WordlePlayerStats;
import com.funfriday.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class RoomViewFactory {

    // Build a view safe to broadcast to everyone
    public RoomPublicView buildPublicView(GameRoom room) {
        GameData<?> data = room.getGameData();
        Map<String, PlayerStats> scoreboard = data != null ? data.getScoreBoard() : Collections.emptyMap();

        // Build host info
        GamePlayer hostPlayer = room.getHost();
        PlayerStats hostStats = hostPlayer != null ? scoreboard.get(hostPlayer.getId()) : null;
        AtomicReference<RoomPublicView.PlayerPublic> host = new AtomicReference<>();

        List<RoomPublicView.PlayerPublic> players = room.getPlayerMap().values().stream()
                .map(p -> {
                    PlayerStats stats = scoreboard.get(p.getId());
                    int score = stats != null ? stats.getScore() : 0;
                    String status = stats != null ? (stats.getStatus() != null ? stats.getStatus().name() : "UNKNOWN") : "UNKNOWN";

                    Map<String, Object> statMap = new HashMap<>();

                    // Wordle-specific metrics
                    if (data instanceof WordleData wdata) {
                        // tries: prefer authoritative totalAttempts in PlayerStats if available
                        if (stats instanceof WordlePlayerStats wps) {
                            statMap.put("tries", wps.getTotalAttempts());
                            statMap.put("solved", wps.getStatus() == PlayerStatus.COMPLETED);
                            // time in seconds -> prefer timeInSeconds field
                            statMap.put("timeElapsedSeconds", wps.getTimeInSeconds());
                            statMap.put("currentWordAttempts", wps.getCurrentWordAttempts());
                        } else {
                            // Fallback: count per-player attempts list length (may reflect current word attempts)
                            int tries = wdata.getPlayerAttempts().getOrDefault(p.getId(), Collections.emptyList()).size();
                            statMap.put("tries", tries);
                            statMap.put("solved", false);
                            statMap.put("timeElapsedSeconds", 0L);
                        }
                    }
                    // Sudoku-specific metrics
                    else if (data instanceof SudokuData sdata) {
                        if (stats instanceof SudokuPlayerStats sps) {
                            statMap.put("percentSolved", sps.getProgress()); // double percentage
                            // time: prefer totalTimeMillis on SudokuPlayerStats if present, convert to seconds
                            long millis = sps.getTotalTimeMillis();
                            statMap.put("timeElapsedSeconds", millis / 1000);
                        } else {
                            statMap.put("percentSolved", 0.0);
                            statMap.put("timeElapsedSeconds", 0L);
                        }
                    } else {
                        // Generic fallback
                        if (stats != null) {
                            statMap.put("timeElapsedSeconds", stats.getTimeInSeconds());
                        } else {
                            statMap.put("timeElapsedSeconds", 0L);
                        }
                    }

                    if (p.isHost()) {
                        host.set(new RoomPublicView.PlayerPublic(
                                p.getId(),
                                p.getName(),
                                stats != null && stats.getStatus() != null ? stats.getStatus().name() : "ACTIVE",
                                stats != null ? stats.getScore() : 0,
                                statMap
                        ));
                    }

                    return new RoomPublicView.PlayerPublic(p.getId(), p.getName(), status, score, statMap);
                })
                .collect(Collectors.toList());
        Object publicGameData = null;
        if (data != null) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("finished", data.isFinished());
            meta.put("startTime", data.getStartTime());
            if (data.getEndTimeMillis() > 0) {
                meta.put("remainingSeconds", data.getRemainingSeconds());
            }
            publicGameData = meta;
        }

        return new RoomPublicView(room.getRoomId(), room.getStatus().name(), room.getGameData() == null ? null : room.getGameData().getGameConfiguration(), room.getType(), room.getStartTime(), host.get(), players, publicGameData);
    }

    // Build a private view for a specific playerId — includes only their sensitive info
    public RoomPrivateView buildPrivateView(GameRoom room, String playerId) {
        RoomPublicView publicView = buildPublicView(room);

        GameData<?> data = room.getGameData();
        PlayerStats selfStats = data != null ? data.getScoreBoard().get(playerId) : null;
        Map<String, Object> selfStatMap = new HashMap<>();

        if (data instanceof WordleData w) {
            if (selfStats instanceof WordlePlayerStats wps) {
                selfStatMap.put("tries", wps.getTotalAttempts());
                selfStatMap.put("solved", wps.getStatus() == PlayerStatus.COMPLETED);
                selfStatMap.put("timeElapsedSeconds", wps.getTimeInSeconds());
                selfStatMap.put("currentWordAttempts", wps.getCurrentWordAttempts());
            } else {
                selfStatMap.put("tries", w.getPlayerAttempts().getOrDefault(playerId, Collections.emptyList()).size());
                selfStatMap.put("solved", false);
                selfStatMap.put("timeElapsedSeconds", 0L);
            }
        } else if (data instanceof SudokuData s) {
            if (selfStats instanceof SudokuPlayerStats sps) {
                selfStatMap.put("percentSolved", sps.getProgress());
                selfStatMap.put("timeElapsedSeconds", sps.getTotalTimeMillis() / 1000);
            } else {
                selfStatMap.put("percentSolved", 0.0);
                selfStatMap.put("timeElapsedSeconds", 0L);
            }
        } else {
            if (selfStats != null) {
                selfStatMap.put("timeElapsedSeconds", selfStats.getTimeInSeconds());
            } else {
                selfStatMap.put("timeElapsedSeconds", 0L);
            }
        }

        RoomPublicView.PlayerPublic self = new RoomPublicView.PlayerPublic(
                playerId,
                selfStats != null && selfStats.getPlayer() != null ? selfStats.getPlayer().getName() : "You",
                selfStats != null && selfStats.getStatus() != null ? selfStats.getStatus().name() : "UNKNOWN",
                selfStats != null ? selfStats.getScore() : 0,
                selfStatMap
        );

        Map<String, Object> privateGameData = new HashMap<>();
        // Wordle example
        if (data instanceof WordleData w) {
            privateGameData.put("playerAttempts", w.getPlayerAttempts().getOrDefault(playerId, Collections.emptyList()));
            privateGameData.put("playerProgress", w.getPlayerProgress().getOrDefault(playerId, 0));
            if (selfStats != null && PlayerStatus.FAILED.equals(selfStats.getStatus())) {
                privateGameData.put("targetWord", w.getCurrentTargetForPlayer(playerId));
            }
        } else if (data instanceof SudokuData s) {
            privateGameData.put("playerBoard", s.getPlayerBoards().get(playerId));
            privateGameData.put("initialBoard", s.getInitialBoard());
        }

        Object publicGameData = publicView.getGameSpecificPublicData();

        return new RoomPrivateView(room.getRoomId(), room.getStatus().name(), room.getType(), room.getStartTime(),
                publicView.getHost(), self, publicGameData, privateGameData);
    }
}
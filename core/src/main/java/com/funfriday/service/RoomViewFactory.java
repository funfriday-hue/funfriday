package com.funfriday.service;

import com.funfriday.dto.RoomPrivateView;
import com.funfriday.dto.RoomPublicView;
import com.funfriday.games.dobble.DobbleData;
import com.funfriday.games.dobble.DobblePlayerStats;
import com.funfriday.games.quizroyale.QuizRoyaleData;
import com.funfriday.games.quizroyale.QuizRoyalePlayerStats;
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
                    // Dobble-specific metrics
                    else if (data instanceof DobbleData) {
                        if (stats instanceof DobblePlayerStats dps) {
                            statMap.put("matchesFound", dps.getMatchesFound());
                            statMap.put("totalResponseTimeMillis", dps.getTotalResponseTimeMillis());
                        }
                    }
                    else if (data instanceof QuizRoyaleData) {
                        if (stats instanceof QuizRoyalePlayerStats qps) {
                            statMap.put("strikes", qps.getStrikes());
                            statMap.put("correctAnswers", qps.getCorrectAnswers());
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
                                statMap,
                                p.isConnected()
                        ));
                    }

                    return new RoomPublicView.PlayerPublic(p.getId(), p.getName(), status, score, statMap, p.isConnected());
                })
                .collect(Collectors.toList());
        Object publicGameData = null;
        if (data != null) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("finished", data.isFinished());
            meta.put("startTime", data.getStartTime());
            meta.put("playStartsAtMillis", data.getPlayStartsAtMillis());
            if (data.getEndTimeMillis() > 0) {
                meta.put("remainingSeconds", data.getRemainingSeconds());
            }
            if (data instanceof DobbleData d) {
                meta.put("gameMode", d.getGameMode().name());
                meta.put("lastMatchPlayerId", d.getLastMatchPlayerId());
                meta.put("lastMatchSymbol", d.getLastMatchSymbol());
                if (d.getGameMode().name().equals("CLASSIC")) {
                    meta.put("centerCard", d.getCenterCard());
                    meta.put("currentCard", d.getCurrentCard());
                    meta.put("currentCardIndex", d.getCurrentCardIndex());
                    meta.put("totalCards", d.getOuterCards().size());
                    meta.put("currentRound", (d.getCurrentCardIndex() / 5) + 1);
                    meta.put("cardsPerRound", 5);
                    meta.put("currentCardSolved", d.isCurrentCardSolved());
                } else if (d.getGameMode().name().equals("PAIR_RUSH")) {
                    int start = d.getCurrentRoundIndex() * 2;
                    meta.put("currentRound", d.getCurrentRoundIndex() + 1);
                    meta.put("totalRounds", 25);
                    meta.put("leftCard", start < d.getPairCards().size() ? d.getPairCards().get(start) : null);
                    meta.put("rightCard", start + 1 < d.getPairCards().size() ? d.getPairCards().get(start + 1) : null);
                } else {
                    meta.put("currentRound", d.getCurrentRoundIndex() + 1);
                    meta.put("totalRounds", 12);
                    meta.put("visibleCards", d.getTripleBoard());
                }
            }
            if (data instanceof QuizRoyaleData q) {
                meta.put("question", q.getQuestion().getPrompt());
                meta.put("category", q.getQuestion().getCategory().name());
                meta.put("questionType", q.getQuestion().getType().name());
                meta.put("questionNumber", q.getQuestionIndex() + 1);
                meta.put("questionCount", q.getQuestions().size());
                meta.put("questionActive", q.isQuestionActive());
                meta.put("questionTransitionEndsAtMillis", q.getQuestionTransitionEndsAtMillis());
                meta.put("playMode", q.getGameConfiguration().getPlayMode().name());
                meta.put("acceptedAnswers", q.getAcceptedAnswers());
                if (q.getQuestion().getType().name().equals("LIST")) {
                    meta.put("totalAnswerCount", q.getQuestion().getAnswers().size());
                }
                meta.put("currentPlayerId", q.getTurnOrder().isEmpty() ? null : q.getTurnOrder().get(q.getCurrentPlayerIndex()));
                meta.put("timeoutCoordinatorId", q.getTurnOrder().stream()
                        .filter(id -> q.getScoreBoard().get(id).getStatus() == PlayerStatus.ACTIVE)
                        .findFirst().orElse(null));
                meta.put("allPlayAnsweredPlayerIds", q.getAllPlayAnsweredPlayerIds());
                meta.put("chronologyPassedPlayerIds", q.getChronologyPassedPlayerIds());
                meta.put("turnStartedAtMillis", q.getTurnStartedAtMillis());
                meta.put("turnSeconds", q.getGameConfiguration().getTurnSeconds());
                meta.put("strikeLimit", q.getGameConfiguration().getStrikeLimit());
                meta.put("lastEvent", q.getLastEvent());
                if (q.isFinished()) {
                    List<Map<String, Object>> questionResults = q.getQuestionResults().stream().map(result -> {
                        Map<String, Object> resultView = new LinkedHashMap<>();
                        resultView.put("questionNumber", result.getQuestionNumber());
                        resultView.put("prompt", result.getPrompt());
                        resultView.put("category", result.getCategory().name());
                        resultView.put("questionType", result.getQuestionType().name());
                        resultView.put("answers", result.getAnswers());
                        resultView.put("hints", result.getHints());
                        resultView.put("answeredAnswerIndexes", result.getAnsweredAnswerIndexes());
                        return resultView;
                    }).toList();
                    // A room begun before this feature may not have a snapshot yet; always retain a review of its final question.
                    if (questionResults.isEmpty()) {
                        Map<String, Object> finalQuestion = new LinkedHashMap<>();
                        finalQuestion.put("questionNumber", q.getQuestionIndex() + 1);
                        finalQuestion.put("prompt", q.getQuestion().getPrompt());
                        finalQuestion.put("category", q.getQuestion().getCategory().name());
                        finalQuestion.put("questionType", q.getQuestion().getType().name());
                        finalQuestion.put("answers", q.getQuestion().getAnswers().stream().map(answer -> answer.getValue()).toList());
                        finalQuestion.put("hints", q.getQuestion().getChronologyHints());
                        finalQuestion.put("answeredAnswerIndexes", q.getAnsweredAnswerIndexes());
                        questionResults = List.of(finalQuestion);
                    }
                    meta.put("questionResults", questionResults);
                    // Retained briefly for clients running the previous UI build during a rolling deploy.
                    meta.put("allAnswers", q.getQuestion().getAnswers().stream().map(answer -> answer.getValue()).toList());
                    meta.put("allAnswerHints", q.getQuestion().getChronologyHints());
                    meta.put("answeredAnswerIndexes", q.getAnsweredAnswerIndexes());
                }
                if (q.getQuestion().getType().name().equals("CHRONOLOGY") && q.getChronologyIndex() < q.getQuestion().getChronologyHints().size()) {
                    meta.put("chronologyHint", q.getQuestion().getChronologyHints().get(q.getChronologyIndex()));
                }
            }
            publicGameData = meta;
        }

        return new RoomPublicView(room.getRoomId(), room.getStatus().name(), room.getGameData() == null ? null : room.getGameData().getGameConfiguration(), room.getType(), room.getInitialGameMode(), room.getStartTime(), host.get(), players, publicGameData);
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
        } else if (data instanceof DobbleData) {
            if (selfStats instanceof DobblePlayerStats dps) {
                selfStatMap.put("matchesFound", dps.getMatchesFound());
                selfStatMap.put("totalResponseTimeMillis", dps.getTotalResponseTimeMillis());
            }
        } else if (data instanceof QuizRoyaleData) {
            if (selfStats instanceof QuizRoyalePlayerStats qps) {
                selfStatMap.put("strikes", qps.getStrikes());
                selfStatMap.put("correctAnswers", qps.getCorrectAnswers());
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
                selfStatMap,
                room.getPlayerMap().containsKey(playerId) && room.getPlayerMap().get(playerId).isConnected()
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
            if (selfStats != null && (PlayerStatus.GIVEN_UP.equals(selfStats.getStatus()) || PlayerStatus.COMPLETED.equals(selfStats.getStatus()))) {
                privateGameData.put("targetBoard", s.getSolution());
            }
        }

        Object publicGameData = publicView.getGameSpecificPublicData();

        return new RoomPrivateView(room.getRoomId(), room.getStatus().name(), room.getType(), room.getStartTime(),
                publicView.getHost(), self, publicGameData, privateGameData);
    }
}

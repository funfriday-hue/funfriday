package com.funfriday.service;

import com.funfriday.db.dao.PlayerCrosswordProgressDao;
import com.funfriday.games.crossword.*;
import com.funfriday.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerCrosswordProgressService {
  private final PlayerCrosswordProgressDao progressDao;

  public void recordCurrentProgress(GameRoom room, String playerId) {
    if (!(room.getGameData() instanceof CrosswordData data) || playerId == null) return;
    PlayerStats raw = data.getScoreBoard().get(playerId);
    if (!(raw instanceof CrosswordPlayerStats stats)) return;
    try {
      progressDao.recordProgress(
          playerId,
          data.getPuzzleId(),
          (int) Math.round(stats.getProgress()),
          stats.getStatus() == PlayerStatus.COMPLETED);
    } catch (Exception e) {
      log.warn(
          "Unable to save crossword progress for player {} and puzzle {}",
          playerId,
          data.getPuzzleId(),
          e);
    }
  }
}

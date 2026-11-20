package com.funfriday.service;

import com.funfriday.db.dao.PlayerSudokuProgressDao;
import com.funfriday.games.suduko.SudokuData;
import com.funfriday.games.suduko.SudokuPlayerStats;
import com.funfriday.model.GameRoom;
import com.funfriday.model.PlayerStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Saves only the best aggregate Sudoku completion percentage for the board a player is solving. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerSudokuProgressService {
    private final PlayerSudokuProgressDao progressDao;

    public void recordCurrentProgress(GameRoom room, String playerId) {
        if (!(room.getGameData() instanceof SudokuData data) || data.getPuzzleId() <= 0 || playerId == null) return;
        PlayerStats rawStats = data.getScoreBoard().get(playerId);
        if (!(rawStats instanceof SudokuPlayerStats stats)) return;
        int progress = (int) Math.round(stats.getProgress());
        try {
            progressDao.recordProgress(playerId, data.getPuzzleId(), progress, "COMPLETED".equals(stats.getStatus().name()));
        } catch (Exception exception) {
            log.warn("Unable to save Sudoku progress for player {} and puzzle {}", playerId, data.getPuzzleId(), exception);
        }
    }
}

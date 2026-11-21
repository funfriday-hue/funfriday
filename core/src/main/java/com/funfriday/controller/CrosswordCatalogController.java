package com.funfriday.controller;

import com.funfriday.db.dao.CrosswordDao;
import com.funfriday.db.dao.PlayerCrosswordProgressDao;
import com.funfriday.db.model.CrosswordPuzzleSummary;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalogue metadata only; answers and grids remain private to an active game room. */
@RestController
@RequestMapping("/api/crosswords")
@CrossOrigin(
    origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"},
    allowCredentials = "true")
@RequiredArgsConstructor
public class CrosswordCatalogController {
  private final CrosswordDao crosswordDao;
  private final PlayerCrosswordProgressDao progressDao;

  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(name = "playerId", required = false) String playerId) {
    try {
      List<CrosswordPuzzleSummary> puzzles = crosswordDao.listActive();
      Map<Long, ?> progress =
          progressDao.findByPlayerAndPuzzleIds(
              playerId, puzzles.stream().map(CrosswordPuzzleSummary::id).toList());
      return ResponseEntity.ok(Map.of("puzzles", puzzles, "progress", progress));
    } catch (Exception exception) {
      return ResponseEntity.internalServerError().body("Unable to load crosswords");
    }
  }
}

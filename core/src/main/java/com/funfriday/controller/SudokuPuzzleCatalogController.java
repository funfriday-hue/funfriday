package com.funfriday.controller;

import com.funfriday.db.dao.PlayerSudokuProgressDao;
import com.funfriday.db.dao.SudokuDao;
import com.funfriday.db.model.SudokuPuzzleProgress;
import com.funfriday.db.model.SudokuPuzzleSummary;
import com.funfriday.service.FunFridayJwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Paginated, answer-free Sudoku catalogue. Puzzle grids and solutions remain game-server-only. */
@RestController
@RequestMapping("/api/sudoku-puzzles")
@CrossOrigin(origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"}, allowCredentials = "true")
@RequiredArgsConstructor
public class SudokuPuzzleCatalogController {
    private static final int GUEST_VISIBLE_PUZZLES = 5;
    private static final Set<String> PROGRESS_FILTERS = Set.of("ALL", "COMPLETED", "PARTIAL", "NOT_SOLVED");
    private final SudokuDao sudokuDao;
    private final PlayerSudokuProgressDao progressDao;
    private final FunFridayJwtService jwtService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(name = "size") int size,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "8") int pageSize,
            @RequestParam(name = "progress", defaultValue = "ALL") String progressFilter,
            @CookieValue(name = "playerId", required = false) String browserPlayerId,
            HttpServletRequest request) {
        if (size != 6 && size != 9) return ResponseEntity.badRequest().body("Sudoku size must be 6 or 9.");
        String normalizedProgress = progressFilter.toUpperCase(Locale.ROOT);
        if (!PROGRESS_FILTERS.contains(normalizedProgress)) {
            return ResponseEntity.badRequest().body("Unknown Sudoku progress filter.");
        }
        try {
            var authenticatedPlayer = jwtService.currentUser(request);
            boolean signedIn = authenticatedPlayer.isPresent();
            // Guests see the first five boards only. Ignore a supplied page so
            // pagination cannot be used to bypass the sign-in catalogue limit.
            String playerId = authenticatedPlayer.map(FunFridayJwtService.PlayerPrincipal::playerId).orElse(browserPlayerId);
            SudokuDao.Page result;
            if (signedIn && playerId != null) {
                result = sudokuDao.listPageBySizeAndProgress(size, page, pageSize, playerId, normalizedProgress);
            } else if ("COMPLETED".equals(normalizedProgress) || "PARTIAL".equals(normalizedProgress)) {
                result = new SudokuDao.Page(List.of(), 0, 0, Math.max(1, Math.min(24, pageSize)));
            } else {
                result = sudokuDao.listPageBySize(size, 0, GUEST_VISIBLE_PUZZLES);
            }
            List<Integer> puzzleIds = result.puzzles().stream().map(puzzle -> puzzle.getId()).toList();
            Map<Integer, SudokuPuzzleProgress> progress = signedIn && playerId != null
                    ? progressDao.findByPlayerAndPuzzleIds(playerId, puzzleIds)
                    : Map.of();
            List<SudokuPuzzleSummary> puzzles = result.puzzles().stream()
                    .map(puzzle -> new SudokuPuzzleSummary(puzzle.getId(), puzzle.getSize(), progress.get(puzzle.getId())))
                    .toList();
            int lockedPuzzleCount = signedIn ? 0 : Math.max(0, result.total() - puzzles.size());
            return ResponseEntity.ok(new SudokuPuzzleCatalogResponse(
                    puzzles, result.total(), result.page(), signedIn ? result.pageSize() : pageSize,
                    lockedPuzzleCount, signedIn
            ));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body("Unable to load Sudoku puzzles");
        }
    }

    public record SudokuPuzzleCatalogResponse(List<SudokuPuzzleSummary> puzzles, int total, int page, int pageSize,
                                              int lockedPuzzleCount, boolean signedIn) { }
}

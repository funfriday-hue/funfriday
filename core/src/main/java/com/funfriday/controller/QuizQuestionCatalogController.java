package com.funfriday.controller;

import com.funfriday.db.dao.QuizQuestionDao;
import com.funfriday.db.dao.PlayerQuestionProgressDao;
import com.funfriday.db.model.PlayerQuestionProgress;
import com.funfriday.service.FunFridayJwtService;
import jakarta.servlet.http.HttpServletRequest;
import com.funfriday.db.model.QuizQuestionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;

import java.util.List;
import java.util.Set;
import java.util.Map;

@RestController
@RequestMapping("/api/quiz-questions")
@CrossOrigin(origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"}, allowCredentials = "true")
@RequiredArgsConstructor
public class QuizQuestionCatalogController {
    private static final Set<String> CATEGORIES = Set.of("CRICKET", "FOOTBALL", "BOLLYWOOD", "WWE", "INDIA");
    private final QuizQuestionDao quizQuestionDao;
    private final PlayerQuestionProgressDao playerQuestionProgressDao;
    private final FunFridayJwtService jwtService;

    @GetMapping("/{category}")
    public ResponseEntity<?> listByCategory(
            @PathVariable("category") String category,
            @CookieValue(name = "playerId", required = false) String browserPlayerId,
            HttpServletRequest request) {
        String normalizedCategory = category.toUpperCase(java.util.Locale.ROOT);
        if (!CATEGORIES.contains(normalizedCategory)) return ResponseEntity.badRequest().body("Unknown quiz category");
        try {
            String playerId = jwtService.currentUser(request).map(FunFridayJwtService.PlayerPrincipal::playerId).orElse(browserPlayerId);
            Map<String, PlayerQuestionProgress> progress = playerId == null ? Map.of()
                    : playerQuestionProgressDao.findByPlayerAndCategory(playerId, normalizedCategory);
            List<QuizQuestionSummary> questions = quizQuestionDao.listActiveSummariesByCategory(normalizedCategory, progress);
            return ResponseEntity.ok(questions);
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body("Unable to load questions");
        }
    }
}

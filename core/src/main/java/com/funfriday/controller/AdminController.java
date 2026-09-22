package com.funfriday.controller;

import com.funfriday.db.dao.QuizDraftDao;
import com.funfriday.db.model.QuizDraftAnswerRecord;
import com.funfriday.db.model.QuizQuestionDraftRecord;
import com.funfriday.service.AdminAuthService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"}, allowCredentials = "true")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    private final AdminAuthService adminAuthService;
    private final QuizDraftDao quizDraftDao;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String token = adminAuthService.login(request.password());
            return token == null
                    ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid password."))
                    : ResponseEntity.ok(Map.of("token", token, "expiresAt", Instant.now().plusSeconds(12 * 60 * 60).toString()));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unable to sign in."));
        }
    }

    @GetMapping("/drafts")
    public ResponseEntity<?> drafts(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @RequestParam(name = "status", defaultValue = "DRAFT") String status) {
        if (!adminAuthService.isAuthorized(authorization)) return unauthorized();
        try {
            return ResponseEntity.ok(quizDraftDao.listDrafts(status.toUpperCase()));
        } catch (Exception exception) {
            log.error("Unable to load Quiz Royale drafts", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unable to load drafts."));
        }
    }

    @GetMapping("/questions")
    public ResponseEntity<?> activeQuestions(@RequestHeader(name = "Authorization", required = false) String authorization) {
        if (!adminAuthService.isAuthorized(authorization)) return unauthorized();
        try {
            return ResponseEntity.ok(quizDraftDao.listActiveQuestions());
        } catch (Exception exception) {
            log.error("Unable to load active Quiz Royale questions", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unable to load active questions."));
        }
    }

    @PostMapping("/drafts/{draftId}/approve")
    public ResponseEntity<?> approve(@RequestHeader(name = "Authorization", required = false) String authorization,
                                     @PathVariable(name = "draftId") long draftId) {
        if (!adminAuthService.isAuthorized(authorization)) return unauthorized();
        try {
            return quizDraftDao.approve(draftId)
                    ? ResponseEntity.ok(Map.of("status", "APPROVED"))
                    : ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Draft is no longer awaiting review."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unable to approve draft."));
        }
    }

    @PutMapping("/drafts/{draftId}")
    public ResponseEntity<?> updateDraft(@RequestHeader(name = "Authorization", required = false) String authorization,
                                         @PathVariable(name = "draftId") long draftId,
                                         @RequestBody UpdateDraftRequest request) {
        if (!adminAuthService.isAuthorized(authorization)) return unauthorized();
        try {
            return quizDraftDao.updateDraft(draftId, request.prompt(), toAnswerRecords(request))
                    ? ResponseEntity.ok(Map.of("status", "SAVED"))
                    : ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Draft is no longer awaiting review."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (Exception exception) {
            log.error("Unable to update Quiz Royale draft {}", draftId, exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unable to save draft."));
        }
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<?> updateActiveQuestion(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                  @PathVariable(name = "questionId") long questionId,
                                                  @RequestBody UpdateDraftRequest request) {
        if (!adminAuthService.isAuthorized(authorization)) return unauthorized();
        try {
            return quizDraftDao.updateActiveQuestion(questionId, request.prompt(), toAnswerRecords(request))
                    ? ResponseEntity.ok(Map.of("status", "SAVED"))
                    : ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Question is no longer active."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (Exception exception) {
            log.error("Unable to update active Quiz Royale question {}", questionId, exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unable to save question."));
        }
    }

    @PostMapping("/drafts/{draftId}/decline")
    public ResponseEntity<?> decline(@RequestHeader(name = "Authorization", required = false) String authorization,
                                     @PathVariable(name = "draftId") long draftId) {
        if (!adminAuthService.isAuthorized(authorization)) return unauthorized();
        try {
            return quizDraftDao.decline(draftId)
                    ? ResponseEntity.ok(Map.of("status", "DECLINED"))
                    : ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Draft is no longer awaiting review."));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unable to decline draft."));
        }
    }

    @PostMapping("/passwords")
    public ResponseEntity<?> addPassword(@RequestHeader(name = "Authorization", required = false) String authorization,
                                         @RequestBody AddPasswordRequest request) {
        if (!adminAuthService.isAuthorized(authorization)) return unauthorized();
        try {
            adminAuthService.addPassword(request.label(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "CREATED"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unable to add password."));
        }
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Admin authentication is required."));
    }

    private List<QuizDraftAnswerRecord> toAnswerRecords(UpdateDraftRequest request) {
        return request.answers() == null ? List.of() : request.answers().stream()
                .map(answer -> new QuizDraftAnswerRecord(answer.id(), answer.canonicalAnswer(), answer.displayOrder(), answer.hint(), answer.aliases()))
                .toList();
    }

    private record LoginRequest(String password) { }
    private record AddPasswordRequest(String label, String password) { }
    private record UpdateDraftRequest(String prompt, List<UpdateDraftAnswerRequest> answers) { }
    private record UpdateDraftAnswerRequest(long id, String canonicalAnswer, int displayOrder, String hint, List<String> aliases) { }
}

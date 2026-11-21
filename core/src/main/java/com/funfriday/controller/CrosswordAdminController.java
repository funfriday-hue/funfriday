package com.funfriday.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funfriday.db.dao.CrosswordDraftDao;
import com.funfriday.service.AdminAuthService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/crossword")
@CrossOrigin(
    origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"},
    allowCredentials = "true")
@RequiredArgsConstructor
public class CrosswordAdminController {
  private final AdminAuthService auth;
  private final CrosswordDraftDao dao;
  private final ObjectMapper json;

  @GetMapping("/drafts")
  public ResponseEntity<?> drafts(
      @RequestHeader(name = "Authorization", required = false) String h,
      @RequestParam(name = "status", defaultValue = "DRAFT") String status)
      throws Exception {
    return allowed(h) ? ResponseEntity.ok(dao.list(status.toUpperCase())) : unauthorized();
  }

  @GetMapping("/puzzles")
  public ResponseEntity<?> puzzles(
      @RequestHeader(name = "Authorization", required = false) String h) throws Exception {
    return allowed(h) ? ResponseEntity.ok(dao.listPublished()) : unauthorized();
  }

  @PostMapping("/drafts/{id}/approve")
  public ResponseEntity<?> approve(
      @RequestHeader(name = "Authorization", required = false) String h,
      @PathVariable(name = "id") long id,
      @RequestBody Map<String, Object> body)
      throws Exception {
    if (!allowed(h)) return unauthorized();
    String raw = String.valueOf(body.getOrDefault("entriesJson", "[]"));
    return dao.approve(id, json.readValue(raw, new TypeReference<List<Map<String, Object>>>() {}))
        ? ResponseEntity.ok(Map.of("status", "APPROVED"))
        : ResponseEntity.status(409).body(Map.of("message", "Draft unavailable"));
  }

  @PutMapping("/drafts/{id}")
  public ResponseEntity<?> update(
      @RequestHeader(name = "Authorization", required = false) String h,
      @PathVariable(name = "id") long id,
      @RequestBody Map<String, String> b)
      throws Exception {
    if (!allowed(h)) return unauthorized();
    return dao.update(id, b.get("title"), b.get("gridJson"), b.get("entriesJson"))
        ? ResponseEntity.ok(Map.of("status", "SAVED"))
        : ResponseEntity.status(409).body(Map.of("message", "Draft unavailable"));
  }

  @PutMapping("/puzzles/{id}")
  public ResponseEntity<?> updatePuzzle(
      @RequestHeader(name = "Authorization", required = false) String h,
      @PathVariable(name = "id") long id,
      @RequestBody Map<String, String> b)
      throws Exception {
    if (!allowed(h)) return unauthorized();
    List<Map<String, Object>> entries =
        b.get("entriesJson") == null
            ? null
            : json.readValue(
                b.get("entriesJson"), new TypeReference<List<Map<String, Object>>>() {});
    return dao.updatePublished(id, b.get("title"), b.get("gridJson"), entries)
        ? ResponseEntity.ok(Map.of("status", "SAVED"))
        : ResponseEntity.status(409).body(Map.of("message", "Puzzle unavailable"));
  }

  @DeleteMapping("/puzzles/{id}")
  public ResponseEntity<?> deletePuzzle(
      @RequestHeader(name = "Authorization", required = false) String h,
      @PathVariable(name = "id") long id)
      throws Exception {
    if (!allowed(h)) return unauthorized();
    return dao.deletePublished(id)
        ? ResponseEntity.ok(Map.of("status", "DELETED"))
        : ResponseEntity.status(409).body(Map.of("message", "Puzzle is no longer available."));
  }

  @PostMapping("/drafts/{id}/decline")
  public ResponseEntity<?> decline(
      @RequestHeader(name = "Authorization", required = false) String h,
      @PathVariable(name = "id") long id,
      @RequestBody Map<String, String> b)
      throws Exception {
    if (!allowed(h)) return unauthorized();
    return dao.decline(id, b.getOrDefault("reason", "Declined"))
        ? ResponseEntity.ok(Map.of("status", "DECLINED"))
        : ResponseEntity.status(409).body(Map.of("message", "Draft unavailable"));
  }

  private boolean allowed(String h) {
    return auth.isAuthorized(h);
  }

  private ResponseEntity<?> unauthorized() {
    return ResponseEntity.status(401).body(Map.of("message", "Admin authentication is required."));
  }
}

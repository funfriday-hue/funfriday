package com.funfriday.controller;

import com.funfriday.db.dao.WavelengthCardDraftDao;
import com.funfriday.service.AdminAuthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/wavelength")
@CrossOrigin(origins={"http://localhost:3000","http://funfriday.co.in","https://funfriday.co.in"},allowCredentials="true") @RequiredArgsConstructor
public class WavelengthAdminController {
  private final AdminAuthService auth; private final WavelengthCardDraftDao cards;
  @GetMapping("/drafts") public ResponseEntity<?> drafts(@RequestHeader(name="Authorization",required=false) String h) throws Exception { return allowed(h) ? ResponseEntity.ok(cards.drafts()) : unauthorized(); }
  @GetMapping("/cards") public ResponseEntity<?> published(@RequestHeader(name="Authorization",required=false) String h) throws Exception { return allowed(h) ? ResponseEntity.ok(cards.published()) : unauthorized(); }
  @PostMapping("/drafts/{id}/approve") public ResponseEntity<?> approve(@RequestHeader(name="Authorization",required=false) String h,@PathVariable long id) throws Exception { return !allowed(h) ? unauthorized() : cards.approve(id) ? ResponseEntity.ok(Map.of("status","APPROVED")) : ResponseEntity.status(409).body(Map.of("message","Draft unavailable")); }
  @PostMapping("/drafts/{id}/decline") public ResponseEntity<?> decline(@RequestHeader(name="Authorization",required=false) String h,@PathVariable long id,@RequestBody(required=false) Map<String,String> body) throws Exception { return !allowed(h) ? unauthorized() : cards.decline(id, body == null ? "Declined" : body.getOrDefault("reason", "Declined")) ? ResponseEntity.ok(Map.of("status","DECLINED")) : ResponseEntity.status(409).body(Map.of("message","Draft unavailable")); }
  @DeleteMapping("/cards/{id}") public ResponseEntity<?> delete(@RequestHeader(name="Authorization",required=false) String h,@PathVariable long id) throws Exception { return !allowed(h) ? unauthorized() : cards.deletePublished(id) ? ResponseEntity.ok(Map.of("status","DELETED")) : ResponseEntity.status(409).body(Map.of("message","Card unavailable")); }
  private boolean allowed(String header) { return auth.isAuthorized(header); }
  private ResponseEntity<?> unauthorized() { return ResponseEntity.status(401).body(Map.of("message", "Admin authentication is required.")); }
}

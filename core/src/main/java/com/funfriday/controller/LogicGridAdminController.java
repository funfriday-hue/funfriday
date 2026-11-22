package com.funfriday.controller;

import com.funfriday.db.dao.LogicGridDraftDao;
import com.funfriday.service.AdminAuthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/logic-grid")
@CrossOrigin(origins={"http://localhost:3000","http://funfriday.co.in","https://funfriday.co.in"},allowCredentials="true") @RequiredArgsConstructor
public class LogicGridAdminController {
  private final AdminAuthService auth; private final LogicGridDraftDao dao;
  @GetMapping("/drafts") public ResponseEntity<?> drafts(@RequestHeader(name="Authorization",required=false)String h)throws Exception{return allowed(h)?ResponseEntity.ok(dao.drafts("DRAFT")):unauthorized();}
  @GetMapping("/puzzles") public ResponseEntity<?> puzzles(@RequestHeader(name="Authorization",required=false)String h)throws Exception{return allowed(h)?ResponseEntity.ok(dao.published()):unauthorized();}
  @PostMapping("/drafts/{id}/approve") public ResponseEntity<?> approve(@RequestHeader(name="Authorization",required=false)String h,@PathVariable long id)throws Exception{return !allowed(h)?unauthorized():dao.approve(id)?ResponseEntity.ok(Map.of("status","APPROVED")):ResponseEntity.status(409).body(Map.of("message","Draft unavailable"));}
  @PostMapping("/drafts/{id}/decline") public ResponseEntity<?> decline(@RequestHeader(name="Authorization",required=false)String h,@PathVariable long id,@RequestBody(required=false)Map<String,String>b)throws Exception{return !allowed(h)?unauthorized():dao.decline(id,b==null?"Declined":b.getOrDefault("reason","Declined"))?ResponseEntity.ok(Map.of("status","DECLINED")):ResponseEntity.status(409).body(Map.of("message","Draft unavailable"));}
  @PutMapping("/drafts/{id}") public ResponseEntity<?> updateDraft(@RequestHeader(name="Authorization",required=false)String h,@PathVariable long id,@RequestBody Map<String,String>b)throws Exception{return !allowed(h)?unauthorized():dao.updateDraft(id,b)?ResponseEntity.ok(Map.of("status","SAVED")):ResponseEntity.status(409).body(Map.of("message","Draft unavailable"));}
  @PutMapping("/puzzles/{id}") public ResponseEntity<?> updatePuzzle(@RequestHeader(name="Authorization",required=false)String h,@PathVariable long id,@RequestBody Map<String,String>b)throws Exception{return !allowed(h)?unauthorized():dao.updatePublished(id,b)?ResponseEntity.ok(Map.of("status","SAVED")):ResponseEntity.status(409).body(Map.of("message","Puzzle unavailable"));}
  @DeleteMapping("/puzzles/{id}") public ResponseEntity<?> delete(@RequestHeader(name="Authorization",required=false)String h,@PathVariable long id)throws Exception{return !allowed(h)?unauthorized():dao.deletePublished(id)?ResponseEntity.ok(Map.of("status","DELETED")):ResponseEntity.status(409).body(Map.of("message","Puzzle unavailable"));}
  private boolean allowed(String h){return auth.isAuthorized(h);} private ResponseEntity<?> unauthorized(){return ResponseEntity.status(401).body(Map.of("message","Admin authentication is required."));}
}

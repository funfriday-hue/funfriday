package com.funfriday.controller;

import com.funfriday.model.GamePlayer;
import com.funfriday.model.GameRoom;
import com.funfriday.request.CreateRoomRequest;
import com.funfriday.request.JoinRequest;
import com.funfriday.service.RoomManager;
import com.funfriday.service.FunFridayJwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = {"http://localhost:3000", "http://funfriday.co.in", "https://funfriday.co.in"}, allowCredentials = "true")
@RequiredArgsConstructor
public class RoomController {

    private final RoomManager roomManager;
    private final FunFridayJwtService jwtService;

    @PostMapping("/create")
    public ResponseEntity<GameRoom> createRoom(
            @CookieValue(name = "playerId", required = false) String playerId,
            @RequestBody CreateRoomRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        // Validation check
        if (request.getType() == null || request.getHost() == null) {
            return ResponseEntity.badRequest().build();
        }

        FunFridayJwtService.PlayerPrincipal authenticated = jwtService.currentUser(httpRequest).orElse(null);
        String effectivePlayerId = authenticated == null ? playerId : authenticated.playerId();
        String effectiveName = authenticated == null ? request.getHost() : authenticated.name();
        GameRoom room = roomManager.createRoom(request.getType(), effectivePlayerId, effectiveName, request.getGameMode());

        // 3. Attach Cookie to Response
        Cookie cookie = new Cookie("playerId", room.getHost().getId());
        cookie.setHttpOnly(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        response.addCookie(cookie);


        return ResponseEntity.ok(room);
    }

    @PostMapping("/join/{roomId}")
    public ResponseEntity<?> joinRoom(
            @PathVariable(name = "roomId") String roomId,
            @CookieValue(name = "playerId", required = false) String playerId,
            @RequestBody JoinRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        // 1. Check if room exists
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found");
        }

        FunFridayJwtService.PlayerPrincipal authenticated = jwtService.currentUser(httpRequest).orElse(null);
        String effectivePlayerId = authenticated == null ? playerId : authenticated.playerId();
        String effectiveName = authenticated == null ? request.getPlayerName() : authenticated.name();
        GamePlayer player = room.addPlayer(effectivePlayerId, effectiveName, false);

        // 3. Attach Cookie to Response
        Cookie cookie = new Cookie("playerId", player.getId());
        cookie.setHttpOnly(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        response.addCookie(cookie);

        // 4. Return success
        return ResponseEntity.ok().body(Map.of("status", "success", "roomId", roomId));
    }

}

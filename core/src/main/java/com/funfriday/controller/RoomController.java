package com.funfriday.controller;

import com.funfriday.model.GamePlayer;
import com.funfriday.model.GameRoom;
import com.funfriday.request.CreateRoomRequest;
import com.funfriday.request.JoinRequest;
import com.funfriday.service.RoomManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class RoomController {

    @Autowired
    private RoomManager roomManager;

    @PostMapping("/create")
    public ResponseEntity<GameRoom> createRoom(@CookieValue(name = "playerId", required = false) String playerId,
                                                @RequestBody CreateRoomRequest request,
                                               HttpServletResponse response) {
        // Validation check
        if (request.getType() == null || request.getHost() == null) {
            return ResponseEntity.badRequest().build();
        }


        GameRoom room = roomManager.createRoom(request.getType(), playerId, request.getHost());

        // 3. Attach Cookie to Response
        Cookie cookie = new Cookie("playerId", room.getHost().getId());
        cookie.setHttpOnly(false); // Secure! JS can't read this
        cookie.setPath("/");
        cookie.setMaxAge(86400);  // 24 hours
        response.addCookie(cookie);

        return ResponseEntity.ok(room);
    }

    @PostMapping("/join/{roomId}")
    public ResponseEntity<?> joinRoom(@PathVariable(name = "roomId") String roomId,
                                      @CookieValue(name = "playerId", required = false) String playerId,
                                      @RequestBody JoinRequest request,
                                      HttpServletResponse response) {

        // 1. Check if room exists
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found");
        }

        GamePlayer player = room.addPlayer(playerId, request.getPlayerName(), false);


        // 3. Attach Cookie to Response
        Cookie cookie = new Cookie("playerId", player.getId());
        cookie.setHttpOnly(false); // Secure! JS can't read this
        cookie.setPath("/");
        cookie.setMaxAge(86400);  // 24 hours
        response.addCookie(cookie);

        // 4. Return success (UI doesn't need the ID, it's already in the cookie)
        return ResponseEntity.ok().body(Map.of("status", "success", "roomId", roomId));
    }

}
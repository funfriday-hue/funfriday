package com.funfriday.controller;

import com.funfriday.exception.InvalidGameMoveException;
import com.funfriday.model.*;
import com.funfriday.request.JoinRequest;
import com.funfriday.request.StartRequest;
import com.funfriday.service.RoomManager;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CookieValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final RoomManager roomService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handles any game-related move (Wordle guess, Quiz answer, etc.)
     * The @Payload GameAction is automatically deserialized into its subclass
     * (e.g., WordleAction) based on the "type" field in the JSON.
     */
    @MessageMapping("/game/{roomId}/move")
    public void handleMove(
            @DestinationVariable("roomId") String roomId,
            SimpMessageHeaderAccessor headerAccessor,
            @Payload GameAction action
    ) {
        log.info("Received action from player {} in room {}", action.getPlayerId(), roomId);

        try {
            // 1. Retrieve the room
            GameRoom room = roomService.getRoom(roomId);
            if (room == null) {
                log.error("Room {} not found", roomId);
                return;
            }

            String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
            action.setPlayerId(playerId);

            // 2. Delegate logic to the room
            // This handles processMove, updateStats, and isGameOver checks
            room.handlePlayerAction(action);

            // 3. Broadcast the entire room state to all subscribers
            // The frontend receives the updated GameData and Scoreboard
            broadcastRoomUpdate(roomId, room);

        } catch (InvalidGameMoveException e) {
            log.warn("Business rule violation: {}", e.getMessage());
            // 🎯 Route the machine-readable error token ("NOT_A_VALID_WORD") to the player's custom path!
            sendErrorMessage(roomId, action.getPlayerId(), e.getErrorCode());

        } catch (IllegalStateException e) {
            log.warn("Invalid move attempted: {}", e.getMessage());
            sendErrorMessage(roomId, action.getPlayerId(), e.getMessage());
        } catch (Exception e) {
            log.error("Error processing move", e);
            sendErrorMessage(roomId, action.getPlayerId(), "An unexpected error occurred.");
        }
    }

    /**
     * Optional: Logic to handle a player explicitly signaling they are ready
     */
    @MessageMapping("/game/{roomId}/ready")
    public void handleReady(
            @DestinationVariable("roomId") String roomId,
            @Payload String playerName
    ) {
        GameRoom room = roomService.getRoom(roomId);
        PlayerStats stats = room.getGameData().getScoreBoard().get(playerName);

        if (stats != null) {
            // Logic to start game if all players are ready could go here
            broadcastRoomUpdate(roomId, room);
        }
    }

    @MessageMapping("/game/{roomId}/join")
    public void handleJoin(@DestinationVariable("roomId") String roomId,
                           SimpMessageHeaderAccessor headerAccessor,
                           @Payload JoinRequest request) {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) {
            sendErrorMessage(roomId, request.getPlayerName(), "No Room Found");
        }


        // Broadcast the updated room so the new player appears on everyone's screen
        messagingTemplate.convertAndSend("/topic/room/" + roomId, room);
    }

    @MessageMapping("/game/{roomId}/start")
    public void startGame(@DestinationVariable("roomId") String roomId,
                          SimpMessageHeaderAccessor headerAccessor,
                          StartRequest request) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        GameRoom room = roomService.getRoom(roomId);
        if (room != null && room.getHost().getId().equals(playerId)) {
            room.setStatus(GameStatus.IN_PROGRESS);
            messagingTemplate.convertAndSend("/topic/room/" + roomId, room);
        }
    }

    // --- Helper Methods ---

    private void broadcastRoomUpdate(String roomId, GameRoom room) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, room);
    }

    private void sendErrorMessage(String roomId, String playerId, String error) {
        // 🎯 Target the exact error sub-topic for this unique player ID string
        String targetDestination = "/topic/room/" + roomId + "/player/" + playerId + "/errors";
        log.info("Broadcasting error token '{}' to explicit path: {}", error, targetDestination);

        messagingTemplate.convertAndSend(targetDestination, error);
    }

    @MessageExceptionHandler(InvalidGameMoveException.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleInvalidGameMove(InvalidGameMoveException ex) {
        Map<String, String> errorPayload = new HashMap<>();

        // Provide both a machine-readable token for the UI state and a human-readable message for debugging logs
        errorPayload.put("error", ex.getErrorCode());     // "NOT_A_VALID_WORD"
        errorPayload.put("message", ex.getMessage());     // "The word 'ABCDE' is not in the dictionary."

        return errorPayload;
    }
}
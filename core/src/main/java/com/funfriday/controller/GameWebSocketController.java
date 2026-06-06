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
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        action.setPlayerId(playerId);
        // submit and then broadcast after completion
        roomService.submitPlayerAction(roomId, action)
                .whenComplete((updatedRoom, ex) -> {
                    if (ex != null) {
                        log.warn("Error processing action for room {}: {}", roomId, ex.getMessage(), ex);
                        sendErrorMessage(roomId, action.getPlayerId(), "Action processing failed");
                    } else {
                        broadcastRoomUpdate(roomId, updatedRoom);
                    }
                });

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
                          @Payload StartRequest request) {

        // Extract protocol metadata parameters safely from socket session attributes
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");

        // Submit startGame to the room executor and broadcast on completion
        roomService.submitStartGame(roomId, playerId, request.getGameMode(), request.getGenericProperties())
                .thenAccept(updatedRoom -> {
                    log.info("Game started in room {} by host {}", roomId, playerId);
                    messagingTemplate.convertAndSend("/topic/room/" + roomId, updatedRoom);
                })
                .exceptionally(ex -> {
                    log.warn("Error starting game in room {}: {}", roomId, ex.getMessage(), ex);

                    Map<String, String> errorResponse = new HashMap<>();
                    if (ex.getCause() instanceof IllegalStateException) {
                        errorResponse.put("error", ex.getCause().getMessage());
                        errorResponse.put("status", "BAD_REQUEST");
                    } else {
                        errorResponse.put("error", "An unexpected internal server error occurred while launching the match.");
                        errorResponse.put("status", "INTERNAL_SERVER_ERROR");
                    }

                    messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorResponse);
                    return null;
                });
    }

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
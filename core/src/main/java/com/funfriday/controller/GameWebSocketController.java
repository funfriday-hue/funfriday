package com.funfriday.controller;

import com.funfriday.dto.RoomPrivateView;
import com.funfriday.dto.RoomPublicView;
import com.funfriday.exception.InvalidGameMoveException;
import com.funfriday.model.*;
import com.funfriday.request.JoinRequest;
import com.funfriday.request.StartRequest;
import com.funfriday.service.RoomManager;
import com.funfriday.service.RoomViewFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final RoomManager roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomViewFactory viewFactory;
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
                        broadcastRoomPublic(roomId, updatedRoom);
                        // Private detailed state to the player who acted (and optionally to others individually if needed)
                        sendPrivateRoomUpdate(roomId, updatedRoom, action.getPlayerId());                    }
                });

    }

    @MessageMapping("/game/{roomId}/join")
    public void handleJoin(@DestinationVariable("roomId") String roomId,
                           SimpMessageHeaderAccessor headerAccessor,
                           @Payload JoinRequest request) {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) {
            sendErrorMessage(roomId, request.getPlayerName(), "No Room Found");
            return;
        }

        // Get or create playerId for this websocket session
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        if (playerId == null) {
            playerId = UUID.randomUUID().toString();
            headerAccessor.getSessionAttributes().put("playerId", playerId);
        }
        headerAccessor.getSessionAttributes().put("roomId", roomId);
        String sessionId = headerAccessor.getSessionId();
        String connectedPlayerId = playerId;

        roomService.submitPlayerConnectionUpdate(roomId, connectedPlayerId, sessionId, true)
                .thenAccept(updatedRoom -> {
                    broadcastRoomPublic(roomId, updatedRoom);
                    sendPrivateRoomUpdate(roomId, updatedRoom, connectedPlayerId);
                })
                .exceptionally(ex -> {
                    log.warn("Unable to mark player {} connected in room {}", connectedPlayerId, roomId, ex);
                    return null;
                });
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
                    broadcastRoomPublic(roomId, updatedRoom);
                    // Private detailed state to the player who acted (and optionally to others individually if needed)
                    updatedRoom.getPlayerMap().keySet().forEach(pid ->
                            sendPrivateRoomUpdate(roomId, updatedRoom, pid));

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

    @MessageMapping("/game/{roomId}/restart")
    public void restartGame(@DestinationVariable("roomId") String roomId,
                            SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        roomService.submitRestartGame(roomId, playerId)
                .thenAccept(updatedRoom -> {
                    log.info("Game restarted in room {} by host {}", roomId, playerId);
                    broadcastRoomPublic(roomId, updatedRoom);
                    updatedRoom.getPlayerMap().keySet().forEach(pid -> sendPrivateRoomUpdate(roomId, updatedRoom, pid));
                })
                .exceptionally(ex -> {
                    log.warn("Error restarting game in room {}: {}", roomId, ex.getMessage(), ex);
                    sendErrorMessage(roomId, playerId, "Unable to restart game");
                    return null;
                });
    }

    @MessageMapping("/game/{roomId}/lobby")
    public void returnToLobby(@DestinationVariable("roomId") String roomId,
                              SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        roomService.submitReturnToLobby(roomId, playerId)
                .thenAccept(updatedRoom -> {
                    log.info("Room {} returned to lobby by host {}", roomId, playerId);
                    broadcastRoomPublic(roomId, updatedRoom);
                    updatedRoom.getPlayerMap().keySet().forEach(pid -> sendPrivateRoomUpdate(roomId, updatedRoom, pid));
                })
                .exceptionally(ex -> {
                    log.warn("Error returning room {} to lobby: {}", roomId, ex.getMessage(), ex);
                    sendErrorMessage(roomId, playerId, "Unable to return to lobby");
                    return null;
                });
    }

    @MessageMapping("/game/{roomId}/kick")
    public void kickPlayer(@DestinationVariable("roomId") String roomId,
                           SimpMessageHeaderAccessor headerAccessor,
                           @Payload KickPlayerRequest request) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        roomService.submitKickPlayer(roomId, playerId, request.playerId())
                .thenAccept(updatedRoom -> {
                    log.info("Host {} removed player {} from room {}", playerId, request.playerId(), roomId);
                    broadcastRoomPublic(roomId, updatedRoom);
                    sendErrorMessage(roomId, request.playerId(), "KICKED_FROM_ROOM");
                })
                .exceptionally(ex -> {
                    log.warn("Error removing player from room {}: {}", roomId, ex.getMessage(), ex);
                    sendErrorMessage(roomId, playerId, "Unable to remove player");
                    return null;
                });
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) return;
        String roomId = (String) attributes.get("roomId");
        String playerId = (String) attributes.get("playerId");
        if (roomId == null || playerId == null) return;

        roomService.submitPlayerConnectionUpdate(roomId, playerId, accessor.getSessionId(), false)
                .thenAccept(updatedRoom -> broadcastRoomPublic(roomId, updatedRoom))
                .exceptionally(ex -> {
                    log.debug("Unable to mark player {} disconnected in room {}: {}", playerId, roomId, ex.getMessage());
                    return null;
                });
    }

    // Broadcast safe public view to everyone
    private void broadcastRoomPublic(String roomId, GameRoom room) {
        RoomPublicView publicView = viewFactory.buildPublicView(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, publicView);
    }

    // Send private view to a single player (use per-player topic)
    private void sendPrivateRoomUpdate(String roomId, GameRoom room, String playerId) {
        RoomPrivateView privateView = viewFactory.buildPrivateView(room, playerId);
        // Using per-player topic under the room — frontend should subscribe to this path:
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/player/" + playerId + "/state", privateView);
        // Alternatively use convertAndSendToUser(playerId, "/queue/room", privateView) if user destinations are configured
    }

    private void sendErrorMessage(String roomId, String playerId, String error) {
        // 🎯 Target the exact error sub-topic for this unique player ID string
        String targetDestination = "/topic/room/" + roomId + "/player/" + playerId + "/errors";
        log.info("Broadcasting error token '{}' to explicit path: {}", error, targetDestination);

        messagingTemplate.convertAndSend(targetDestination, error);
    }

    private record KickPlayerRequest(String playerId) { }

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

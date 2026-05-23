package com.funfriday.service;

import com.funfriday.factory.GameFactory;
import com.funfriday.model.GameRoom;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RoomManager {
    // Configure the cache to drop keys 30 minutes after the last read or write access
    private final Cache<String, GameRoom> activeRooms = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public GameRoom createRoom(GameFactory.GameType gameType, String playerId, String hostName) {
        String roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        GameLogic logic = GameFactory.createGame(gameType);
        GameRoom room = new GameRoom(roomId, playerId, hostName, logic, gameType);
        activeRooms.put(roomId, room);
        return room;
    }

    /**
     * Retrieves an active room by its ID.
     */
    public GameRoom getRoom(String roomId) {
        return activeRooms.getIfPresent(roomId);
    }

    /**
     * Optional: Clean up rooms when games are finished to save memory.
     */
    public void removeRoom(String roomId) {
        activeRooms.invalidate(roomId);
    }
}
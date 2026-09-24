package com.funfriday.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Getter
@Setter
public class GamePlayer {
    private String id;
    private String name;
    private boolean isHost;
    @JsonIgnore
    private final Set<String> connectedSessionIds = ConcurrentHashMap.newKeySet();

    public GamePlayer(String id, String name, boolean isHost) {
        this.id = id;
        this.name = name;
        this.isHost = isHost;
    }

    public void setSessionConnected(String sessionId, boolean connected) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (connected) connectedSessionIds.add(sessionId);
        else connectedSessionIds.remove(sessionId);
    }

    public boolean isConnected() {
        return !connectedSessionIds.isEmpty();
    }


}

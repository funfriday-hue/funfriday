package com.funfriday.dto;

import com.funfriday.model.GameConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class RoomPublicView {
    private String roomId;
    private String status;
    private GameConfiguration configuration;
    private String type;
    private long startTime;
    private PlayerPublic host;  // NEW: host info
    private List<PlayerPublic> players;
    private Object gameSpecificPublicData;

    @Data
    @AllArgsConstructor
    public static class PlayerPublic {
        private String id;
        private String name;
        private String status;
        private int score;
        private Map<String, Object> stats;
    }
}
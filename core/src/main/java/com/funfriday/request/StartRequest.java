package com.funfriday.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Data
public class StartRequest {
    // Top-level property shared across ALL games
    private String gameMode;

    // 🎯 Catch-all container for game-specific payload items (e.g., {"hints": 3, "gridSize": "4x4"})
    private Map<String, Object> genericProperties = new HashMap<>();
}

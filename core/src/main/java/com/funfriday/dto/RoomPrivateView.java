package com.funfriday.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class RoomPrivateView {
    private String roomId;
    private String status;
    private String type;
    private long startTime;
    private RoomPublicView.PlayerPublic host;  // NEW: host info
    private RoomPublicView.PlayerPublic self;
    private Object gameSpecificPublicData;
    private Map<String, Object> privateGameData;
}
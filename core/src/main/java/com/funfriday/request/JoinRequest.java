package com.funfriday.request;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class JoinRequest {
    private String playerName;

    // Default constructor is required for JSON parsing
    public JoinRequest() {}

    public JoinRequest(String playerName) {
        this.playerName = playerName;
    }

}
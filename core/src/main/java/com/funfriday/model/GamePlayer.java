package com.funfriday.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class GamePlayer {
    private String id;
    private String name;
    private boolean isHost;

    public GamePlayer(String id, String name, boolean isHost) {
        this.id = id;
        this.name = name;
        this.isHost = isHost;
    }


}

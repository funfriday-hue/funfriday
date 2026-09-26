package com.funfriday.request;

import com.funfriday.factory.GameFactory;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class CreateRoomRequest {
    private GameFactory.GameType type;
    private String host;
    /** Optional preselected game mode, used by Quiz Royale category cards. */
    private String gameMode;
}

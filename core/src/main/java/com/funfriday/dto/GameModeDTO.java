package com.funfriday.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GameModeDTO {
    private String gameType;  // WORDLE, SUDOKU
    private List<ModeOption> modes;

    @Data
    @AllArgsConstructor
    public static class ModeOption {
        private String modeId;        // WORD_3, TIME_5, SUDOKU_9x9, etc.
        private String displayName;   // 3-Word Race, 2 Min Sprint, etc.
    }
}
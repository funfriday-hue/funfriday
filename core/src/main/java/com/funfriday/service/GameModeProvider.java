package com.funfriday.service;

import com.funfriday.dto.GameModeDTO;

import java.util.List;

public interface GameModeProvider {
    List<GameModeDTO.ModeOption> getAvailableModes();
}

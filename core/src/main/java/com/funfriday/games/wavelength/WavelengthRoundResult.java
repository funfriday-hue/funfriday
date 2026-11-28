package com.funfriday.games.wavelength;

/** A compact, public recap entry for one completed Telepathy round. */
public record WavelengthRoundResult(
    int roundNumber,
    String teamId,
    String clue,
    int points
) { }

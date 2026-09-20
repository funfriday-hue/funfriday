package com.funfriday.games.dobble;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DobbleCard {
    private String id;
    private List<String> symbols;
}

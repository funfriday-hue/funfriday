package com.funfriday.games.wavelength;

import com.funfriday.model.GameConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WavelengthConfiguration implements GameConfiguration {
    private Map<String, String> teamAssignments = new LinkedHashMap<>();
    private Map<String, String> teamNames = new LinkedHashMap<>();
}

package com.funfriday.games.quizroyale;

import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

@Component
public class AnswerMatcher {
    public boolean matches(String submitted, QuizAnswer answer) {
        String input = normalize(submitted);
        if (input.isBlank()) return false;
        if (similar(input, normalize(answer.getValue()))) return true;
        return answer.getAliases() != null && answer.getAliases().stream().anyMatch(alias -> similar(input, normalize(alias)));
    }

    /**
     * Accept a recognisable part of a multi-word answer, e.g. "de Silva" for
     * "Aravinda de Silva" or "Strokes" for "Ben Stokes".  Callers must make
     * sure this produces a unique answer in a list question before accepting it.
     */
    public boolean matchesShortForm(String submitted, QuizAnswer answer) {
        String input = normalize(submitted);
        if (input.length() < 4 || answer == null || answer.getValue() == null) return false;

        String[] words = answer.getValue().split("\\s+");
        for (int start = 0; start < words.length; start++) {
            String shortForm = normalize(String.join(" ", Arrays.copyOfRange(words, start, words.length)));
            if (shortForm.length() >= 4 && similar(input, shortForm)) return true;
        }
        return false;
    }
    public String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    private boolean similar(String a, String b) {
        if (a.equals(b)) return true;
        int distance = levenshtein(a, b);
        return distance <= 2 && (double) distance / Math.max(a.length(), b.length()) <= .40;
    }
    private int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) { costs[0] = i; int diagonal = i - 1;
            for (int j = 1; j <= b.length(); j++) { int old = costs[j]; costs[j] = Math.min(Math.min(costs[j] + 1, costs[j - 1] + 1), diagonal + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)); diagonal = old; }
        }
        return costs[b.length()];
    }
}

package com.funfriday.games.dobble;

import com.funfriday.dto.GameModeDTO;
import com.funfriday.exception.InvalidGameMoveException;
import com.funfriday.model.GameAction;
import com.funfriday.model.GameConfiguration;
import com.funfriday.model.GameData;
import com.funfriday.model.GamePlayer;
import com.funfriday.model.PlayerStats;
import com.funfriday.model.PlayerStatus;
import com.funfriday.service.GameLogic;
import com.funfriday.service.GameModeProvider;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Component
public class DobbleGame implements GameLogic, GameModeProvider {
    public static final int SYMBOLS_PER_CARD = 8;
    public static final int CARDS_PER_ROUND = 5;
    public static final int ROUND_COUNT = 9;
    private static final int ORDER = 7;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> SYMBOLS = List.of(
            "★", "●", "▲", "■", "◆", "♥", "☀", "☂", "☕", "✈", "⚽", "♫", "⚓", "☘", "♞", "☾", "☁", "⚡", "❄", "✿",
            "☯", "☮", "⌛", "⌂", "✉", "✏", "✂", "☎", "⚙", "☠", "♟", "♣", "♦", "♠", "☃", "☄", "☝", "✦", "✧", "✩",
            "✪", "✫", "✬", "✭", "✮", "✯", "✰", "✱", "✲", "✳", "✴", "✵", "✶", "✷", "✸", "✹", "✺"
    );

    @Override
    public GameData<?> initializeData(GameConfiguration configuration) {
        List<DobbleCard> deck = new ArrayList<>(buildDeck());
        Collections.shuffle(deck, RANDOM);

        DobbleData data = new DobbleData();
        DobbleGameMode mode = ((DobbleConfiguration) configuration).getGameMode();
        data.setGameMode(mode);
        data.setCurrentCardOpenedAtMillis(System.currentTimeMillis());
        if (mode == DobbleGameMode.PAIR_RUSH) {
            data.setPairCards(new ArrayList<>(deck.subList(0, 50)));
            return data;
        }
        if (mode == DobbleGameMode.TRIPLE_HUNT) {
            initializeTripleHunt(data, deck);
            return data;
        }
        List<DobbleCard> centerCards = new ArrayList<>();
        List<DobbleCard> outerCards = new ArrayList<>();
        for (int round = 0; round < ROUND_COUNT; round++) {
            int roundStart = round * (CARDS_PER_ROUND + 1);
            centerCards.add(deck.get(roundStart));
            outerCards.addAll(deck.subList(roundStart + 1, roundStart + 1 + CARDS_PER_ROUND));
        }
        data.setCenterCards(centerCards);
        data.setOuterCards(outerCards);
        data.setCurrentCardIndex(0);
        data.setCurrentCardSolved(false);
        return data;
    }

    @Override
    public void processMove(GameAction action, GameData<?> rawData) {
        DobbleAction dobbleAction = (DobbleAction) action;
        DobbleData data = (DobbleData) rawData;

        if (data.getGameMode() == DobbleGameMode.CLASSIC && "DOBBLE_NEXT_CARD".equals(dobbleAction.getType())) {
            advanceCard(data);
            return;
        }
        if (!"DOBBLE_MATCH".equals(dobbleAction.getType())) {
            throw new InvalidGameMoveException("Unknown Dobble action.", "INVALID_DOBBLE_ACTION");
        }
        if (data.getGameMode() == DobbleGameMode.PAIR_RUSH) {
            processPairMatch(action, dobbleAction, data);
            return;
        }
        if (data.getGameMode() == DobbleGameMode.TRIPLE_HUNT) {
            processTripleMatch(action, dobbleAction, data);
            return;
        }
        if (data.isCurrentCardSolved()) {
            throw new InvalidGameMoveException("That card has already been claimed.", "CARD_ALREADY_CLAIMED");
        }

        DobbleCard currentCard = data.getCurrentCard();
        if (currentCard == null) {
            throw new InvalidGameMoveException("The match is complete.", "MATCH_COMPLETE");
        }
        String selected = dobbleAction.getSelectedSymbol();
        String matchingSymbol = findMatchingSymbol(data.getCenterCard(), currentCard);
        if (selected == null || !selected.equals(matchingSymbol)) {
            throw new InvalidGameMoveException("That symbol is not the match.", "WRONG_SYMBOL");
        }

        DobblePlayerStats stats = (DobblePlayerStats) data.getScoreBoard().get(action.getPlayerId());
        long responseTime = Math.max(0, System.currentTimeMillis() - data.getCurrentCardOpenedAtMillis());
        stats.setMatchesFound(stats.getMatchesFound() + 1);
        stats.setScore(stats.getMatchesFound());
        stats.setTotalResponseTimeMillis(stats.getTotalResponseTimeMillis() + responseTime);

        data.setCurrentCardSolved(true);
        data.setLastMatchPlayerId(action.getPlayerId());
        data.setLastMatchSymbol(matchingSymbol);
        if (data.getCurrentCardIndex() == data.getOuterCards().size() - 1) {
            data.setFinished(true);
            data.getScoreBoard().values().forEach(playerStats -> playerStats.setStatus(PlayerStatus.COMPLETED));
        }
    }

    private void advanceCard(DobbleData data) {
        if (!data.isCurrentCardSolved()) {
            throw new InvalidGameMoveException("Find the match before revealing the next card.", "CARD_STILL_OPEN");
        }
        if (data.isFinished()) {
            return;
        }
        data.setCurrentCardIndex(data.getCurrentCardIndex() + 1);
        data.setCurrentCardSolved(false);
        data.setCurrentCardOpenedAtMillis(System.currentTimeMillis());
        data.setLastMatchPlayerId(null);
        data.setLastMatchSymbol(null);
    }

    private void processPairMatch(GameAction action, DobbleAction dobbleAction, DobbleData data) {
        int start = data.getCurrentRoundIndex() * 2;
        if (start >= data.getPairCards().size()) throw new InvalidGameMoveException("The match is complete.", "MATCH_COMPLETE");
        String match = findMatchingSymbol(data.getPairCards().get(start), data.getPairCards().get(start + 1));
        if (!match.equals(dobbleAction.getSelectedSymbol())) throw new InvalidGameMoveException("That symbol is not the match.", "WRONG_SYMBOL");
        awardPoint(action.getPlayerId(), data);
        data.setLastMatchSymbol(match);
        data.setCurrentRoundIndex(data.getCurrentRoundIndex() + 1);
        data.setCurrentCardOpenedAtMillis(System.currentTimeMillis());
        if (data.getCurrentRoundIndex() == 25) finish(data);
    }

    private void processTripleMatch(GameAction action, DobbleAction dobbleAction, DobbleData data) {
        List<String> ids = dobbleAction.getSelectedCardIds();
        if (ids == null || ids.size() != 3 || new HashSet<>(ids).size() != 3) throw new InvalidGameMoveException("Select exactly three cards.", "SELECT_THREE_CARDS");
        List<DobbleCard> selected = data.getTripleBoard().stream().filter(card -> ids.contains(card.getId())).toList();
        if (selected.size() != 3 || dobbleAction.getSelectedSymbol() == null || selected.stream().anyMatch(card -> !card.getSymbols().contains(dobbleAction.getSelectedSymbol()))) {
            throw new InvalidGameMoveException("Those cards do not share that symbol.", "INVALID_TRIPLE");
        }
        awardPoint(action.getPlayerId(), data);
        data.getTripleBoard().removeAll(selected);
        data.setLastMatchSymbol(dobbleAction.getSelectedSymbol());
        data.setCurrentRoundIndex(data.getCurrentRoundIndex() + 1);
        if (data.getCurrentRoundIndex() == 12) { finish(data); return; }
        addTripleReplacement(data);
        data.setCurrentCardOpenedAtMillis(System.currentTimeMillis());
    }

    private void awardPoint(String playerId, DobbleData data) {
        DobblePlayerStats stats = (DobblePlayerStats) data.getScoreBoard().get(playerId);
        stats.setMatchesFound(stats.getMatchesFound() + 1);
        stats.setScore(stats.getMatchesFound());
        stats.setTotalResponseTimeMillis(stats.getTotalResponseTimeMillis() + Math.max(0, System.currentTimeMillis() - data.getCurrentCardOpenedAtMillis()));
        data.setLastMatchPlayerId(playerId);
    }

    private void finish(DobbleData data) {
        data.setFinished(true);
        data.getScoreBoard().values().forEach(stats -> stats.setStatus(PlayerStatus.COMPLETED));
    }

    @Override
    public void updateStats(PlayerStats stats, GameData<?> data) {
        // Dobble updates the successful player's score atomically while validating their answer.
    }

    @Override
    public boolean isGameOver(GameData<?> data) {
        return data.isFinished();
    }

    @Override
    public PlayerStats createInitialStats(GamePlayer player, boolean isHost) {
        return new DobblePlayerStats(player, isHost);
    }

    @Override
    public GameConfiguration parseConfiguration(Map<String, Object> payload) {
        try {
            return new DobbleConfiguration(DobbleGameMode.valueOf(String.valueOf(payload.getOrDefault("gameMode", "CLASSIC")).toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return new DobbleConfiguration(DobbleGameMode.CLASSIC);
        }
    }

    @Override
    public List<GameModeDTO.ModeOption> getAvailableModes() {
        return Arrays.stream(DobbleGameMode.values())
                .map(mode -> new GameModeDTO.ModeOption(mode.name(), mode.getDisplayName()))
                .toList();
    }

    private void initializeTripleHunt(DobbleData data, List<DobbleCard> deck) {
        String target = deck.get(0).getSymbols().get(0);
        List<DobbleCard> board = deck.stream().filter(card -> card.getSymbols().contains(target)).limit(3)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        deck.stream().filter(card -> !board.contains(card)).limit(6).forEach(board::add);
        data.setTripleBoard(board);
        data.setTripleDeck(deck.stream().filter(card -> !board.contains(card))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
    }

    private void addTripleReplacement(DobbleData data) {
        List<DobbleCard> deck = data.getTripleDeck();
        for (int i = 0; i < deck.size() - 2; i++) for (int j = i + 1; j < deck.size() - 1; j++) for (int k = j + 1; k < deck.size(); k++) {
            List<DobbleCard> candidate = new ArrayList<>(data.getTripleBoard());
            candidate.add(deck.get(i)); candidate.add(deck.get(j)); candidate.add(deck.get(k));
            if (hasTriple(candidate)) {
                data.getTripleBoard().add(deck.get(i)); data.getTripleBoard().add(deck.get(j)); data.getTripleBoard().add(deck.get(k));
                deck.remove(k); deck.remove(j); deck.remove(i);
                return;
            }
        }
        throw new IllegalStateException("Unable to generate the next Triple Hunt board.");
    }

    private boolean hasTriple(List<DobbleCard> cards) {
        return cards.stream().flatMap(card -> card.getSymbols().stream())
                .collect(java.util.stream.Collectors.groupingBy(symbol -> symbol, java.util.stream.Collectors.counting()))
                .values().stream().anyMatch(count -> count >= 3);
    }

    private List<DobbleCard> buildDeck() {
        if (SYMBOLS.size() != 57) {
            throw new IllegalStateException("Dobble requires exactly 57 distinct symbols.");
        }
        List<Set<Integer>> cardSymbols = new ArrayList<>();
        // Non-vertical lines y = mx + b, plus their shared point at infinity for slope m.
        for (int m = 0; m < ORDER; m++) {
            for (int b = 0; b < ORDER; b++) {
                Set<Integer> card = new HashSet<>();
                for (int x = 0; x < ORDER; x++) {
                    card.add(x * ORDER + ((m * x + b) % ORDER));
                }
                card.add(49 + m);
                cardSymbols.add(card);
            }
        }
        // Vertical lines x = constant.
        for (int x = 0; x < ORDER; x++) {
            Set<Integer> card = new HashSet<>();
            for (int y = 0; y < ORDER; y++) {
                card.add(x * ORDER + y);
            }
            card.add(56);
            cardSymbols.add(card);
        }
        // The line at infinity.
        cardSymbols.add(IntStream.range(49, 57).collect(HashSet::new, Set::add, Set::addAll));

        return IntStream.range(0, cardSymbols.size())
                .mapToObj(index -> new DobbleCard(
                        "card-" + index,
                        cardSymbols.get(index).stream().sorted().map(SYMBOLS::get).toList()
                ))
                .toList();
    }

    private String findMatchingSymbol(DobbleCard center, DobbleCard outer) {
        return center.getSymbols().stream()
                .filter(outer.getSymbols()::contains)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Dobble deck invariant violated: cards do not match."));
    }
}

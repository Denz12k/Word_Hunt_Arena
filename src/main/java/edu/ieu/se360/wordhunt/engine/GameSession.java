package edu.ieu.se360.wordhunt.engine;

import edu.ieu.se360.wordhunt.config.ConfigManager;
import edu.ieu.se360.wordhunt.model.PlayerScore;
import edu.ieu.se360.wordhunt.reflection.HintPowerUp;
import edu.ieu.se360.wordhunt.reflection.ReflectionRuleLoader;
import edu.ieu.se360.wordhunt.reflection.ShufflePowerUp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the live state of an active Word Hunt Arena match:
 * - 12-letter shared board ({@code List<Character>})
 * - Thread-safe locked words map ({@code ConcurrentHashMap<String, String>}: Word -> Username)
 * - Thread-safe player scores map ({@code ConcurrentHashMap<String, PlayerScore>})
 * - Synchronized word submission & validation ({@link #submitWord(String, String)})
 * - Live leaderboard sorted via {@link PriorityQueue} ({@link #getSortedLeaderboard()})
 * - Missed high-value words discovery ({@link #getMissedHighValueWords(int)})
 */
public class GameSession {
    private final List<Character> boardLetters;
    private final ConcurrentHashMap<String, String> lockedWords;
    private final ConcurrentHashMap<String, PlayerScore> playerScores;
    private final DictionaryManager dictionaryManager;
    private final ScoreCalculator scoreCalculator;
    private final ReflectionRuleLoader ruleLoader;
    private final int minWordLength;
    private volatile boolean active;

    public GameSession() {
        this(new BoardGenerator().generateBoard(), DictionaryManager.getInstance(), new ScoreCalculator());
    }

    public GameSession(List<Character> boardLetters) {
        this(boardLetters, DictionaryManager.getInstance(), new ScoreCalculator());
    }

    public GameSession(List<Character> boardLetters, DictionaryManager dictionaryManager, ScoreCalculator scoreCalculator) {
        this.dictionaryManager = dictionaryManager != null ? dictionaryManager : DictionaryManager.getInstance();
        this.scoreCalculator = scoreCalculator != null ? scoreCalculator : new ScoreCalculator(this.dictionaryManager, new ReflectionRuleLoader());
        this.ruleLoader = this.scoreCalculator.getRuleLoader();
        this.boardLetters = new ArrayList<>(boardLetters != null ? boardLetters : new BoardGenerator().generateBoard());
        this.lockedWords = new ConcurrentHashMap<>();
        this.playerScores = new ConcurrentHashMap<>();
        int configuredMinLen = ConfigManager.getInstance().getMinWordLength();
        this.minWordLength = configuredMinLen > 0 ? configuredMinLen : 3;
        this.active = true;
    }

    /**
     * Registers a player in the active game session if not already present.
     */
    public PlayerScore addPlayer(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        String trimmed = username.trim();
        return playerScores.computeIfAbsent(trimmed, PlayerScore::new);
    }

    /**
     * Removes a player from the session.
     */
    public void removePlayer(String username) {
        if (username != null) {
            playerScores.remove(username.trim());
        }
    }

    /**
     * Thread-safe word validation and locking method.
     * Validates:
     * 1. Is the word at least {@code minWordLength} (3) characters long? -> {@code TOO_SHORT}
     * 2. Can the word be formed from the 12 board letters' frequencies? -> {@code INVALID_LETTERS}
     * 3. Does the word exist in the dictionary {@code HashSet}? -> {@code NOT_IN_DICTIONARY}
     * 4. Was the word already claimed by another player ({@code ConcurrentHashMap.putIfAbsent})? -> {@code ALREADY_CLAIMED}
     * If all checks pass, locks the word, calculates the score (consuming 2x power-up if active),
     * updates the player's score, and returns {@code SUCCESS} with the points earned.
     *
     * @param username the player submitting the word
     * @param word     the candidate word
     * @return a {@link WordSubmissionResult} containing the status and points awarded
     */
    public synchronized WordSubmissionResult submitWord(String username, String word) {
        String safeUser = (username == null || username.isBlank()) ? "Unknown" : username.trim();
        PlayerScore playerScore = playerScores.computeIfAbsent(safeUser, PlayerScore::new);

        if (word == null) {
            return new WordSubmissionResult(
                    WordSubmissionResult.Status.TOO_SHORT,
                    "",
                    safeUser,
                    0,
                    null,
                    "Word must be at least " + minWordLength + " letters long."
            );
        }

        String normalizedWord = word.trim().toUpperCase(Locale.ENGLISH);

        // 1. Check minimum word length (at least 3 letters)
        if (normalizedWord.length() < minWordLength) {
            return new WordSubmissionResult(
                    WordSubmissionResult.Status.TOO_SHORT,
                    normalizedWord,
                    safeUser,
                    0,
                    null,
                    "Word '" + normalizedWord + "' is too short (minimum " + minWordLength + " letters)."
            );
        }

        // 2. Check if the word can be formed from the 12 letters on the board
        if (!dictionaryManager.canFormWord(normalizedWord, boardLetters)) {
            return new WordSubmissionResult(
                    WordSubmissionResult.Status.INVALID_LETTERS,
                    normalizedWord,
                    safeUser,
                    0,
                    null,
                    "Word '" + normalizedWord + "' cannot be formed from the current board letters."
            );
        }

        // 3. Check if the word exists in the dictionary (HashSet O(1))
        if (!dictionaryManager.isValidWord(normalizedWord)) {
            return new WordSubmissionResult(
                    WordSubmissionResult.Status.NOT_IN_DICTIONARY,
                    normalizedWord,
                    safeUser,
                    0,
                    null,
                    "Word '" + normalizedWord + "' is not in the dictionary."
            );
        }

        // 4. Check if the word was already claimed by someone (ConcurrentHashMap.putIfAbsent)
        String existingOwner = lockedWords.putIfAbsent(normalizedWord, safeUser);
        if (existingOwner != null) {
            return new WordSubmissionResult(
                    WordSubmissionResult.Status.ALREADY_CLAIMED,
                    normalizedWord,
                    safeUser,
                    0,
                    existingOwner,
                    "Word '" + normalizedWord + "' was already claimed by " + existingOwner + "!"
            );
        }

        // 5. Word is valid and newly claimed! Calculate score and update player stats
        boolean useDoubleScore = playerScore.isDoubleScoreActive();
        int earnedPoints = scoreCalculator.calculateWordScore(normalizedWord, useDoubleScore);
        if (useDoubleScore) {
            playerScore.setDoubleScoreActive(false);
        }
        playerScore.addPoints(earnedPoints);

        return new WordSubmissionResult(
                WordSubmissionResult.Status.SUCCESS,
                normalizedWord,
                safeUser,
                earnedPoints,
                safeUser,
                safeUser + " claimed '" + normalizedWord + "' for +" + earnedPoints + " points!"
        );
    }

    /**
     * Activates the 2x Double Score power-up for the specified player (once per match).
     *
     * @param username the player's username
     * @return true if activated; false if already used in this match
     */
    public synchronized boolean activateDoubleScorePowerUp(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        PlayerScore ps = playerScores.computeIfAbsent(username.trim(), PlayerScore::new);
        if (ps.isDoubleScoreUsed()) {
            return false;
        }
        ruleLoader.activatePowerUp("DOUBLE_SCORE", ps);
        return true;
    }

    /**
     * Uses the Hint power-up for the specified player (once per match) and returns a formatted hint
     * (e.g., {@code "S _ _ _ _ A"}) for an unclaimed valid word on the board.
     *
     * @param username the player's username
     * @return the formatted hint string, or null if the player already used Hint or no unclaimed words remain
     */
    public synchronized String useHintPowerUp(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        PlayerScore ps = playerScores.computeIfAbsent(username.trim(), PlayerScore::new);
        if (ps.isHintUsed()) {
            return null;
        }
        List<String> missedWords = getMissedHighValueWords(10);
        if (missedWords.isEmpty()) {
            return null;
        }
        ruleLoader.activatePowerUp("HINT", ps);
        int randomIndex = ThreadLocalRandom.current().nextInt(missedWords.size());
        return HintPowerUp.formatHint(missedWords.get(randomIndex));
    }

    /**
     * Uses the Shuffle power-up for the specified player (once per match) and returns a shuffled
     * list of the board letters.
     *
     * @param username the player's username
     * @return a shuffled list of board characters, or null if already used
     */
    public synchronized List<Character> useShufflePowerUp(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        PlayerScore ps = playerScores.computeIfAbsent(username.trim(), PlayerScore::new);
        if (ps.isShuffleUsed()) {
            return null;
        }
        ruleLoader.activatePowerUp("SHUFFLE", ps);
        ShufflePowerUp shufflePowerUp = (ShufflePowerUp) ruleLoader.getPowerUp("SHUFFLE");
        if (shufflePowerUp != null) {
            return shufflePowerUp.shuffleLetters(boardLetters);
        }
        List<Character> copy = new ArrayList<>(boardLetters);
        Collections.shuffle(copy);
        return copy;
    }

    /**
     * Returns the live leaderboard sorted from highest score to lowest using a {@link PriorityQueue}.
     *
     * @return ordered list of {@link PlayerScore} objects (descending by score)
     */
    public List<PlayerScore> getSortedLeaderboard() {
        if (playerScores.isEmpty()) {
            return Collections.emptyList();
        }
        PriorityQueue<PlayerScore> maxHeap = new PriorityQueue<>(playerScores.size());
        maxHeap.addAll(playerScores.values());

        List<PlayerScore> sortedLeaderboard = new ArrayList<>(maxHeap.size());
        while (!maxHeap.isEmpty()) {
            sortedLeaderboard.add(maxHeap.poll());
        }
        return sortedLeaderboard;
    }

    /**
     * Finds the highest-scoring valid dictionary words that can be formed from the board's 12 letters
     * but were NOT claimed by any player during the match.
     *
     * @param limit maximum number of missed words to return (e.g., 5)
     * @return list of unclaimed high-value words sorted by score descending
     */
    public List<String> getMissedHighValueWords(int limit) {
        int effectiveLimit = limit > 0 ? limit : 5;
        List<String> allPossible = dictionaryManager.findAllPossibleWords(boardLetters);
        if (allPossible.isEmpty()) {
            return Collections.emptyList();
        }

        Comparator<String> highValueComparator = Comparator
                .comparingInt((String w) -> scoreCalculator.calculateWordScore(w, false))
                .reversed()
                .thenComparing(Comparator.comparingInt(String::length).reversed())
                .thenComparing(Comparator.naturalOrder());

        PriorityQueue<String> missedQueue = new PriorityQueue<>(highValueComparator);
        for (String candidate : allPossible) {
            if (!lockedWords.containsKey(candidate)) {
                missedQueue.offer(candidate);
            }
        }

        List<String> result = new ArrayList<>(Math.min(effectiveLimit, missedQueue.size()));
        while (!missedQueue.isEmpty() && result.size() < effectiveLimit) {
            result.add(missedQueue.poll());
        }
        return result;
    }

    /**
     * Builds a formatted summary of all players and their scores (e.g. "Deniz: 45, Ali: 32")
     * suitable for storing in {@code match_history.players_summary}.
     */
    public String buildPlayersSummary() {
        List<PlayerScore> leaderboard = getSortedLeaderboard();
        if (leaderboard.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < leaderboard.size(); i++) {
            PlayerScore ps = leaderboard.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ps.getUsername()).append(": ").append(ps.getScore());
        }
        return sb.toString();
    }

    public List<Character> getBoardLetters() {
        return Collections.unmodifiableList(boardLetters);
    }

    public Map<String, String> getLockedWords() {
        return Collections.unmodifiableMap(lockedWords);
    }

    public Map<String, PlayerScore> getPlayerScores() {
        return Collections.unmodifiableMap(playerScores);
    }

    public PlayerScore getPlayerScore(String username) {
        if (username == null) {
            return null;
        }
        return playerScores.get(username.trim());
    }

    public boolean isWordLocked(String word) {
        if (word == null) {
            return false;
        }
        return lockedWords.containsKey(word.trim().toUpperCase(Locale.ENGLISH));
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public DictionaryManager getDictionaryManager() {
        return dictionaryManager;
    }

    public ScoreCalculator getScoreCalculator() {
        return scoreCalculator;
    }
}

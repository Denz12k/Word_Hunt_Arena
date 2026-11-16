package edu.ieu.se360.wordhunt.network;

import edu.ieu.se360.wordhunt.model.PlayerScore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Defines the text-based TCP Socket communication protocol ({@code COMMAND|payload})
 * between {@link GameClient} and {@link GameServer}.
 *
 * <p>Client-to-Server Commands:
 * <ul>
 *     <li>{@code JOIN_LOBBY|username}</li>
 *     <li>{@code CHAT|username|message}</li>
 *     <li>{@code START_MATCH|username}</li>
 *     <li>{@code SUBMIT_WORD|username|word}</li>
 *     <li>{@code ACTIVATE_2X|username}</li>
 *     <li>{@code LEAVE|username}</li>
 * </ul>
 *
 * <p>Server-to-Client (Broadcast / Direct) Commands:
 * <ul>
 *     <li>{@code LOBBY_USERS|user1,user2}</li>
 *     <li>{@code CHAT_MSG|username|message}</li>
 *     <li>{@code MATCH_STARTED|K,A,L,E,M,S,I,N,R,T,O,U|90}</li>
 *     <li>{@code TIMER_TICK|remainingSeconds}</li>
 *     <li>{@code WORD_LOCKED|username|word|pointsEarned}</li>
 *     <li>{@code WORD_REJECTED|word|reason}</li>
 *     <li>{@code SCOREBOARD_UPDATE|user1:score1:words1,user2:score2:words2}</li>
 *     <li>{@code MATCH_ENDED|winnerUsername|winnerScore|missedWord1,missedWord2,missedWord3}</li>
 * </ul>
 */
public final class Protocol {

    public static final String DELIMITER = "|";
    public static final String DELIMITER_REGEX = Pattern.quote(DELIMITER);

    // Client -> Server Commands
    public static final String CMD_JOIN_LOBBY = "JOIN_LOBBY";
    public static final String CMD_CHAT = "CHAT";
    public static final String CMD_START_MATCH = "START_MATCH";
    public static final String CMD_SUBMIT_WORD = "SUBMIT_WORD";
    public static final String CMD_ACTIVATE_2X = "ACTIVATE_2X";
    public static final String CMD_LEAVE = "LEAVE";

    // Server -> Client Commands
    public static final String MSG_LOBBY_USERS = "LOBBY_USERS";
    public static final String MSG_CHAT_MSG = "CHAT_MSG";
    public static final String MSG_MATCH_STARTED = "MATCH_STARTED";
    public static final String MSG_TIMER_TICK = "TIMER_TICK";
    public static final String MSG_WORD_LOCKED = "WORD_LOCKED";
    public static final String MSG_WORD_REJECTED = "WORD_REJECTED";
    public static final String MSG_SCOREBOARD_UPDATE = "SCOREBOARD_UPDATE";
    public static final String MSG_MATCH_ENDED = "MATCH_ENDED";
    public static final String MSG_2X_ACTIVATED = "2X_ACTIVATED";
    public static final String MSG_ERROR = "ERROR";

    private Protocol() {
    }

    // =========================================================================
    // Client -> Server Message Builders
    // =========================================================================

    public static String buildJoinLobby(String username) {
        return CMD_JOIN_LOBBY + DELIMITER + sanitize(username);
    }

    public static String buildChat(String username, String message) {
        return CMD_CHAT + DELIMITER + sanitize(username) + DELIMITER + sanitize(message);
    }

    public static String buildStartMatch(String username) {
        return CMD_START_MATCH + DELIMITER + sanitize(username);
    }

    public static String buildSubmitWord(String username, String word) {
        return CMD_SUBMIT_WORD + DELIMITER + sanitize(username) + DELIMITER + sanitize(word);
    }

    public static String buildActivate2x(String username) {
        return CMD_ACTIVATE_2X + DELIMITER + sanitize(username);
    }

    public static String buildLeave(String username) {
        return CMD_LEAVE + DELIMITER + sanitize(username);
    }

    // =========================================================================
    // Server -> Client Message Builders
    // =========================================================================

    public static String buildLobbyUsers(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return MSG_LOBBY_USERS + DELIMITER;
        }
        StringBuilder sb = new StringBuilder(MSG_LOBBY_USERS).append(DELIMITER);
        boolean first = true;
        for (String user : usernames) {
            if (user != null && !user.isBlank()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(sanitize(user.trim()));
                first = false;
            }
        }
        return sb.toString();
    }

    public static String buildChatMsg(String username, String message) {
        return MSG_CHAT_MSG + DELIMITER + sanitize(username) + DELIMITER + sanitize(message);
    }

    public static String buildMatchStarted(List<Character> boardLetters, int durationSeconds) {
        StringBuilder lettersPart = new StringBuilder();
        if (boardLetters != null) {
            for (int i = 0; i < boardLetters.size(); i++) {
                if (i > 0) {
                    lettersPart.append(",");
                }
                lettersPart.append(boardLetters.get(i));
            }
        }
        return MSG_MATCH_STARTED + DELIMITER + lettersPart + DELIMITER + durationSeconds;
    }

    public static String buildTimerTick(int remainingSeconds) {
        return MSG_TIMER_TICK + DELIMITER + remainingSeconds;
    }

    public static String buildWordLocked(String username, String word, int pointsEarned) {
        return MSG_WORD_LOCKED + DELIMITER + sanitize(username) + DELIMITER + sanitize(word) + DELIMITER + pointsEarned;
    }

    public static String buildWordRejected(String word, String reason) {
        return MSG_WORD_REJECTED + DELIMITER + sanitize(word) + DELIMITER + sanitize(reason);
    }

    public static String buildScoreboardUpdate(List<PlayerScore> leaderboard) {
        StringBuilder sb = new StringBuilder(MSG_SCOREBOARD_UPDATE).append(DELIMITER);
        if (leaderboard != null && !leaderboard.isEmpty()) {
            for (int i = 0; i < leaderboard.size(); i++) {
                PlayerScore ps = leaderboard.get(i);
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(sanitize(ps.getUsername()))
                        .append(":")
                        .append(ps.getScore())
                        .append(":")
                        .append(ps.getWordsFoundCount());
            }
        }
        return sb.toString();
    }

    public static String buildMatchEnded(String winnerUsername, int winnerScore, List<String> missedWords) {
        String safeWinner = (winnerUsername == null || winnerUsername.isBlank()) ? "N/A" : sanitize(winnerUsername);
        StringBuilder missedPart = new StringBuilder();
        if (missedWords != null && !missedWords.isEmpty()) {
            for (int i = 0; i < missedWords.size(); i++) {
                if (i > 0) {
                    missedPart.append(",");
                }
                missedPart.append(sanitize(missedWords.get(i)));
            }
        }
        return MSG_MATCH_ENDED + DELIMITER + safeWinner + DELIMITER + winnerScore + DELIMITER + missedPart;
    }

    public static String build2xActivated(String username, boolean success) {
        return MSG_2X_ACTIVATED + DELIMITER + sanitize(username) + DELIMITER + success;
    }

    public static String buildError(String reason) {
        return MSG_ERROR + DELIMITER + sanitize(reason);
    }

    // =========================================================================
    // Parsing Helpers
    // =========================================================================

    /**
     * Splits a raw protocol line by {@code |}, preserving empty trailing tokens.
     */
    public static String[] splitMessage(String rawLine) {
        if (rawLine == null) {
            return new String[0];
        }
        return rawLine.split(DELIMITER_REGEX, -1);
    }

    /**
     * Parses a comma-separated list of characters (e.g., {@code "K,A,L,E,M..."}) into a {@link List} of {@link Character}.
     */
    public static List<Character> parseBoardLetters(String csvLetters) {
        if (csvLetters == null || csvLetters.isBlank()) {
            return Collections.emptyList();
        }
        String[] tokens = csvLetters.split(",");
        List<Character> letters = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                letters.add(Character.toUpperCase(trimmed.charAt(0)));
            }
        }
        return letters;
    }

    /**
     * Parses a comma-separated list of strings (e.g., lobby users or missed words).
     */
    public static List<String> parseCommaSeparatedList(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        String[] tokens = csv.split(",");
        List<String> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Parses a scoreboard payload ({@code user1:score1:words1,user2:score2:words2}) into a list of {@link PlayerScore}.
     */
    public static List<PlayerScore> parseScoreboard(String payload) {
        if (payload == null || payload.isBlank()) {
            return Collections.emptyList();
        }
        String[] entries = payload.split(",");
        List<PlayerScore> scores = new ArrayList<>(entries.length);
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] fields = trimmed.split(":");
            if (fields.length >= 3) {
                String username = fields[0].trim();
                int score = parseIntSafe(fields[1], 0);
                int wordsCount = parseIntSafe(fields[2], 0);
                scores.add(new PlayerScore(username, score, wordsCount, false));
            } else if (fields.length == 2) {
                String username = fields[0].trim();
                int score = parseIntSafe(fields[1], 0);
                scores.add(new PlayerScore(username, score, 0, false));
            }
        }
        return scores;
    }

    public static int parseIntSafe(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\r", " ").replace("\n", " ").replace(DELIMITER, "/").trim();
    }
}

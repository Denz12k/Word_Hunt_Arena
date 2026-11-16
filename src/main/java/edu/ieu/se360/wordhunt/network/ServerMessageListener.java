package edu.ieu.se360.wordhunt.network;

import edu.ieu.se360.wordhunt.model.PlayerScore;

import java.util.List;

/**
 * Listener interface for receiving real-time server events from {@link GameClient}'s
 * background listener thread.
 * All callback methods have default no-op implementations so GUI views can either
 * override specific callbacks or handle raw messages via {@link #onRawMessage(String)}.
 */
public interface ServerMessageListener {

    /**
     * Invoked for every raw line received from the server before or alongside parsed callbacks.
     */
    default void onRawMessage(String rawMessage) {
    }

    /**
     * Invoked when {@code LOBBY_USERS|user1,user2} is received.
     */
    default void onLobbyUsersUpdated(List<String> usernames) {
    }

    /**
     * Invoked when {@code CHAT_MSG|username|message} is received.
     */
    default void onChatMessage(String username, String message) {
    }

    /**
     * Invoked when {@code MATCH_STARTED|K,A,L,E,M...|90} is received.
     */
    default void onMatchStarted(List<Character> boardLetters, int durationSeconds) {
    }

    /**
     * Invoked every second when {@code TIMER_TICK|remainingSeconds} is received.
     */
    default void onTimerTick(int remainingSeconds) {
    }

    /**
     * Invoked when {@code WORD_LOCKED|username|word|pointsEarned} is received.
     */
    default void onWordLocked(String username, String word, int pointsEarned) {
    }

    /**
     * Invoked when {@code WORD_REJECTED|word|reason} is received.
     */
    default void onWordRejected(String word, String reason) {
    }

    /**
     * Invoked when {@code SCOREBOARD_UPDATE|user1:score1:words1,user2:score2:words2} is received.
     */
    default void onScoreboardUpdate(List<PlayerScore> leaderboard) {
    }

    /**
     * Invoked when {@code MATCH_ENDED|winnerUsername|winnerScore|missedWord1,missedWord2,missedWord3} is received.
     */
    default void onMatchEnded(String winnerUsername, int winnerScore, List<String> missedWords) {
    }

    /**
     * Invoked when {@code 2X_ACTIVATED|username|success} is received.
     */
    default void onDoubleScoreActivated(String username, boolean success) {
    }

    /**
     * Invoked when {@code ERROR|reason} is received from the server.
     */
    default void onServerError(String reason) {
    }

    /**
     * Invoked if the connection to the server is lost or closed.
     */
    default void onConnectionClosed(String reason) {
    }
}

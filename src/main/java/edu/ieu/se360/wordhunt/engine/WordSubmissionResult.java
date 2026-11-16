package edu.ieu.se360.wordhunt.engine;

import java.io.Serializable;

/**
 * Represents the outcome of a player's word submission in {@link GameSession}.
 * Includes both the status enum and the points awarded (0 if rejected).
 */
public class WordSubmissionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Enumeration of all possible word submission statuses.
     */
    public enum Status {
        SUCCESS,
        ALREADY_CLAIMED,
        NOT_IN_DICTIONARY,
        INVALID_LETTERS,
        TOO_SHORT
    }

    // Convenience constants matching the status names directly
    public static final Status SUCCESS = Status.SUCCESS;
    public static final Status ALREADY_CLAIMED = Status.ALREADY_CLAIMED;
    public static final Status NOT_IN_DICTIONARY = Status.NOT_IN_DICTIONARY;
    public static final Status INVALID_LETTERS = Status.INVALID_LETTERS;
    public static final Status TOO_SHORT = Status.TOO_SHORT;

    private final Status status;
    private final String word;
    private final String username;
    private final int pointsAwarded;
    private final String claimedBy;
    private final String message;

    public WordSubmissionResult(Status status, String word, String username, int pointsAwarded, String claimedBy, String message) {
        this.status = status;
        this.word = word;
        this.username = username;
        this.pointsAwarded = pointsAwarded;
        this.claimedBy = claimedBy;
        this.message = message;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public String getWord() {
        return word;
    }

    public String getUsername() {
        return username;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    /**
     * Alias for {@link #getPointsAwarded()} for convenience.
     */
    public int getScore() {
        return pointsAwarded;
    }

    /**
     * Alias for {@link #getPointsAwarded()} for convenience.
     */
    public int getPoints() {
        return pointsAwarded;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "WordSubmissionResult{" +
                "status=" + status +
                ", word='" + word + '\'' +
                ", username='" + username + '\'' +
                ", pointsAwarded=" + pointsAwarded +
                ", claimedBy='" + claimedBy + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}

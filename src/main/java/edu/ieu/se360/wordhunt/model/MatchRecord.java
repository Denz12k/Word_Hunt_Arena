package edu.ieu.se360.wordhunt.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a completed match record stored in the database.
 * Implements Serializable so instances can be transmitted over Java RMI.
 */
public class MatchRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String winnerUsername;
    private int winnerScore;
    private String playedAt;
    private String playersSummary;

    public MatchRecord() {
    }

    public MatchRecord(int id, String winnerUsername, int winnerScore, String playedAt, String playersSummary) {
        this.id = id;
        this.winnerUsername = winnerUsername;
        this.winnerScore = winnerScore;
        this.playedAt = playedAt;
        this.playersSummary = playersSummary;
    }

    public MatchRecord(String winnerUsername, int winnerScore, String playedAt, String playersSummary) {
        this(0, winnerUsername, winnerScore, playedAt, playersSummary);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getWinnerUsername() {
        return winnerUsername;
    }

    public void setWinnerUsername(String winnerUsername) {
        this.winnerUsername = winnerUsername;
    }

    public int getWinnerScore() {
        return winnerScore;
    }

    public void setWinnerScore(int winnerScore) {
        this.winnerScore = winnerScore;
    }

    public String getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(String playedAt) {
        this.playedAt = playedAt;
    }

    public String getPlayersSummary() {
        return playersSummary;
    }

    public void setPlayersSummary(String playersSummary) {
        this.playersSummary = playersSummary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchRecord that = (MatchRecord) o;
        return id == that.id &&
                winnerScore == that.winnerScore &&
                Objects.equals(winnerUsername, that.winnerUsername) &&
                Objects.equals(playedAt, that.playedAt) &&
                Objects.equals(playersSummary, that.playersSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, winnerUsername, winnerScore, playedAt, playersSummary);
    }

    @Override
    public String toString() {
        return "MatchRecord{" +
                "id=" + id +
                ", winnerUsername='" + winnerUsername + '\'' +
                ", winnerScore=" + winnerScore +
                ", playedAt='" + playedAt + '\'' +
                ", playersSummary='" + playersSummary + '\'' +
                '}';
    }
}

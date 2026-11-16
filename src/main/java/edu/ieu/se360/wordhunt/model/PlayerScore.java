package edu.ieu.se360.wordhunt.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Tracks a player's live score and state within an active match.
 * Implements Serializable for network/RMI transmission and Comparable<PlayerScore>
 * for natural descending sorting (highest score first).
 */
public class PlayerScore implements Serializable, Comparable<PlayerScore> {
    private static final long serialVersionUID = 1L;

    private String username;
    private volatile int score;
    private volatile int wordsFoundCount;
    private volatile boolean doubleScoreActive;
    private volatile boolean hintUsed;
    private volatile boolean doubleScoreUsed;
    private volatile boolean shuffleUsed;

    public PlayerScore() {
    }

    public PlayerScore(String username) {
        this(username, 0, 0, false);
    }

    public PlayerScore(String username, int score, int wordsFoundCount, boolean doubleScoreActive) {
        this.username = username;
        this.score = score;
        this.wordsFoundCount = wordsFoundCount;
        this.doubleScoreActive = doubleScoreActive;
        this.hintUsed = false;
        this.doubleScoreUsed = false;
        this.shuffleUsed = false;
    }

    public synchronized void addPoints(int points) {
        this.score += points;
        this.wordsFoundCount++;
    }

    public synchronized void addScoreOnly(int points) {
        this.score += points;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getWordsFoundCount() {
        return wordsFoundCount;
    }

    public void setWordsFoundCount(int wordsFoundCount) {
        this.wordsFoundCount = wordsFoundCount;
    }

    public boolean isDoubleScoreActive() {
        return doubleScoreActive;
    }

    public void setDoubleScoreActive(boolean doubleScoreActive) {
        this.doubleScoreActive = doubleScoreActive;
    }

    public boolean isHintUsed() {
        return hintUsed;
    }

    public void setHintUsed(boolean hintUsed) {
        this.hintUsed = hintUsed;
    }

    public boolean isDoubleScoreUsed() {
        return doubleScoreUsed;
    }

    public void setDoubleScoreUsed(boolean doubleScoreUsed) {
        this.doubleScoreUsed = doubleScoreUsed;
    }

    public boolean isShuffleUsed() {
        return shuffleUsed;
    }

    public void setShuffleUsed(boolean shuffleUsed) {
        this.shuffleUsed = shuffleUsed;
    }

    /**
     * Compares PlayerScore objects in descending order of score,
     * then descending order of wordsFoundCount, then ascending alphabetical order of username.
     */
    @Override
    public int compareTo(PlayerScore other) {
        if (other == null) {
            return -1;
        }
        int scoreComparison = Integer.compare(other.score, this.score);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        int wordsComparison = Integer.compare(other.wordsFoundCount, this.wordsFoundCount);
        if (wordsComparison != 0) {
            return wordsComparison;
        }
        if (this.username == null && other.username == null) {
            return 0;
        }
        if (this.username == null) {
            return 1;
        }
        if (other.username == null) {
            return -1;
        }
        return this.username.compareToIgnoreCase(other.username);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerScore that = (PlayerScore) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "PlayerScore{" +
                "username='" + username + '\'' +
                ", score=" + score +
                ", wordsFoundCount=" + wordsFoundCount +
                ", doubleScoreActive=" + doubleScoreActive +
                '}';
    }
}

package edu.ieu.se360.wordhunt.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a registered user in the Word Hunt Arena system.
 * Implements Serializable so instances can be transmitted over Java RMI.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String username;
    private transient String passwordHash;
    private int totalScore;
    private int gamesPlayed;
    private int gamesWon;

    public User() {
    }

    public User(int id, String username, String passwordHash, int totalScore, int gamesPlayed, int gamesWon) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.totalScore = totalScore;
        this.gamesPlayed = gamesPlayed;
        this.gamesWon = gamesWon;
    }

    public User(String username, String passwordHash) {
        this(0, username, passwordHash, 0, 0, 0);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public int getGamesWon() {
        return gamesWon;
    }

    public void setGamesWon(int gamesWon) {
        this.gamesWon = gamesWon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", totalScore=" + totalScore +
                ", gamesPlayed=" + gamesPlayed +
                ", gamesWon=" + gamesWon +
                '}';
    }
}

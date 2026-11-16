package edu.ieu.se360.wordhunt.database;

import edu.ieu.se360.wordhunt.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Data Access Object for User persistence, authentication, statistics updates,
 * and leaderboard retrieval.
 */
public class UserDAO {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final DatabaseManager dbManager;

    public UserDAO() {
        this(DatabaseManager.getInstance());
    }

    public UserDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager != null ? dbManager : DatabaseManager.getInstance();
    }

    /**
     * Hashes a raw password using SHA-256.
     */
    public static String hashPassword(String password) {
        if (password == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Registers a new user with the given username and password.
     *
     * @param username the unique username
     * @param password the raw password
     * @return true if registration succeeded; false if username already exists or inputs are invalid
     */
    public synchronized boolean registerUser(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return false;
        }
        String trimmedUsername = username.trim();
        if (!USERNAME_PATTERN.matcher(trimmedUsername).matches()) {
            return false;
        }
        String hashedPassword = hashPassword(password);

        String checkSql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)";
        String insertSql = "INSERT INTO users (username, password_hash, total_score, games_played, games_won) VALUES (?, ?, 0, 0, 0)";
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, trimmedUsername);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return false;
                    }
                }
            }
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, trimmedUsername);
                pstmt.setString(2, hashedPassword);
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            }
        } catch (SQLException e) {
            // Duplicate username or constraint violation
            return false;
        }
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param username the username
     * @param password the raw password
     * @return the authenticated User object if credentials are valid, or null otherwise
     */
    public User authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return null;
        }
        String trimmedUsername = username.trim();
        String hashedPassword = hashPassword(password);

        String sql = "SELECT id, username, password_hash, total_score, games_played, games_won FROM users WHERE LOWER(username) = LOWER(?) AND password_hash = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUsername);
            pstmt.setString(2, hashedPassword);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error authenticating user '" + username + "': " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Updates a user's lifetime statistics after a match completes.
     * Increments games_played by 1, adds scoreToAdd to total_score, and increments games_won by 1 if won is true.
     *
     * @param username   the player's username
     * @param scoreToAdd the score earned in the match
     * @param won        true if the player won the match
     * @return true if the user record was found and updated
     */
    public synchronized boolean updateUserStats(String username, int scoreToAdd, boolean won) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String sql = """
                UPDATE users
                SET total_score = total_score + ?,
                    games_played = games_played + 1,
                    games_won = games_won + ?
                WHERE LOWER(username) = LOWER(?)
                """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Math.max(0, scoreToAdd));
            pstmt.setInt(2, won ? 1 : 0);
            pstmt.setString(3, username.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating stats for user '" + username + "': " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the top players ordered by total_score descending, then games_won descending.
     *
     * @param limit maximum number of players to return
     * @return list of top User records
     */
    public List<User> getTopPlayers(int limit) {
        int effectiveLimit = limit > 0 ? limit : 10;
        List<User> topPlayers = new ArrayList<>();
        String sql = """
                SELECT id, username, password_hash, total_score, games_played, games_won
                FROM users
                ORDER BY total_score DESC, games_won DESC, username ASC
                LIMIT ?
                """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, effectiveLimit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    User u = mapRowToUser(rs);
                    u.setPasswordHash("");
                    topPlayers.add(u);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching top players: " + e.getMessage(), e);
        }
        return topPlayers;
    }

    /**
     * Finds a user by username.
     */
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(getUserByUsername(username));
    }

    /**
     * Retrieves a User by username, or null if not found.
     */
    public User getUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String sql = "SELECT id, username, password_hash, total_score, games_played, games_won FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving user '" + username + "': " + e.getMessage(), e);
        }
        return null;
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getInt("total_score"),
                rs.getInt("games_played"),
                rs.getInt("games_won")
        );
    }
}

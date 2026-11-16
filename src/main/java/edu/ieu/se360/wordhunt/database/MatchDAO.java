package edu.ieu.se360.wordhunt.database;

import edu.ieu.se360.wordhunt.model.MatchRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Data Access Object for saving and querying match history records in SQLite.
 */
public class MatchDAO {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatabaseManager dbManager;

    public MatchDAO() {
        this(DatabaseManager.getInstance());
    }

    public MatchDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager != null ? dbManager : DatabaseManager.getInstance();
    }

    /**
     * Saves a completed match to the match_history table.
     *
     * @param winnerUsername the username of the match winner
     * @param winnerScore    the score achieved by the winner
     * @param summary        summary of all players and their scores in the match
     * @return true if the match record was successfully inserted
     */
    public synchronized boolean saveMatch(String winnerUsername, int winnerScore, String summary) {
        if (winnerUsername == null || winnerUsername.isBlank()) {
            return false;
        }
        String trimmedWinner = winnerUsername.trim();
        String playedAt = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String safeSummary = summary != null ? summary : "";

        String sql = "INSERT INTO match_history (winner_username, winner_score, played_at, players_summary) VALUES (?, ?, ?, ?)";
        String playerSql = "INSERT INTO match_players (match_id, username, score) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, trimmedWinner);
            pstmt.setInt(2, winnerScore);
            pstmt.setString(3, playedAt);
            pstmt.setString(4, safeSummary);
            int affected = pstmt.executeUpdate();
            if (affected <= 0) {
                return false;
            }

            int matchId = -1;
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    matchId = generatedKeys.getInt(1);
                }
            }
            if (matchId <= 0) {
                try (Statement idStmt = conn.createStatement();
                     ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()")) {
                    if (rs.next()) {
                        matchId = rs.getInt(1);
                    }
                }
            }

            if (matchId > 0) {
                Map<String, Map.Entry<String, Integer>> parsedPlayers = new LinkedHashMap<>();
                if (!safeSummary.isBlank()) {
                    String[] entries = safeSummary.split(",");
                    for (String entry : entries) {
                        String trimmedEntry = entry.trim();
                        if (trimmedEntry.isEmpty()) {
                            continue;
                        }
                        int colonIdx = trimmedEntry.indexOf(':');
                        if (colonIdx > 0) {
                            String pName = trimmedEntry.substring(0, colonIdx).trim();
                            String scorePart = trimmedEntry.substring(colonIdx + 1).trim();
                            int pScore = 0;
                            try {
                                pScore = Integer.parseInt(scorePart);
                            } catch (NumberFormatException ignored) {
                            }
                            if (!pName.isEmpty()) {
                                parsedPlayers.put(pName.toLowerCase(Locale.ROOT), Map.entry(pName, pScore));
                            }
                        }
                    }
                }
                if (!trimmedWinner.toLowerCase(Locale.ROOT).startsWith("none")
                        && !trimmedWinner.toLowerCase(Locale.ROOT).startsWith("tie:")
                        && !"N/A".equalsIgnoreCase(trimmedWinner)) {
                    parsedPlayers.putIfAbsent(trimmedWinner.toLowerCase(Locale.ROOT), Map.entry(trimmedWinner, winnerScore));
                }

                try (PreparedStatement playerStmt = conn.prepareStatement(playerSql)) {
                    for (Map.Entry<String, Integer> p : parsedPlayers.values()) {
                        playerStmt.setInt(1, matchId);
                        playerStmt.setString(2, p.getKey());
                        playerStmt.setInt(3, p.getValue());
                        playerStmt.addBatch();
                    }
                    playerStmt.executeBatch();
                }
            }
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Error saving match record: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all match history records in which the specified user participated
     * (joined via the match_players table).
     *
     * @param username the username to query
     * @return list of MatchRecord objects ordered from most recent to oldest
     */
    public List<MatchRecord> getUserMatchHistory(String username) {
        List<MatchRecord> history = new ArrayList<>();
        if (username == null || username.isBlank()) {
            return history;
        }
        String trimmed = username.trim();
        String sql = """
                SELECT DISTINCT mh.id, mh.winner_username, mh.winner_score, mh.played_at, mh.players_summary
                FROM match_history mh
                INNER JOIN match_players mp ON mh.id = mp.match_id
                WHERE LOWER(mp.username) = LOWER(?)
                ORDER BY mh.id DESC
                """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmed);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(mapRowToMatchRecord(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving match history for '" + username + "': " + e.getMessage(), e);
        }
        return history;
    }

    /**
     * Retrieves recent matches across all players.
     */
    public List<MatchRecord> getAllMatches() {
        List<MatchRecord> matches = new ArrayList<>();
        String sql = "SELECT id, winner_username, winner_score, played_at, players_summary FROM match_history ORDER BY id DESC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                matches.add(mapRowToMatchRecord(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving all matches: " + e.getMessage(), e);
        }
        return matches;
    }

    private MatchRecord mapRowToMatchRecord(ResultSet rs) throws SQLException {
        return new MatchRecord(
                rs.getInt("id"),
                rs.getString("winner_username"),
                rs.getInt("winner_score"),
                rs.getString("played_at"),
                rs.getString("players_summary")
        );
    }
}

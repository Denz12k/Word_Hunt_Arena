package edu.ieu.se360.wordhunt;

import edu.ieu.se360.wordhunt.database.DatabaseManager;
import edu.ieu.se360.wordhunt.database.MatchDAO;
import edu.ieu.se360.wordhunt.database.UserDAO;
import edu.ieu.se360.wordhunt.model.MatchRecord;
import edu.ieu.se360.wordhunt.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 Unit Tests for SQLite JDBC persistence via {@link UserDAO} and {@link MatchDAO}.
 */
@DisplayName("SQLite Database UserDAO & MatchDAO Unit Tests")
class DatabaseDAOTest {

    @TempDir
    Path tempDir;

    private DatabaseManager testDbManager;
    private UserDAO userDAO;
    private MatchDAO matchDAO;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("test_wordhunt.db");
        String customJdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        testDbManager = DatabaseManager.useTestDatabase(customJdbcUrl);
        testDbManager.clearAllTables();

        userDAO = new UserDAO(testDbManager);
        matchDAO = new MatchDAO(testDbManager);
    }

    @AfterEach
    void tearDown() {
        if (testDbManager != null) {
            testDbManager.clearAllTables();
            testDbManager.closeKeepAliveConnection();
        }
    }

    @Test
    @DisplayName("UserDAO registers users, prevents duplicate usernames, and authenticates with SHA-256 hash")
    void testUserRegistrationAndAuthentication() {
        assertTrue(userDAO.registerUser("Deniz", "secret123"), "New user registration should succeed");
        assertFalse(userDAO.registerUser("Deniz", "differentPass"), "Duplicate username registration must return false");
        assertFalse(userDAO.registerUser("deniz", "lowercasePass"), "Case-insensitive duplicate username registration must return false");

        // Invalid username formats (spaces, colons, commas, pipes, too short, too long) must be rejected
        assertFalse(userDAO.registerUser("ab", "pass123"), "Username shorter than 3 chars must be rejected");
        assertFalse(userDAO.registerUser("bad user", "pass123"), "Username with space must be rejected");
        assertFalse(userDAO.registerUser("bad:user", "pass123"), "Username with colon must be rejected");
        assertFalse(userDAO.registerUser("bad,user", "pass123"), "Username with comma must be rejected");
        assertFalse(userDAO.registerUser("bad|user", "pass123"), "Username with pipe must be rejected");

        User authenticated = userDAO.authenticate("Deniz", "secret123");
        assertNotNull(authenticated, "Valid credentials must return a User instance");
        assertEquals("Deniz", authenticated.getUsername());
        assertEquals(0, authenticated.getTotalScore());
        assertEquals(0, authenticated.getGamesPlayed());
        assertEquals(0, authenticated.getGamesWon());

        // Password must be stored as SHA-256 hash, never plain text
        assertNotEquals("secret123", authenticated.getPasswordHash());
        assertEquals(64, authenticated.getPasswordHash().length(), "SHA-256 hex hash must be 64 characters");

        assertNull(userDAO.authenticate("Deniz", "wrongPassword"), "Wrong password must fail authentication");
        assertNull(userDAO.authenticate("NonExistentUser", "secret123"), "Non-existent user must fail authentication");
    }

    @Test
    @DisplayName("UserDAO updates lifetime user stats and orders leaderboard descending by total_score")
    void testUpdateUserStatsAndLeaderboardOrdering() {
        userDAO.registerUser("Alice", "pass1");
        userDAO.registerUser("Bob", "pass2");
        userDAO.registerUser("Charlie", "pass3");

        assertTrue(userDAO.updateUserStats("Alice", 45, false));
        assertTrue(userDAO.updateUserStats("Bob", 120, true));
        assertTrue(userDAO.updateUserStats("Charlie", 80, false));
        assertTrue(userDAO.updateUserStats("Alice", 50, true)); // Alice now has 95 total_score, 2 played, 1 won

        List<User> topPlayers = userDAO.getTopPlayers(10);
        assertEquals(3, topPlayers.size());

        // Expected order: Bob (120), Alice (95), Charlie (80)
        assertEquals("Bob", topPlayers.get(0).getUsername());
        assertEquals(120, topPlayers.get(0).getTotalScore());
        assertEquals(1, topPlayers.get(0).getGamesWon());

        assertEquals("Alice", topPlayers.get(1).getUsername());
        assertEquals(95, topPlayers.get(1).getTotalScore());
        assertEquals(2, topPlayers.get(1).getGamesPlayed());
        assertEquals(1, topPlayers.get(1).getGamesWon());

        assertEquals("Charlie", topPlayers.get(2).getUsername());
        assertEquals(80, topPlayers.get(2).getTotalScore());
        assertEquals(0, topPlayers.get(2).getGamesWon());
    }

    @Test
    @DisplayName("MatchDAO saves match records and retrieves personal match history accurately")
    void testMatchDAOSaveAndQueryHistory() {
        assertTrue(matchDAO.saveMatch("Deniz", 110, "Deniz: 110, Ali: 75, Zeynep: 60"));
        assertTrue(matchDAO.saveMatch("Zeynep", 95, "Zeynep: 95, Deniz: 82"));
        assertTrue(matchDAO.saveMatch("Ali", 130, "Ali: 130, Burak: 90"));

        // Deniz participated in 2 matches (1 as winner, 1 in summary)
        List<MatchRecord> denizHistory = matchDAO.getUserMatchHistory("Deniz");
        assertEquals(2, denizHistory.size(), "Deniz should have 2 match records in history");
        assertEquals("Zeynep", denizHistory.get(0).getWinnerUsername(), "Most recent match should appear first");
        assertEquals("Deniz", denizHistory.get(1).getWinnerUsername());

        // Burak participated in 1 match (in players_summary)
        List<MatchRecord> burakHistory = matchDAO.getUserMatchHistory("Burak");
        assertEquals(1, burakHistory.size());
        assertEquals("Ali", burakHistory.get(0).getWinnerUsername());
        assertEquals(130, burakHistory.get(0).getWinnerScore());

        // Verify that a match with "Alice" does NOT pollute "Ali"'s match history
        assertTrue(matchDAO.saveMatch("Alice", 150, "Alice: 150, Burak: 100"));
        List<MatchRecord> aliHistory = matchDAO.getUserMatchHistory("Ali");
        assertEquals(2, aliHistory.size(), "Ali's history must not include Alice's match");
    }
}

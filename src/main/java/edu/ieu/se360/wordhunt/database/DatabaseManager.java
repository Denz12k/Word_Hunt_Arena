package edu.ieu.se360.wordhunt.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite JDBC connection (default: wordhunt.db) and initializes
 * the required database schema (users and match_history tables).
 * Supports custom or in-memory database URLs for JUnit testing.
 */
public class DatabaseManager {
    public static final String DEFAULT_DB_URL = "jdbc:sqlite:wordhunt.db";
    public static final String IN_MEMORY_SHARED_DB_URL = "jdbc:sqlite:file:wordhunt_test?mode=memory&cache=shared";

    private static volatile DatabaseManager instance;

    private String dbUrl;
    private Connection keepAliveConnection;

    public DatabaseManager() {
        this(DEFAULT_DB_URL);
    }

    public DatabaseManager(String dbUrl) {
        this.dbUrl = (dbUrl == null || dbUrl.isBlank()) ? DEFAULT_DB_URL : normalizeUrl(dbUrl);
        initializeDatabase();
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager(DEFAULT_DB_URL);
                }
            }
        }
        return instance;
    }

    /**
     * Switches the singleton DatabaseManager to use a custom JDBC URL (e.g., for testing)
     * and initializes the tables on that database.
     */
    public static synchronized DatabaseManager useTestDatabase(String customUrl) {
        if (instance != null) {
            instance.closeKeepAliveConnection();
        }
        String targetUrl = (customUrl == null || customUrl.isBlank() || ":memory:".equals(customUrl) || "jdbc:sqlite::memory:".equals(customUrl))
                ? IN_MEMORY_SHARED_DB_URL
                : normalizeUrl(customUrl);
        instance = new DatabaseManager(targetUrl);
        return instance;
    }

    /**
     * Resets the singleton DatabaseManager back to the default wordhunt.db.
     */
    public static synchronized void resetToDefault() {
        if (instance != null) {
            instance.closeKeepAliveConnection();
        }
        instance = new DatabaseManager(DEFAULT_DB_URL);
    }

    private static String normalizeUrl(String url) {
        if (url.startsWith("jdbc:sqlite:")) {
            if ("jdbc:sqlite::memory:".equals(url)) {
                return IN_MEMORY_SHARED_DB_URL;
            }
            return url;
        }
        if (":memory:".equals(url)) {
            return IN_MEMORY_SHARED_DB_URL;
        }
        return "jdbc:sqlite:" + url;
    }

    public synchronized void setDbUrl(String dbUrl) {
        closeKeepAliveConnection();
        this.dbUrl = normalizeUrl(dbUrl);
        initializeDatabase();
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /**
     * Creates the users and match_history tables if they do not already exist.
     */
    public final synchronized void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            if (dbUrl.contains("mode=memory") || dbUrl.contains(":memory:")) {
                if (keepAliveConnection == null || keepAliveConnection.isClosed()) {
                    keepAliveConnection = DriverManager.getConnection(dbUrl);
                }
            }
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                String createUsersTable = """
                        CREATE TABLE IF NOT EXISTS users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                            password_hash TEXT NOT NULL,
                            total_score INTEGER NOT NULL DEFAULT 0,
                            games_played INTEGER NOT NULL DEFAULT 0,
                            games_won INTEGER NOT NULL DEFAULT 0
                        );
                        """;

                String createMatchHistoryTable = """
                        CREATE TABLE IF NOT EXISTS match_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            winner_username TEXT NOT NULL,
                            winner_score INTEGER NOT NULL,
                            played_at TEXT NOT NULL,
                            players_summary TEXT NOT NULL
                        );
                        """;

                String createMatchPlayersTable = """
                        CREATE TABLE IF NOT EXISTS match_players (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            match_id INTEGER NOT NULL,
                            username TEXT NOT NULL COLLATE NOCASE,
                            score INTEGER NOT NULL,
                            FOREIGN KEY(match_id) REFERENCES match_history(id)
                        );
                        """;

                stmt.execute(createUsersTable);
                stmt.execute(createMatchHistoryTable);
                stmt.execute(createMatchPlayersTable);
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database (" + dbUrl + "): " + e.getMessage(), e);
        }
    }

    /**
     * Clears all records from users and match_history tables (useful for unit test setup/teardown).
     */
    public synchronized void clearAllTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM match_players;");
            stmt.executeUpdate("DELETE FROM match_history;");
            stmt.executeUpdate("DELETE FROM users;");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear database tables: " + e.getMessage(), e);
        }
    }

    public synchronized void closeKeepAliveConnection() {
        if (keepAliveConnection != null) {
            try {
                if (!keepAliveConnection.isClosed()) {
                    keepAliveConnection.close();
                }
            } catch (SQLException ignored) {
            } finally {
                keepAliveConnection = null;
            }
        }
    }
}

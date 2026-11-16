package edu.ieu.se360.wordhunt;

import edu.ieu.se360.wordhunt.database.DatabaseManager;
import edu.ieu.se360.wordhunt.database.MatchDAO;
import edu.ieu.se360.wordhunt.database.UserDAO;
import edu.ieu.se360.wordhunt.engine.DictionaryManager;
import edu.ieu.se360.wordhunt.engine.GameSession;
import edu.ieu.se360.wordhunt.model.MatchRecord;
import edu.ieu.se360.wordhunt.model.PlayerScore;
import edu.ieu.se360.wordhunt.model.User;
import edu.ieu.se360.wordhunt.network.GameClient;
import edu.ieu.se360.wordhunt.network.GameServer;
import edu.ieu.se360.wordhunt.network.Protocol;
import edu.ieu.se360.wordhunt.network.ServerMessageListener;
import edu.ieu.se360.wordhunt.rmi.AuthRemoteService;
import edu.ieu.se360.wordhunt.rmi.AuthRemoteServiceImpl;
import edu.ieu.se360.wordhunt.rmi.HintRemoteService;
import edu.ieu.se360.wordhunt.rmi.HintRemoteServiceImpl;
import edu.ieu.se360.wordhunt.rmi.LeaderboardRemoteService;
import edu.ieu.se360.wordhunt.rmi.LeaderboardRemoteServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 Integration Tests for Java RMI Services ({@link AuthRemoteService},
 * {@link LeaderboardRemoteService}, {@link HintRemoteService}) and TCP {@link Protocol} / {@link GameServer} / {@link GameClient}.
 */
@DisplayName("RMI Services & TCP Network Protocol Integration Tests")
class RmiAndNetworkIntegrationTest {

    @TempDir
    Path tempDir;

    private DatabaseManager testDbManager;
    private UserDAO userDAO;
    private MatchDAO matchDAO;

    private AuthRemoteServiceImpl authService;
    private LeaderboardRemoteServiceImpl leaderboardService;
    private HintRemoteServiceImpl hintService;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("test_rmi_wordhunt.db");
        testDbManager = DatabaseManager.useTestDatabase("jdbc:sqlite:" + dbFile.toAbsolutePath());
        testDbManager.clearAllTables();

        userDAO = new UserDAO(testDbManager);
        matchDAO = new MatchDAO(testDbManager);

        authService = new AuthRemoteServiceImpl(userDAO);
        leaderboardService = new LeaderboardRemoteServiceImpl(userDAO, matchDAO);
        hintService = new HintRemoteServiceImpl();
    }

    @AfterEach
    void tearDown() {
        unexportQuietly(authService);
        unexportQuietly(leaderboardService);
        unexportQuietly(hintService);
        if (testDbManager != null) {
            testDbManager.clearAllTables();
            testDbManager.closeKeepAliveConnection();
        }
    }

    private void unexportQuietly(UnicastRemoteObject obj) {
        if (obj != null) {
            try {
                UnicastRemoteObject.unexportObject(obj, true);
            } catch (Exception ignored) {
            }
        }
    }

    private int findFreePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    @Test
    @DisplayName("RMI Auth, Leaderboard, and Hint remote services operate accurately")
    void testRmiRemoteServicesEndToEnd() throws Exception {
        // 1. Test AuthRemoteService registration & login
        User registered = authService.register("EgePlayer", "pass123");
        assertNotNull(registered, "RMI register should return newly created User");
        assertEquals("EgePlayer", registered.getUsername());

        assertNull(authService.register("EgePlayer", "otherPass"), "Duplicate RMI registration should return null");

        User loggedIn = authService.login("EgePlayer", "pass123");
        assertNotNull(loggedIn, "RMI login with valid credentials should return User");
        assertNull(authService.login("EgePlayer", "wrongPass"), "RMI login with wrong password should return null");

        // 2. Test LeaderboardRemoteService
        userDAO.updateUserStats("EgePlayer", 85, true);
        matchDAO.saveMatch("EgePlayer", 85, "EgePlayer: 85");

        List<User> topPlayers = leaderboardService.getTopPlayers(5);
        assertEquals(1, topPlayers.size());
        assertEquals("EgePlayer", topPlayers.get(0).getUsername());
        assertEquals(85, topPlayers.get(0).getTotalScore());

        List<MatchRecord> history = leaderboardService.getMatchHistory("EgePlayer");
        assertEquals(1, history.size());
        assertEquals("EgePlayer", history.get(0).getWinnerUsername());

        // 3. Test HintRemoteService with an active GameSession
        List<Character> board = Arrays.asList('S', 'T', 'O', 'N', 'E', 'A', 'R', 'M', 'I', 'L', 'C', 'P');
        GameSession session = new GameSession(board);
        session.addPlayer("EgePlayer");
        hintService.setActiveSession(session);

        String firstHint = hintService.requestHint("EgePlayer");
        assertNotNull(firstHint, "First hint request during active match must return a formatted hint");
        assertTrue(firstHint.contains("_"), "Formatted hint should mask middle letters with underscores: " + firstHint);

        String secondHint = hintService.requestHint("EgePlayer");
        assertNull(secondHint, "Second hint request in the same match must be denied (1x per match)");
    }

    @Test
    @DisplayName("Protocol builds and parses all client/server TCP messages accurately")
    void testProtocolBuildingAndParsing() {
        // Test MATCH_STARTED building and board parsing
        List<Character> board = Arrays.asList('K', 'A', 'L', 'E', 'M', 'S', 'I', 'N', 'R', 'T', 'O', 'U');
        String matchStartedMsg = Protocol.buildMatchStarted(board, 90);
        assertEquals("MATCH_STARTED|K,A,L,E,M,S,I,N,R,T,O,U|90", matchStartedMsg);

        String[] parts = Protocol.splitMessage(matchStartedMsg);
        assertEquals(3, parts.length);
        assertEquals(Protocol.MSG_MATCH_STARTED, parts[0]);
        assertEquals(board, Protocol.parseBoardLetters(parts[1]));
        assertEquals(90, Protocol.parseIntSafe(parts[2], 0));

        // Test SCOREBOARD_UPDATE building and parsing
        List<PlayerScore> leaderboard = Arrays.asList(
                new PlayerScore("Deniz", 64, 5, false),
                new PlayerScore("Ali", 42, 4, false)
        );
        String sbMsg = Protocol.buildScoreboardUpdate(leaderboard);
        assertEquals("SCOREBOARD_UPDATE|Deniz:64:5,Ali:42:4", sbMsg);

        String[] sbParts = Protocol.splitMessage(sbMsg);
        List<PlayerScore> parsedScores = Protocol.parseScoreboard(sbParts[1]);
        assertEquals(2, parsedScores.size());
        assertEquals("Deniz", parsedScores.get(0).getUsername());
        assertEquals(64, parsedScores.get(0).getScore());
        assertEquals(5, parsedScores.get(0).getWordsFoundCount());

        // Test WORD_LOCKED & MATCH_ENDED messages
        String lockedMsg = Protocol.buildWordLocked("Deniz", "STONE", 10);
        assertEquals("WORD_LOCKED|Deniz|STONE|10", lockedMsg);

        String endedMsg = Protocol.buildMatchEnded("Deniz", 64, Arrays.asList("STREAM", "PLANET", "MASTER"));
        String[] endedParts = Protocol.splitMessage(endedMsg);
        assertEquals(Protocol.MSG_MATCH_ENDED, endedParts[0]);
        assertEquals("Deniz", endedParts[1]);
        assertEquals(64, Protocol.parseIntSafe(endedParts[2], 0));
        assertEquals(Arrays.asList("STREAM", "PLANET", "MASTER"), Protocol.parseCommaSeparatedList(endedParts[3]));
    }

    @Test
    @DisplayName("End-to-End TCP Socket Flow: GameServer + Two GameClients (JOIN_LOBBY, START_MATCH, SUBMIT_WORD, WORD_LOCKED)")
    void testEndToEndTcpSocketFlowWithTwoClients() throws Exception {
        int testPort = findFreePort();
        userDAO.registerUser("Deniz", "pass123");
        userDAO.registerUser("Zeynep", "pass456");

        GameServer server = new GameServer(testPort, 60, userDAO, matchDAO, null);
        server.startAsync();

        // Wait briefly for server socket to bind
        long deadline = System.currentTimeMillis() + 3000;
        while (!server.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertTrue(server.isRunning(), "GameServer must be running on test port");

        CountDownLatch bothInLobbyLatch = new CountDownLatch(2);
        CountDownLatch matchStartedLatch = new CountDownLatch(2);
        CountDownLatch wordLockedLatch = new CountDownLatch(2);
        CountDownLatch wordRejectedLatch = new CountDownLatch(1);
        AtomicReference<String> lockedWordHolder = new AtomicReference<>();
        AtomicReference<String> lockedByHolder = new AtomicReference<>();

        ServerMessageListener client1Listener = new ServerMessageListener() {
            @Override
            public void onLobbyUsersUpdated(List<String> usernames) {
                if (usernames.contains("Deniz") && usernames.contains("Zeynep")) {
                    bothInLobbyLatch.countDown();
                }
            }

            @Override
            public void onMatchStarted(List<Character> boardLetters, int durationSeconds) {
                matchStartedLatch.countDown();
            }

            @Override
            public void onWordLocked(String username, String word, int pointsEarned) {
                lockedByHolder.set(username);
                lockedWordHolder.set(word);
                wordLockedLatch.countDown();
            }
        };

        ServerMessageListener client2Listener = new ServerMessageListener() {
            @Override
            public void onLobbyUsersUpdated(List<String> usernames) {
                if (usernames.contains("Deniz") && usernames.contains("Zeynep")) {
                    bothInLobbyLatch.countDown();
                }
            }

            @Override
            public void onMatchStarted(List<Character> boardLetters, int durationSeconds) {
                matchStartedLatch.countDown();
            }

            @Override
            public void onWordLocked(String username, String word, int pointsEarned) {
                wordLockedLatch.countDown();
            }

            @Override
            public void onWordRejected(String word, String reason) {
                wordRejectedLatch.countDown();
            }
        };

        GameClient client1 = new GameClient("localhost", testPort, client1Listener);
        GameClient client2 = new GameClient("localhost", testPort, client2Listener);

        try {
            // 1. JOIN_LOBBY
            client1.connect("Deniz");
            client2.connect("Zeynep");
            assertTrue(bothInLobbyLatch.await(5, TimeUnit.SECONDS), "Both clients should receive LOBBY_USERS containing Deniz & Zeynep");

            // 2. START_MATCH
            client1.startMatch("Deniz");
            assertTrue(matchStartedLatch.await(5, TimeUnit.SECONDS), "Both clients should receive MATCH_STARTED broadcast");

            // Replace active session board with known letters to deterministically submit "CARPET"
            List<Character> deterministicBoard = Arrays.asList('C', 'A', 'R', 'P', 'E', 'T', 'S', 'O', 'N', 'I', 'L', 'M');
            GameSession knownSession = new GameSession(deterministicBoard);
            knownSession.addPlayer("Deniz");
            knownSession.addPlayer("Zeynep");
            server.setActiveSession(knownSession);

            // 3. SUBMIT_WORD -> WORD_LOCKED broadcast to both clients
            // Also verify socket identity enforcement: even if client1 spoofs another name in payload, server uses bound "Deniz"
            client1.submitWord("SpoofedUser", "CARPET");
            assertTrue(wordLockedLatch.await(5, TimeUnit.SECONDS), "Both clients must receive WORD_LOCKED broadcast");
            assertEquals("CARPET", lockedWordHolder.get());
            assertEquals("Deniz", lockedByHolder.get(), "Server must use socket-bound username 'Deniz', ignoring spoofed payload username");

            // 4. Second client submitting the same locked word receives WORD_REJECTED
            client2.submitWord("Zeynep", "CARPET");
            assertTrue(wordRejectedLatch.await(5, TimeUnit.SECONDS), "Second client submitting already-locked word must receive WORD_REJECTED");
        } finally {
            client1.disconnect();
            client2.disconnect();
            server.stop();
        }
    }

    @Test
    @DisplayName("endMatch() handles 0-point matches (no winner / 0 games_won) and Tie matches accurately")
    void testEndMatchZeroScoreAndTieHandling() throws Exception {
        int testPort = findFreePort();
        userDAO.registerUser("Player1", "pass123");
        userDAO.registerUser("Player2", "pass123");

        GameServer server = new GameServer(testPort, 60, userDAO, matchDAO, null);
        List<Character> board = Arrays.asList('C', 'A', 'R', 'P', 'E', 'T', 'S', 'O', 'N', 'I', 'L', 'M');

        // Case 1: 0-Point Match (Nobody found any words)
        GameSession zeroScoreSession = new GameSession(board);
        zeroScoreSession.addPlayer("Player1");
        zeroScoreSession.addPlayer("Player2");
        server.setActiveSession(zeroScoreSession);

        server.endMatch();

        User p1AfterZero = userDAO.getUserByUsername("Player1");
        User p2AfterZero = userDAO.getUserByUsername("Player2");
        assertEquals(1, p1AfterZero.getGamesPlayed());
        assertEquals(0, p1AfterZero.getGamesWon(), "0-score match must NOT increment games_won for Player1");
        assertEquals(1, p2AfterZero.getGamesPlayed());
        assertEquals(0, p2AfterZero.getGamesWon(), "0-score match must NOT increment games_won for Player2");

        List<MatchRecord> zeroMatches = matchDAO.getAllMatches();
        assertFalse(zeroMatches.isEmpty());
        assertEquals("None (No Words Found)", zeroMatches.get(0).getWinnerUsername());
        assertEquals(0, zeroMatches.get(0).getWinnerScore());

        // Case 2: Tie Match (Both players achieve the exact same positive top score)
        // "CAT" = C(3)+A(1)+T(1) = 5 pts; "CAP" = C(3)+A(1)+P(3) = 7 pts; "CAR" = C(3)+A(1)+R(1) = 5 pts
        GameSession tieSession = new GameSession(board);
        tieSession.addPlayer("Player1");
        tieSession.addPlayer("Player2");
        assertTrue(tieSession.submitWord("Player1", "CAT").isSuccess()); // 5 pts
        assertTrue(tieSession.submitWord("Player2", "CAR").isSuccess()); // 5 pts
        assertEquals(tieSession.getPlayerScore("Player1").getScore(), tieSession.getPlayerScore("Player2").getScore());

        server.setActiveSession(tieSession);
        server.endMatch();

        User p1AfterTie = userDAO.getUserByUsername("Player1");
        User p2AfterTie = userDAO.getUserByUsername("Player2");
        assertEquals(2, p1AfterTie.getGamesPlayed());
        assertEquals(1, p1AfterTie.getGamesWon(), "Tied top scorer Player1 must receive a win");
        assertEquals(2, p2AfterTie.getGamesPlayed());
        assertEquals(1, p2AfterTie.getGamesWon(), "Tied top scorer Player2 must receive a win");

        List<MatchRecord> afterTieMatches = matchDAO.getAllMatches();
        MatchRecord latestMatch = afterTieMatches.get(0);
        assertTrue(latestMatch.getWinnerUsername().startsWith("Tie: "),
                "Tie match winnerUsername should start with 'Tie: ', was: " + latestMatch.getWinnerUsername());
        assertTrue(latestMatch.getWinnerUsername().contains("Player1") && latestMatch.getWinnerUsername().contains("Player2"));
        assertEquals(5, latestMatch.getWinnerScore());
    }

    @Test
    @DisplayName("MatchDAO keeps 'Ali' and 'Alice' match histories strictly isolated")
    void testMatchDaoAliAndAliceHistoryIsolation() {
        matchDAO.saveMatch("Ali", 100, "Ali: 100, Burak: 60");
        matchDAO.saveMatch("Alice", 140, "Alice: 140, Deniz: 90");
        matchDAO.saveMatch("Deniz", 120, "Deniz: 120, Alice: 110");

        List<MatchRecord> aliHistory = matchDAO.getUserMatchHistory("Ali");
        List<MatchRecord> aliceHistory = matchDAO.getUserMatchHistory("Alice");

        assertEquals(1, aliHistory.size(), "Ali participated in exactly 1 match");
        assertEquals("Ali", aliHistory.get(0).getWinnerUsername());

        assertEquals(2, aliceHistory.size(), "Alice participated in exactly 2 matches");
        for (MatchRecord rec : aliceHistory) {
            assertTrue(rec.getPlayersSummary().contains("Alice"), "Alice's history must only contain Alice's matches");
        }
    }

    @Test
    @DisplayName("User object returned over RMI does not leak passwordHash and Dictionary contains common words like CARPET")
    void testUserDoesNotLeakPasswordHashAndDictionaryContainsCommonWords() throws Exception {
        User registered = authService.register("SecUser", "MySuperSecretPass!");
        assertNotNull(registered);
        assertTrue(registered.getPasswordHash() == null || registered.getPasswordHash().isEmpty(),
                "AuthRemoteService.register must not expose passwordHash");

        User loggedIn = authService.login("SecUser", "MySuperSecretPass!");
        assertNotNull(loggedIn);
        assertTrue(loggedIn.getPasswordHash() == null || loggedIn.getPasswordHash().isEmpty(),
                "AuthRemoteService.login must not expose passwordHash");

        // Also verify Java Serialization (transient passwordHash) over RMI wire stream
        User userWithSecret = new User(1, "SecUser", "hashed_secret_value", 50, 2, 1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(userWithSecret);
        }
        User deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (User) ois.readObject();
        }
        assertNull(deserialized.getPasswordHash(), "Transient passwordHash must be null after RMI serialization");

        // Verify DictionaryManager contains common English words such as CARPET
        DictionaryManager dict = DictionaryManager.getInstance();
        assertTrue(dict.isValidWord("CARPET"), "Dictionary must contain common word 'CARPET'");
        assertTrue(dict.isValidWord("MARKET"), "Dictionary must contain common word 'MARKET'");
        assertTrue(dict.isValidWord("PLANET"), "Dictionary must contain common word 'PLANET'");
    }
}

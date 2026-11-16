package edu.ieu.se360.wordhunt.network;

import edu.ieu.se360.wordhunt.config.ConfigManager;
import edu.ieu.se360.wordhunt.database.DatabaseManager;
import edu.ieu.se360.wordhunt.database.MatchDAO;
import edu.ieu.se360.wordhunt.database.UserDAO;
import edu.ieu.se360.wordhunt.engine.GameSession;
import edu.ieu.se360.wordhunt.engine.WordSubmissionResult;
import edu.ieu.se360.wordhunt.model.PlayerScore;
import edu.ieu.se360.wordhunt.rmi.RmiServerManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Multithreaded TCP Socket Game Server for Word Hunt Arena.
 * <ul>
 *     <li>Listens on a {@link ServerSocket} (default port 5555).</li>
 *     <li>Manages client connections using {@link Executors#newCachedThreadPool()} and {@link ClientHandler}.</li>
 *     <li>Manages the 90-second match countdown timer using a {@link ScheduledExecutorService},
 *         broadcasting {@code TIMER_TICK} every second.</li>
 *     <li>Persists match results via {@link UserDAO} and {@link MatchDAO} when the timer reaches 0
 *         and broadcasts {@code MATCH_ENDED}.</li>
 *     <li>Provides a {@code main} entry point that starts both the RMI Registry/Services and the TCP Socket Server.</li>
 * </ul>
 */
public class GameServer {
    public static final int DEFAULT_TCP_PORT = 5555;
    public static final int DEFAULT_MATCH_DURATION_SECONDS = 90;

    private final int tcpPort;
    private final int matchDurationSeconds;
    private final UserDAO userDAO;
    private final MatchDAO matchDAO;
    private final RmiServerManager rmiServerManager;

    private final ExecutorService clientThreadPool;
    private final ScheduledExecutorService timerScheduler;
    private final CopyOnWriteArrayList<ClientHandler> clients;
    private final ConcurrentHashMap<String, ClientHandler> lobbyClientsByUsername;

    private ServerSocket serverSocket;
    private volatile boolean running;
    private volatile GameSession activeSession;
    private ScheduledFuture<?> timerTask;
    private final AtomicInteger remainingSeconds;

    public GameServer() {
        this(
                ConfigManager.getInstance().getTcpPort(),
                ConfigManager.getInstance().getMatchDurationSeconds(),
                new UserDAO(),
                new MatchDAO(),
                new RmiServerManager()
        );
    }

    public GameServer(int tcpPort) {
        this(
                tcpPort,
                ConfigManager.getInstance().getMatchDurationSeconds(),
                new UserDAO(),
                new MatchDAO(),
                new RmiServerManager()
        );
    }

    public GameServer(int tcpPort, int matchDurationSeconds, UserDAO userDAO, MatchDAO matchDAO, RmiServerManager rmiServerManager) {
        this.tcpPort = tcpPort > 0 ? tcpPort : DEFAULT_TCP_PORT;
        this.matchDurationSeconds = matchDurationSeconds > 0 ? matchDurationSeconds : DEFAULT_MATCH_DURATION_SECONDS;
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
        this.matchDAO = matchDAO != null ? matchDAO : new MatchDAO();
        this.rmiServerManager = rmiServerManager;

        this.clientThreadPool = Executors.newCachedThreadPool();
        this.timerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WordHunt-MatchTimer");
            t.setDaemon(true);
            return t;
        });
        this.clients = new CopyOnWriteArrayList<>();
        this.lobbyClientsByUsername = new ConcurrentHashMap<>();
        this.remainingSeconds = new AtomicInteger(0);

        if (this.rmiServerManager != null) {
            this.rmiServerManager.setSessionSupplier(this::getActiveSession);
        }
    }

    /**
     * Starts the RMI services (if configured) and begins accepting TCP connections in a blocking loop.
     */
    public void start() throws IOException {
        if (running) {
            return;
        }
        DatabaseManager.getInstance().initializeDatabase();

        if (rmiServerManager != null && !rmiServerManager.isRunning()) {
            try {
                rmiServerManager.start();
                rmiServerManager.setSessionSupplier(this::getActiveSession);
            } catch (RemoteException e) {
                System.err.println("[GameServer] Warning: Could not start RMI Server: " + e.getMessage());
            }
        }

        serverSocket = new ServerSocket(tcpPort);
        running = true;
        System.out.println("==============================================================");
        System.out.println(" Word Hunt Arena - Server Started");
        System.out.println(" TCP Socket Port : " + tcpPort);
        if (rmiServerManager != null) {
            System.out.println(" RMI Registry Port: " + rmiServerManager.getPort());
        }
        System.out.println(" Match Duration   : " + matchDurationSeconds + " seconds");
        System.out.println(" Waiting for players to connect...");
        System.out.println("==============================================================");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Socket Server] Accepted connection from " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);
                clientThreadPool.execute(handler);
            } catch (SocketException e) {
                if (running) {
                    System.err.println("[Socket Server] Socket error while accepting client: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                if (running) {
                    System.err.println("[Socket Server] I/O error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Starts the TCP server asynchronously on a background thread (useful for tests or embedded launch).
     */
    public void startAsync() {
        Thread serverThread = new Thread(() -> {
            try {
                start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("[GameServer] Server stopped with error: " + e.getMessage());
                }
            }
        }, "WordHunt-AcceptorThread");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    // =========================================================================
    // Protocol Command Handlers
    // =========================================================================

    /**
     * Handles {@code JOIN_LOBBY|username}.
     * Rejects duplicate active sessions for the same username (case-insensitive).
     */
    public synchronized void handleJoinLobby(String username, ClientHandler handler) {
        if (username == null || username.isBlank() || handler == null) {
            return;
        }
        String trimmed = username.trim();

        // Check for an already active session with the same username (case-insensitive)
        for (Map.Entry<String, ClientHandler> entry : lobbyClientsByUsername.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmed)) {
                ClientHandler existingHandler = entry.getValue();
                if (existingHandler != null && existingHandler != handler && existingHandler.isConnected()) {
                    System.out.println("[Lobby] Rejected duplicate session for username: " + trimmed);
                    handler.sendMessage(Protocol.buildError("Username '" + trimmed + "' is already connected in the lobby."));
                    clients.remove(handler);
                    handler.disconnect();
                    return;
                }
                lobbyClientsByUsername.remove(entry.getKey());
            }
        }

        handler.setUsername(trimmed);
        lobbyClientsByUsername.put(trimmed, handler);
        clients.addIfAbsent(handler);

        System.out.println("[Lobby] Player joined lobby: " + trimmed + " (Total in lobby: " + lobbyClientsByUsername.size() + ")");
        broadcastLobbyUsers();

        // If a match is already active, add the late-joining player to the session and sync state
        GameSession session = this.activeSession;
        if (session != null && session.isActive()) {
            session.addPlayer(trimmed);
            if (rmiServerManager != null) {
                rmiServerManager.setActiveGameSession(session);
            }
            handler.sendMessage(Protocol.buildMatchStarted(session.getBoardLetters(), remainingSeconds.get()));
            broadcastScoreboardUpdate(session);
        }
    }

    /**
     * Handles {@code CHAT|username|message}.
     */
    public void handleChat(String username, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String safeUser = (username == null || username.isBlank()) ? "Player" : username.trim();
        System.out.println("[Chat] " + safeUser + ": " + message);
        broadcast(Protocol.buildChatMsg(safeUser, message));
    }

    /**
     * Handles {@code START_MATCH|username}.
     * Initializes a new {@link GameSession}, notifies RMI Hint service, broadcasts {@code MATCH_STARTED}
     * and initial {@code SCOREBOARD_UPDATE}, and starts the 90-second countdown timer.
     */
    public synchronized void handleStartMatch(String startedByUsername) {
        if (activeSession != null && activeSession.isActive()) {
            System.out.println("[Match] Match start requested by " + startedByUsername + ", but a match is already in progress.");
            return;
        }

        cancelCurrentTimer();

        GameSession newSession = new GameSession();
        for (String user : lobbyClientsByUsername.keySet()) {
            newSession.addPlayer(user);
        }
        if (startedByUsername != null && !startedByUsername.isBlank()) {
            newSession.addPlayer(startedByUsername.trim());
        }

        this.activeSession = newSession;
        if (rmiServerManager != null) {
            rmiServerManager.setActiveGameSession(newSession);
        }

        this.remainingSeconds.set(matchDurationSeconds);

        System.out.println("[Match] Match started by " + startedByUsername
                + " | Board: " + newSession.getBoardLetters()
                + " | Duration: " + matchDurationSeconds + "s");

        broadcast(Protocol.buildMatchStarted(newSession.getBoardLetters(), matchDurationSeconds));
        broadcastScoreboardUpdate(newSession);

        startCountdownTimer(newSession, matchDurationSeconds);
    }

    /**
     * Starts the 1-second periodic timer task using {@link ScheduledExecutorService}.
     */
    private void startCountdownTimer(GameSession session, int durationSeconds) {
        remainingSeconds.set(durationSeconds);
        timerTask = timerScheduler.scheduleAtFixedRate(() -> {
            try {
                if (activeSession != session || !session.isActive()) {
                    cancelCurrentTimer();
                    return;
                }
                int timeLeft = remainingSeconds.decrementAndGet();
                if (timeLeft >= 0) {
                    broadcast(Protocol.buildTimerTick(timeLeft));
                }
                if (timeLeft <= 0) {
                    endMatch();
                }
            } catch (Exception e) {
                System.err.println("[Match Timer] Error in timer tick: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Handles {@code SUBMIT_WORD|username|word}.
     * Validates the word via {@link GameSession#submitWord(String, String)}.
     * If accepted, broadcasts {@code WORD_LOCKED} and {@code SCOREBOARD_UPDATE}.
     * If rejected, sends {@code WORD_REJECTED} directly to the submitting client.
     */
    public synchronized void handleSubmitWord(String username, String word, ClientHandler sender) {
        GameSession session = this.activeSession;
        if (session == null || !session.isActive()) {
            if (sender != null) {
                sender.sendMessage(Protocol.buildWordRejected(word, "No active match is running."));
            }
            return;
        }

        WordSubmissionResult result = session.submitWord(username, word);
        if (result.isSuccess()) {
            System.out.println("[Match] WORD_LOCKED: " + result.getUsername()
                    + " -> " + result.getWord() + " (+" + result.getPointsAwarded() + " pts)");
            broadcast(Protocol.buildWordLocked(result.getUsername(), result.getWord(), result.getPointsAwarded()));
            broadcastScoreboardUpdate(session);
        } else {
            String reason = formatRejectionReason(result);
            System.out.println("[Match] WORD_REJECTED for " + username + " (" + result.getWord() + "): " + reason);
            if (sender != null) {
                sender.sendMessage(Protocol.buildWordRejected(result.getWord(), reason));
            }
        }
    }

    private String formatRejectionReason(WordSubmissionResult result) {
        if (result == null) {
            return "Invalid word";
        }
        if (result.getMessage() != null && !result.getMessage().isBlank()) {
            return result.getMessage();
        }
        return result.getStatus().name();
    }

    /**
     * Handles {@code ACTIVATE_2X|username}.
     */
    public synchronized void handleActivate2x(String username, ClientHandler sender) {
        GameSession session = this.activeSession;
        if (session == null || !session.isActive()) {
            return;
        }
        boolean activated = session.activateDoubleScorePowerUp(username);
        System.out.println("[Match] 2x Power-Up requested by " + username + " -> " + (activated ? "ACTIVATED" : "ALREADY_USED"));
        if (sender != null) {
            sender.sendMessage(Protocol.build2xActivated(username, activated));
        }
    }

    /**
     * Handles {@code LEAVE|username}.
     */
    public synchronized void handleLeave(String username, ClientHandler handler) {
        if (username != null && !username.isBlank()) {
            String trimmed = username.trim();
            lobbyClientsByUsername.entrySet().removeIf(e ->
                    e.getKey().equalsIgnoreCase(trimmed) && (handler == null || e.getValue() == handler));
            System.out.println("[Lobby] Player left: " + trimmed);
        }
        if (handler != null) {
            clients.remove(handler);
        }
        broadcastLobbyUsers();
    }

    /**
     * Called when a client socket closes or disconnects.
     */
    public synchronized void removeClientHandler(ClientHandler handler) {
        if (handler == null) {
            return;
        }
        clients.remove(handler);
        String user = handler.getUsername();
        if (user != null && !user.isBlank()) {
            boolean removed = lobbyClientsByUsername.entrySet().removeIf(e ->
                    e.getKey().equalsIgnoreCase(user) && e.getValue() == handler);
            if (removed) {
                System.out.println("[Lobby] Removed disconnected player: " + user);
                broadcastLobbyUsers();
            }
        }
    }

    /**
     * Concludes the active match, determines the winner (handling 0-score and tie scenarios),
     * updates player stats and match history in SQLite via {@link UserDAO} and {@link MatchDAO},
     * and broadcasts {@code MATCH_ENDED}.
     */
    public synchronized void endMatch() {
        GameSession session = this.activeSession;
        if (session == null || !session.isActive()) {
            return;
        }
        session.setActive(false);
        cancelCurrentTimer();

        List<PlayerScore> leaderboard = session.getSortedLeaderboard();
        String winnerUsername = "None (No Words Found)";
        int winnerScore = 0;
        List<String> tiedWinners = new ArrayList<>();

        if (!leaderboard.isEmpty()) {
            PlayerScore top = leaderboard.get(0);
            if (top.getScore() <= 0) {
                winnerUsername = "None (No Words Found)";
                winnerScore = 0;
            } else {
                winnerScore = top.getScore();
                for (PlayerScore ps : leaderboard) {
                    if (ps.getScore() == winnerScore) {
                        tiedWinners.add(ps.getUsername());
                    }
                }
                if (tiedWinners.size() > 1) {
                    winnerUsername = "Tie: " + String.join(" & ", tiedWinners);
                } else {
                    winnerUsername = top.getUsername();
                }
            }
        }

        // Persist player statistics and match record to SQLite
        try {
            for (PlayerScore ps : leaderboard) {
                boolean isWinner = winnerScore > 0 && tiedWinners.stream()
                        .anyMatch(w -> w.equalsIgnoreCase(ps.getUsername()));
                userDAO.updateUserStats(ps.getUsername(), ps.getScore(), isWinner);
            }
            if (!leaderboard.isEmpty()) {
                String summary = session.buildPlayersSummary();
                matchDAO.saveMatch(winnerUsername, winnerScore, summary);
            }
        } catch (Exception e) {
            System.err.println("[GameServer] Error saving match results to SQLite: " + e.getMessage());
        }

        List<String> missedWords = session.getMissedHighValueWords(5);
        String matchEndedMsg = Protocol.buildMatchEnded(winnerUsername, winnerScore, missedWords);

        System.out.println("[Match] Match Ended! Winner: " + winnerUsername + " (" + winnerScore + " pts) | Missed Words: " + missedWords);
        broadcast(matchEndedMsg);
    }

    private void cancelCurrentTimer() {
        if (timerTask != null && !timerTask.isDone()) {
            timerTask.cancel(false);
        }
        timerTask = null;
    }

    /**
     * Broadcasts a protocol message to all connected clients.
     */
    public void broadcast(String message) {
        if (message == null) {
            return;
        }
        for (ClientHandler client : clients) {
            if (client != null && client.isConnected()) {
                client.sendMessage(message);
            }
        }
    }

    private void broadcastLobbyUsers() {
        List<String> usernames = new ArrayList<>(lobbyClientsByUsername.keySet());
        Collections.sort(usernames, String.CASE_INSENSITIVE_ORDER);
        broadcast(Protocol.buildLobbyUsers(usernames));
    }

    private void broadcastScoreboardUpdate(GameSession session) {
        if (session != null) {
            broadcast(Protocol.buildScoreboardUpdate(session.getSortedLeaderboard()));
        }
    }

    /**
     * Stops the active match timer, closes all client connections, closes the {@link ServerSocket},
     * and stops the RMI server.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        cancelCurrentTimer();
        timerScheduler.shutdownNow();

        for (ClientHandler client : clients) {
            client.disconnect();
        }
        clients.clear();
        lobbyClientsByUsername.clear();

        clientThreadPool.shutdownNow();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        if (rmiServerManager != null && rmiServerManager.isRunning()) {
            rmiServerManager.stop();
        }
        System.out.println("[GameServer] Server shut down cleanly.");
    }

    public GameSession getActiveSession() {
        return activeSession;
    }

    public void setActiveSession(GameSession activeSession) {
        this.activeSession = activeSession;
        if (rmiServerManager != null) {
            rmiServerManager.setActiveGameSession(activeSession);
        }
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public boolean isRunning() {
        return running;
    }

    public Set<String> getLobbyUsernames() {
        return Collections.unmodifiableSet(lobbyClientsByUsername.keySet());
    }

    public RmiServerManager getRmiServerManager() {
        return rmiServerManager;
    }

    /**
     * Main entry point to start both the Java RMI Registry/Services and the Multithreaded TCP Socket Server.
     */
    public static void main(String[] args) {
        ConfigManager config = ConfigManager.getInstance();
        int tcpPort = config.getTcpPort();
        int rmiPort = config.getRmiPort();
        int duration = config.getMatchDurationSeconds();

        if (args != null && args.length >= 1) {
            tcpPort = Protocol.parseIntSafe(args[0], tcpPort);
        }
        if (args != null && args.length >= 2) {
            rmiPort = Protocol.parseIntSafe(args[1], rmiPort);
        }

        DatabaseManager.getInstance().initializeDatabase();
        UserDAO userDAO = new UserDAO();
        MatchDAO matchDAO = new MatchDAO();
        RmiServerManager rmiManager = new RmiServerManager(rmiPort, userDAO, matchDAO);

        GameServer server = new GameServer(tcpPort, duration, userDAO, matchDAO, rmiManager);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "WordHunt-ShutdownHook"));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("[GameServer] Fatal error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

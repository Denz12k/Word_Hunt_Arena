package edu.ieu.se360.wordhunt.network;

import edu.ieu.se360.wordhunt.config.ConfigManager;
import edu.ieu.se360.wordhunt.model.PlayerScore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TCP Socket client used by the Swing GUI to communicate with {@link GameServer}.
 * Spawns a dedicated background {@code Listener Thread} to read incoming messages
 * asynchronously and dispatch parsed events to registered {@link ServerMessageListener}s.
 */
public class GameClient {
    private String host;
    private int port;
    private String username;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private volatile boolean connected;

    private final List<ServerMessageListener> listeners = new CopyOnWriteArrayList<>();

    public GameClient() {
        this(ConfigManager.getInstance().getServerHost(), ConfigManager.getInstance().getTcpPort());
    }

    public GameClient(String host, int port) {
        this.host = (host == null || host.isBlank()) ? "localhost" : host.trim();
        this.port = port > 0 ? port : GameServer.DEFAULT_TCP_PORT;
    }

    public GameClient(String host, int port, ServerMessageListener listener) {
        this(host, port);
        if (listener != null) {
            this.listeners.add(listener);
        }
    }

    /**
     * Connects to the TCP {@link GameServer} and starts the background listener thread.
     */
    public synchronized void connect() throws IOException {
        if (connected) {
            return;
        }
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        connected = true;

        listenerThread = new Thread(this::listenLoop, "WordHunt-ClientListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Connects to the server and immediately sends {@code JOIN_LOBBY|username}.
     */
    public synchronized void connect(String username) throws IOException {
        this.username = username != null ? username.trim() : "";
        connect();
        if (!this.username.isEmpty()) {
            joinLobby(this.username);
        }
    }

    private void listenLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    dispatchServerMessage(trimmed);
                }
            }
            if (connected) {
                connected = false;
                notifyConnectionClosed("Server closed the connection.");
            }
        } catch (IOException e) {
            if (connected) {
                connected = false;
                notifyConnectionClosed("Connection lost: " + e.getMessage());
            }
        }
    }

    /**
     * Parses a raw message from the server and notifies all registered {@link ServerMessageListener}s.
     */
    private void dispatchServerMessage(String rawLine) {
        for (ServerMessageListener listener : listeners) {
            try {
                listener.onRawMessage(rawLine);
            } catch (Exception e) {
                System.err.println("[GameClient] Error in listener onRawMessage: " + e.getMessage());
            }
        }

        String[] parts = Protocol.splitMessage(rawLine);
        if (parts.length == 0) {
            return;
        }
        String type = parts[0].trim().toUpperCase(Locale.ENGLISH);

        switch (type) {
            case Protocol.MSG_LOBBY_USERS -> {
                String csv = parts.length > 1 ? parts[1] : "";
                List<String> users = Protocol.parseCommaSeparatedList(csv);
                for (ServerMessageListener l : listeners) {
                    l.onLobbyUsersUpdated(users);
                }
            }
            case Protocol.MSG_CHAT_MSG -> {
                String sender = parts.length > 1 ? parts[1] : "";
                String msg = parts.length > 2 ? parts[2] : "";
                for (ServerMessageListener l : listeners) {
                    l.onChatMessage(sender, msg);
                }
            }
            case Protocol.MSG_MATCH_STARTED -> {
                String lettersCsv = parts.length > 1 ? parts[1] : "";
                int duration = parts.length > 2 ? Protocol.parseIntSafe(parts[2], 90) : 90;
                List<Character> boardLetters = Protocol.parseBoardLetters(lettersCsv);
                for (ServerMessageListener l : listeners) {
                    l.onMatchStarted(boardLetters, duration);
                }
            }
            case Protocol.MSG_TIMER_TICK -> {
                int remaining = parts.length > 1 ? Protocol.parseIntSafe(parts[1], 0) : 0;
                for (ServerMessageListener l : listeners) {
                    l.onTimerTick(remaining);
                }
            }
            case Protocol.MSG_WORD_LOCKED -> {
                String user = parts.length > 1 ? parts[1] : "";
                String word = parts.length > 2 ? parts[2] : "";
                int points = parts.length > 3 ? Protocol.parseIntSafe(parts[3], 0) : 0;
                for (ServerMessageListener l : listeners) {
                    l.onWordLocked(user, word, points);
                }
            }
            case Protocol.MSG_WORD_REJECTED -> {
                String word = parts.length > 1 ? parts[1] : "";
                String reason = parts.length > 2 ? parts[2] : "Rejected";
                for (ServerMessageListener l : listeners) {
                    l.onWordRejected(word, reason);
                }
            }
            case Protocol.MSG_SCOREBOARD_UPDATE -> {
                String payload = parts.length > 1 ? parts[1] : "";
                List<PlayerScore> leaderboard = Protocol.parseScoreboard(payload);
                for (ServerMessageListener l : listeners) {
                    l.onScoreboardUpdate(leaderboard);
                }
            }
            case Protocol.MSG_MATCH_ENDED -> {
                String winner = parts.length > 1 ? parts[1] : "None";
                int winnerScore = parts.length > 2 ? Protocol.parseIntSafe(parts[2], 0) : 0;
                String missedCsv = parts.length > 3 ? parts[3] : "";
                List<String> missedWords = Protocol.parseCommaSeparatedList(missedCsv);
                for (ServerMessageListener l : listeners) {
                    l.onMatchEnded(winner, winnerScore, missedWords);
                }
            }
            case Protocol.MSG_2X_ACTIVATED -> {
                String user = parts.length > 1 ? parts[1] : "";
                boolean success = parts.length > 2 && Boolean.parseBoolean(parts[2].trim());
                for (ServerMessageListener l : listeners) {
                    l.onDoubleScoreActivated(user, success);
                }
            }
            case Protocol.MSG_ERROR -> {
                String reason = parts.length > 1 ? parts[1] : "Server error";
                for (ServerMessageListener l : listeners) {
                    l.onServerError(reason);
                }
            }
            default -> {
                // Unrecognized or custom message already delivered via onRawMessage
            }
        }
    }

    private void notifyConnectionClosed(String reason) {
        for (ServerMessageListener l : listeners) {
            try {
                l.onConnectionClosed(reason);
            } catch (Exception ignored) {
            }
        }
    }

    // =========================================================================
    // High-Level Client Actions
    // =========================================================================

    public void joinLobby(String username) {
        this.username = username != null ? username.trim() : "";
        sendRawMessage(Protocol.buildJoinLobby(this.username));
    }

    public void sendChatMessage(String username, String message) {
        sendRawMessage(Protocol.buildChat(username, message));
    }

    public void sendChatMessage(String message) {
        sendChatMessage(this.username, message);
    }

    public void startMatch(String username) {
        sendRawMessage(Protocol.buildStartMatch(username));
    }

    public void startMatch() {
        startMatch(this.username);
    }

    public void submitWord(String username, String word) {
        sendRawMessage(Protocol.buildSubmitWord(username, word));
    }

    public void submitWord(String word) {
        submitWord(this.username, word);
    }

    public void activateDoubleScore(String username) {
        sendRawMessage(Protocol.buildActivate2x(username));
    }

    public void activateDoubleScore() {
        activateDoubleScore(this.username);
    }

    public void leaveLobby(String username) {
        sendRawMessage(Protocol.buildLeave(username));
    }

    public void leaveLobby() {
        leaveLobby(this.username);
    }

    /**
     * Sends a raw protocol line to the server.
     */
    public synchronized void sendRawMessage(String rawLine) {
        if (connected && out != null && rawLine != null) {
            out.println(rawLine);
        }
    }

    /**
     * Sends {@code LEAVE} if a username is set and closes the socket connection cleanly.
     */
    public synchronized void disconnect() {
        if (!connected) {
            return;
        }
        try {
            if (username != null && !username.isBlank() && out != null) {
                out.println(Protocol.buildLeave(username));
            }
        } catch (Exception ignored) {
        }
        connected = false;
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        if (out != null) {
            out.close();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public void addMessageListener(ServerMessageListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void setMessageListener(ServerMessageListener listener) {
        listeners.clear();
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeMessageListener(ServerMessageListener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = (host == null || host.isBlank()) ? "localhost" : host.trim();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port > 0 ? port : GameServer.DEFAULT_TCP_PORT;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

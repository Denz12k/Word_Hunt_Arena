package edu.ieu.se360.wordhunt.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles a single connected TCP client on its own worker thread inside {@link GameServer}'s
 * {@code ExecutorService} thread pool.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private BufferedReader in;
    private PrintWriter out;
    private volatile String username;
    private final AtomicBoolean connected;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.connected = new AtomicBoolean(true);
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while (connected.get() && (line = in.readLine()) != null) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty()) {
                    handleClientCommand(trimmedLine);
                }
            }
        } catch (IOException e) {
            if (connected.get()) {
                System.out.println("[Socket Server] Client connection closed (" + (username != null ? username : socket.getRemoteSocketAddress()) + "): " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /**
     * Parses and routes an incoming command line from the client according to {@link Protocol}.
     * Always enforces socket session identity ({@code this.username} bound during {@code JOIN_LOBBY})
     * rather than trusting the username parameter inside subsequent commands.
     */
    private void handleClientCommand(String rawLine) {
        String[] parts = Protocol.splitMessage(rawLine);
        if (parts.length == 0) {
            return;
        }
        String command = parts[0].trim().toUpperCase(Locale.ENGLISH);

        if (Protocol.CMD_JOIN_LOBBY.equals(command)) {
            String user = parts.length > 1 ? parts[1].trim() : "";
            if (!user.isEmpty()) {
                server.handleJoinLobby(user, this);
            } else {
                sendMessage(Protocol.buildError("Username cannot be empty when joining lobby."));
            }
            return;
        }

        // Enforce authenticated socket identity for all post-join commands
        String boundUser = this.username;
        if (boundUser == null || boundUser.isBlank()) {
            sendMessage(Protocol.buildError("Must JOIN_LOBBY with a valid username before sending commands."));
            return;
        }

        switch (command) {
            case Protocol.CMD_CHAT -> {
                String message = parts.length > 2 ? parts[2] : (parts.length > 1 ? parts[1] : "");
                server.handleChat(boundUser, message);
            }
            case Protocol.CMD_START_MATCH -> {
                server.handleStartMatch(boundUser);
            }
            case Protocol.CMD_SUBMIT_WORD -> {
                String word = parts.length > 2 ? parts[2].trim() : (parts.length > 1 ? parts[1].trim() : "");
                server.handleSubmitWord(boundUser, word, this);
            }
            case Protocol.CMD_ACTIVATE_2X -> {
                server.handleActivate2x(boundUser, this);
            }
            case Protocol.CMD_LEAVE -> {
                server.handleLeave(boundUser, this);
                disconnect();
            }
            default -> {
                System.out.println("[Socket Server] Ignoring unrecognized command from " + boundUser + ": " + rawLine);
            }
        }
    }

    /**
     * Sends a single protocol line directly to this client in a thread-safe manner.
     */
    public synchronized void sendMessage(String message) {
        if (connected.get() && out != null && message != null) {
            out.println(message);
        }
    }

    /**
     * Closes the underlying socket and unregisters this handler from the server.
     * Uses {@link AtomicBoolean} and invokes {@code server.removeClientHandler(this)} outside
     * the {@code synchronized} block to prevent lock-ordering deadlocks between
     * {@code ClientHandler} and {@code GameServer}.
     */
    public void disconnect() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        server.removeClientHandler(this);
        synchronized (this) {
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
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public Socket getSocket() {
        return socket;
    }
}

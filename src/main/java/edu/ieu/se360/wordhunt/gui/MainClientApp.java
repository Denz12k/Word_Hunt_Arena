package edu.ieu.se360.wordhunt.gui;

import edu.ieu.se360.wordhunt.config.ConfigManager;
import edu.ieu.se360.wordhunt.model.PlayerScore;
import edu.ieu.se360.wordhunt.model.User;
import edu.ieu.se360.wordhunt.network.GameClient;
import edu.ieu.se360.wordhunt.network.ServerMessageListener;
import edu.ieu.se360.wordhunt.rmi.RmiClientHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

/**
 * Main Java Swing desktop client application frame ({@code MainClientApp} - {@link JFrame})
 * for Word Hunt Arena.
 * <ul>
 *     <li>Uses {@link CardLayout} to seamlessly switch between {@link LoginPanel},
 *         {@link LobbyPanel}, and {@link GameArenaPanel}.</li>
 *     <li>Implements {@link ServerMessageListener} to receive real-time TCP socket broadcasts
 *         and dispatches UI updates safely onto the Swing Event Dispatch Thread via
 *         {@link SwingUtilities#invokeLater(Runnable)}.</li>
 *     <li>Manages the shared {@link RmiClientHelper} and {@link GameClient} instances.</li>
 * </ul>
 */
public class MainClientApp extends JFrame implements ServerMessageListener {

    // CardLayout screen identifiers
    public static final String CARD_LOGIN = "LOGIN";
    public static final String CARD_LOBBY = "LOBBY";
    public static final String CARD_ARENA = "ARENA";

    // Modern Navy / Clean Light Gray-White Theme Constants
    public static final String FONT_FAMILY = "Segoe UI";
    public static final Color COLOR_NAVY_HEADER = new Color(15, 23, 42);    // #0F172A Deep Navy
    public static final Color COLOR_PRIMARY = new Color(37, 99, 235);       // #2563EB Royal Blue
    public static final Color COLOR_ACCENT_GREEN = new Color(22, 163, 74);  // #16A34A Emerald Green
    public static final Color COLOR_DANGER = new Color(220, 38, 38);        // #DC2626 Crimson Red
    public static final Color COLOR_BG = new Color(241, 245, 249);          // #F1F5F9 Clean Slate Light Gray
    public static final Color COLOR_CARD = Color.WHITE;                     // #FFFFFF Pure White Card
    public static final Color COLOR_BORDER = new Color(203, 213, 225);      // #CBD5E1 Subtle Card Border
    public static final Color COLOR_TEXT_DARK = new Color(30, 41, 59);      // #1E293B Dark Slate Text
    public static final Color COLOR_TEXT_MUTED = new Color(100, 116, 139);  // #64748B Muted Gray Text

    private final CardLayout cardLayout;
    private final JPanel cardContainer;

    private final LoginPanel loginPanel;
    private final LobbyPanel lobbyPanel;
    private final GameArenaPanel gameArenaPanel;

    private final RmiClientHelper rmiClientHelper;
    private final GameClient gameClient;

    private User currentUser;
    private String currentCard = CARD_LOGIN;

    public MainClientApp() {
        super("Word Hunt Arena — Multiplayer Real-Time Word Game");

        ConfigManager config = ConfigManager.getInstance();
        this.rmiClientHelper = new RmiClientHelper(config.getServerHost(), config.getRmiPort());
        this.gameClient = new GameClient(config.getServerHost(), config.getTcpPort(), this);

        this.cardLayout = new CardLayout();
        this.cardContainer = new JPanel(cardLayout);
        this.cardContainer.setBackground(COLOR_BG);

        this.loginPanel = new LoginPanel(this);
        this.lobbyPanel = new LobbyPanel(this);
        this.gameArenaPanel = new GameArenaPanel(this);

        initFrame();
    }

    private void initFrame() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1120, 760);
        setMinimumSize(new Dimension(960, 660));
        setLocationRelativeTo(null);

        cardContainer.add(loginPanel, CARD_LOGIN);
        cardContainer.add(lobbyPanel, CARD_LOBBY);
        cardContainer.add(gameArenaPanel, CARD_ARENA);

        setContentPane(cardContainer);
        showScreen(CARD_LOGIN);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownAndExit();
            }
        });
    }

    /**
     * Switches the active screen inside the {@link CardLayout}.
     */
    public void showScreen(String cardName) {
        if (SwingUtilities.isEventDispatchThread()) {
            cardLayout.show(cardContainer, cardName);
            currentCard = cardName;
        } else {
            SwingUtilities.invokeLater(() -> {
                cardLayout.show(cardContainer, cardName);
                currentCard = cardName;
            });
        }
    }

    /**
     * Updates the host, TCP port, and RMI port on both {@link RmiClientHelper} and {@link GameClient}.
     */
    public void configureConnectionEndpoints(String host, int tcpPort, int rmiPort) {
        ConfigManager cfg = ConfigManager.getInstance();
        cfg.setServerHost(host);
        cfg.setTcpPort(tcpPort);
        cfg.setRmiPort(rmiPort);

        rmiClientHelper.setHost(host);
        rmiClientHelper.setPort(rmiPort);

        if (gameClient.isConnected()) {
            gameClient.disconnect();
        }
        gameClient.setHost(host);
        gameClient.setPort(tcpPort);
    }

    /**
     * Connects the TCP Socket {@link GameClient} and sends {@code JOIN_LOBBY|username}.
     */
    public void connectSocketForUser(User user) throws IOException {
        this.currentUser = user;
        if (gameClient.isConnected()) {
            gameClient.disconnect();
        }
        gameClient.connect(user.getUsername());
    }

    /**
     * Transition callback invoked on the EDT after RMI authentication + TCP socket connection succeed.
     */
    public void onLoginSuccess(User authenticatedUser) {
        this.currentUser = authenticatedUser;
        setTitle("Word Hunt Arena — Player: " + authenticatedUser.getUsername());
        lobbyPanel.clearChat();
        lobbyPanel.appendSystemChatMessage("Welcome to Word Hunt Arena, " + authenticatedUser.getUsername() + "!");
        lobbyPanel.onEnterLobby(authenticatedUser);
        showScreen(CARD_LOBBY);
    }

    /**
     * Disconnects the active TCP session and returns to the {@link LoginPanel}.
     */
    public void logoutToLoginScreen() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                if (gameClient.isConnected()) {
                    gameClient.disconnect();
                }
                return null;
            }

            @Override
            protected void done() {
                currentUser = null;
                setTitle("Word Hunt Arena — Multiplayer Real-Time Word Game");
                loginPanel.resetFieldsForLogout();
                showScreen(CARD_LOGIN);
            }
        };
        worker.execute();
    }

    /**
     * Called when the user clicks "Back to Lobby" on the {@link ResultDialog}.
     */
    public void returnToLobbyAfterMatch() {
        showScreen(CARD_LOBBY);
        lobbyPanel.refreshRmiDataAsync();
    }

    private void shutdownAndExit() {
        try {
            if (gameClient != null && gameClient.isConnected()) {
                gameClient.disconnect();
            }
        } catch (Exception ignored) {
        }
        dispose();
        System.exit(0);
    }

    // =========================================================================
    // ServerMessageListener Callbacks (From GameClient Background Thread)
    // =========================================================================

    @Override
    public void onLobbyUsersUpdated(List<String> usernames) {
        lobbyPanel.updateLobbyUsers(usernames);
    }

    @Override
    public void onChatMessage(String username, String message) {
        lobbyPanel.appendChatMessage(username, message);
    }

    @Override
    public void onMatchStarted(List<Character> boardLetters, int durationSeconds) {
        SwingUtilities.invokeLater(() -> {
            gameArenaPanel.startNewMatch(boardLetters, durationSeconds);
            showScreen(CARD_ARENA);
        });
    }

    @Override
    public void onTimerTick(int remainingSeconds) {
        gameArenaPanel.updateTimerTick(remainingSeconds);
    }

    @Override
    public void onWordLocked(String username, String word, int pointsEarned) {
        gameArenaPanel.onWordLocked(username, word, pointsEarned);
    }

    @Override
    public void onWordRejected(String word, String reason) {
        gameArenaPanel.onWordRejected(word, reason);
    }

    @Override
    public void onScoreboardUpdate(List<PlayerScore> leaderboard) {
        gameArenaPanel.updateScoreboard(leaderboard);
    }

    @Override
    public void onDoubleScoreActivated(String username, boolean success) {
        gameArenaPanel.onDoubleScoreActivated(username, success);
    }

    @Override
    public void onMatchEnded(String winnerUsername, int winnerScore, List<String> missedWords) {
        SwingUtilities.invokeLater(() -> {
            gameArenaPanel.disableMatchControls();
            gameArenaPanel.updateTimerTick(0);
            List<PlayerScore> finalStandings = gameArenaPanel.getLatestLeaderboard();
            ResultDialog dialog = new ResultDialog(this, winnerUsername, winnerScore, finalStandings, missedWords);
            dialog.setVisible(true);
        });
    }

    @Override
    public void onServerError(String reason) {
        SwingUtilities.invokeLater(() -> {
            if (CARD_LOBBY.equals(currentCard)) {
                lobbyPanel.appendSystemChatMessage("Server Alert: " + reason);
            }
            JOptionPane.showMessageDialog(
                    this,
                    reason,
                    "Server Message",
                    JOptionPane.WARNING_MESSAGE
            );
        });
    }

    @Override
    public void onConnectionClosed(String reason) {
        SwingUtilities.invokeLater(() -> {
            if (currentUser != null && !CARD_LOGIN.equals(currentCard)) {
                JOptionPane.showMessageDialog(
                        this,
                        "Disconnected from server: " + reason,
                        "Connection Closed",
                        JOptionPane.WARNING_MESSAGE
                );
                currentUser = null;
                setTitle("Word Hunt Arena — Multiplayer Real-Time Word Game");
                loginPanel.resetFieldsForLogout();
                loginPanel.showStatus("Disconnected: " + reason, COLOR_DANGER);
                showScreen(CARD_LOGIN);
            }
        });
    }

    // =========================================================================
    // Shared UI Factory Helpers & Getters
    // =========================================================================

    /**
     * Creates a consistently styled flat modern Swing button with the specified background color.
     */
    public static JButton createPrimaryButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(FONT_FAMILY, Font.BOLD, 14));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setBorder(new EmptyBorder(10, 18, 10, 18));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public RmiClientHelper getRmiClientHelper() {
        return rmiClientHelper;
    }

    public GameClient getGameClient() {
        return gameClient;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public String getCurrentUsername() {
        return currentUser != null ? currentUser.getUsername() : "";
    }

    public LoginPanel getLoginPanel() {
        return loginPanel;
    }

    public LobbyPanel getLobbyPanel() {
        return lobbyPanel;
    }

    public GameArenaPanel getGameArenaPanel() {
        return gameArenaPanel;
    }

    /**
     * Application entry point for launching the Word Hunt Arena Swing Desktop Client.
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            MainClientApp app = new MainClientApp();
            app.setVisible(true);
        });
    }
}

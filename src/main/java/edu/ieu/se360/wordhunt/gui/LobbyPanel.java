package edu.ieu.se360.wordhunt.gui;

import edu.ieu.se360.wordhunt.model.MatchRecord;
import edu.ieu.se360.wordhunt.model.User;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * Lobby screen of Word Hunt Arena.
 * <ul>
 *     <li>Left/Center: All-Time Leaderboard ({@link JTable}) and Personal Match History ({@link JTable})
 *         queried over Java RMI ({@code LeaderboardRemoteService}) with a Refresh button.</li>
 *     <li>Right: Connected Lobby Players list ({@link JList}) and Real-Time Lobby Chat ({@link JTextArea} + input).</li>
 *     <li>Bottom: Prominent "START MATCH (90s)" button that broadcasts match start to all connected players.</li>
 * </ul>
 */
public class LobbyPanel extends JPanel {

    private final MainClientApp mainApp;

    private JLabel welcomeLabel;
    private JButton refreshButton;
    private JButton logoutButton;

    // Leaderboard Table
    private DefaultTableModel leaderboardModel;
    private JTable leaderboardTable;

    // Personal Match History Table
    private DefaultTableModel historyModel;
    private JTable historyTable;

    // Connected Players List
    private DefaultListModel<String> lobbyUsersModel;
    private JList<String> lobbyUsersList;
    private JLabel onlineCountLabel;

    // Live Lobby Chat
    private JTextArea chatArea;
    private JTextField chatInputField;
    private JButton sendChatButton;

    // Start Match Button
    private JButton startMatchButton;

    public LobbyPanel(MainClientApp mainApp) {
        this.mainApp = mainApp;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(16, 16));
        setBackground(MainClientApp.COLOR_BG);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. Top Header Bar
        add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. Center Split / Grid Content (Left/Center: Leaderboards, Right: Online Players & Chat)
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createLeftStatsPanel(),
                createRightCommunityPanel());
        centerSplit.setResizeWeight(0.60);
        centerSplit.setDividerLocation(640);
        centerSplit.setDividerSize(8);
        centerSplit.setBorder(null);
        centerSplit.setOpaque(false);
        add(centerSplit, BorderLayout.CENTER);

        // 3. Bottom Start Match Bar
        add(createBottomActionPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(MainClientApp.COLOR_NAVY_HEADER);
        header.setBorder(new EmptyBorder(14, 22, 14, 22));

        JPanel leftTitleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftTitleBox.setOpaque(false);

        JLabel title = new JLabel("WORD HUNT ARENA — LOBBY");
        title.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        welcomeLabel = new JLabel("Player: -");
        welcomeLabel.setFont(new Font(MainClientApp.FONT_FAMILY, Font.PLAIN, 14));
        welcomeLabel.setForeground(new Color(185, 210, 245));

        leftTitleBox.add(title);
        leftTitleBox.add(new JLabel(" | ") {{
            setForeground(new Color(120, 150, 190));
        }});
        leftTitleBox.add(welcomeLabel);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightButtons.setOpaque(false);

        refreshButton = MainClientApp.createPrimaryButton("↻ Refresh Stats (RMI)", MainClientApp.COLOR_PRIMARY);
        refreshButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 13));
        refreshButton.addActionListener(e -> refreshRmiDataAsync());

        logoutButton = MainClientApp.createPrimaryButton("Logout", MainClientApp.COLOR_DANGER);
        logoutButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 13));
        logoutButton.addActionListener(e -> mainApp.logoutToLoginScreen());

        rightButtons.add(refreshButton);
        rightButtons.add(logoutButton);

        header.add(leftTitleBox, BorderLayout.WEST);
        header.add(rightButtons, BorderLayout.EAST);
        return header;
    }

    private JPanel createLeftStatsPanel() {
        JPanel container = new JPanel(new GridLayout(2, 1, 0, 14));
        container.setOpaque(false);

        // Top Card: All-Time Leaderboard
        JPanel leaderboardCard = createSectionCard("[*] All-Time Leaderboard (Top Players via RMI)");
        String[] lbCols = {"Rank", "Player", "Total Score", "Matches Played", "Matches Won"};
        leaderboardModel = new DefaultTableModel(lbCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        leaderboardTable = createStyledTable(leaderboardModel);
        leaderboardTable.getColumnModel().getColumn(0).setMaxWidth(65);
        leaderboardCard.add(new JScrollPane(leaderboardTable), BorderLayout.CENTER);

        // Bottom Card: Personal Match History
        JPanel historyCard = createSectionCard("[*] Personal Match History (RMI)");
        String[] histCols = {"Match ID", "Winner", "Winning Score", "Players Summary", "Played At"};
        historyModel = new DefaultTableModel(histCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = createStyledTable(historyModel);
        historyTable.getColumnModel().getColumn(0).setMaxWidth(80);
        historyTable.getColumnModel().getColumn(2).setMaxWidth(115);
        historyCard.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        container.add(leaderboardCard);
        container.add(historyCard);
        return container;
    }

    private JPanel createRightCommunityPanel() {
        JPanel rightContainer = new JPanel(new BorderLayout(0, 14));
        rightContainer.setOpaque(false);

        // Top: Connected Players in Lobby
        JPanel usersCard = createSectionCard("[*] Connected Players in Room");
        usersCard.setPreferredSize(new Dimension(340, 200));

        onlineCountLabel = new JLabel("Online: 0 players");
        onlineCountLabel.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 12));
        onlineCountLabel.setForeground(MainClientApp.COLOR_ACCENT_GREEN);
        onlineCountLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        usersCard.add(onlineCountLabel, BorderLayout.NORTH);

        lobbyUsersModel = new DefaultListModel<>();
        lobbyUsersList = new JList<>(lobbyUsersModel);
        lobbyUsersList.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        lobbyUsersList.setBackground(new Color(248, 250, 252));
        lobbyUsersList.setForeground(MainClientApp.COLOR_TEXT_DARK);
        lobbyUsersList.setFixedCellHeight(28);
        lobbyUsersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String name = value != null ? value.toString() : "";
                String currentUsername = mainApp.getCurrentUsername();
                if (name.equalsIgnoreCase(currentUsername)) {
                    label.setText("  ● " + name + " (You)");
                    label.setForeground(MainClientApp.COLOR_PRIMARY);
                } else {
                    label.setText("  ● " + name);
                    label.setForeground(MainClientApp.COLOR_TEXT_DARK);
                }
                label.setBorder(new EmptyBorder(2, 6, 2, 6));
                return label;
            }
        });
        usersCard.add(new JScrollPane(lobbyUsersList), BorderLayout.CENTER);

        // Bottom: Live Lobby Chat
        JPanel chatCard = createSectionCard("[*] Live Lobby Chat");
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font(MainClientApp.FONT_FAMILY, Font.PLAIN, 13));
        chatArea.setBackground(new Color(248, 250, 252));
        chatArea.setForeground(MainClientApp.COLOR_TEXT_DARK);
        chatArea.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatCard.add(chatScroll, BorderLayout.CENTER);

        JPanel chatInputPanel = new JPanel(new BorderLayout(8, 0));
        chatInputPanel.setOpaque(false);
        chatInputPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

        chatInputField = new JTextField();
        chatInputField.setFont(new Font(MainClientApp.FONT_FAMILY, Font.PLAIN, 13));
        chatInputField.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(7, 10, 7, 10)
        ));

        sendChatButton = MainClientApp.createPrimaryButton("Send", MainClientApp.COLOR_PRIMARY);
        sendChatButton.setPreferredSize(new Dimension(85, 36));

        ActionListener sendChatAction = e -> sendChatMessage();
        chatInputField.addActionListener(sendChatAction);
        sendChatButton.addActionListener(sendChatAction);

        chatInputPanel.add(chatInputField, BorderLayout.CENTER);
        chatInputPanel.add(sendChatButton, BorderLayout.EAST);
        chatCard.add(chatInputPanel, BorderLayout.SOUTH);

        rightContainer.add(usersCard, BorderLayout.NORTH);
        rightContainer.add(chatCard, BorderLayout.CENTER);
        return rightContainer;
    }

    private JPanel createBottomActionPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(MainClientApp.COLOR_CARD);
        bottomPanel.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(12, 20, 12, 20)
        ));

        JLabel infoLabel = new JLabel("Ready to compete? Pressing START MATCH launches the 90-second arena for everyone in the lobby!");
        infoLabel.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 13));
        infoLabel.setForeground(MainClientApp.COLOR_TEXT_MUTED);

        startMatchButton = MainClientApp.createPrimaryButton(">> START MATCH (90s)", MainClientApp.COLOR_ACCENT_GREEN);
        startMatchButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 16));
        startMatchButton.setPreferredSize(new Dimension(260, 48));
        startMatchButton.addActionListener(e -> {
            if (mainApp.getGameClient() != null && mainApp.getGameClient().isConnected()) {
                mainApp.getGameClient().startMatch(mainApp.getCurrentUsername());
            } else {
                JOptionPane.showMessageDialog(this,
                        "Not connected to the TCP Game Server!",
                        "Connection Error",
                        JOptionPane.WARNING_MESSAGE);
            }
        });

        bottomPanel.add(infoLabel, BorderLayout.WEST);
        bottomPanel.add(startMatchButton, BorderLayout.EAST);
        return bottomPanel;
    }

    private JPanel createSectionCard(String titleText) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(MainClientApp.COLOR_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JPanel titleBanner = new JPanel(new BorderLayout());
        titleBanner.setBackground(MainClientApp.COLOR_NAVY_HEADER);
        titleBanner.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel title = new JLabel(titleText);
        title.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        titleBanner.add(title, BorderLayout.WEST);

        card.add(titleBanner, BorderLayout.NORTH);
        return card;
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(new Font(MainClientApp.FONT_FAMILY, Font.PLAIN, 13));
        table.setRowHeight(26);
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(MainClientApp.COLOR_TEXT_DARK);
        table.setGridColor(new Color(226, 232, 240));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 13));
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(MainClientApp.COLOR_NAVY_HEADER);
        header.setPreferredSize(new Dimension(header.getWidth(), 30));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        if (table.getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        }
        return table;
    }

    private void sendChatMessage() {
        String text = chatInputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (mainApp.getGameClient() != null && mainApp.getGameClient().isConnected()) {
            mainApp.getGameClient().sendChatMessage(mainApp.getCurrentUsername(), text);
            chatInputField.setText("");
        }
    }

    /**
     * Updates the header label and asynchronously fetches All-Time Leaderboard and Personal Match History
     * over Java RMI using a {@link SwingWorker}.
     */
    public void onEnterLobby(User user) {
        if (user != null) {
            welcomeLabel.setText("Logged in as: " + user.getUsername());
        }
        refreshRmiDataAsync();
    }

    public void refreshRmiDataAsync() {
        final String currentUsername = mainApp.getCurrentUsername();
        refreshButton.setEnabled(false);
        refreshButton.setText("Refreshing...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private List<User> topPlayers = Collections.emptyList();
            private List<MatchRecord> matchHistory = Collections.emptyList();
            private String errorMsg = null;

            @Override
            protected Void doInBackground() {
                try {
                    topPlayers = mainApp.getRmiClientHelper().getTopPlayers(20);
                    if (currentUsername != null && !currentUsername.isBlank()) {
                        matchHistory = mainApp.getRmiClientHelper().getMatchHistory(currentUsername);
                    }
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                refreshButton.setText("↻ Refresh Stats (RMI)");

                leaderboardModel.setRowCount(0);
                int rank = 1;
                for (User u : topPlayers) {
                    leaderboardModel.addRow(new Object[]{
                            "#" + rank++,
                            u.getUsername(),
                            u.getTotalScore(),
                            u.getGamesPlayed(),
                            u.getGamesWon()
                    });
                }

                historyModel.setRowCount(0);
                for (MatchRecord record : matchHistory) {
                    historyModel.addRow(new Object[]{
                            "#" + record.getId(),
                            record.getWinnerUsername(),
                            record.getWinnerScore() + " pts",
                            record.getPlayersSummary(),
                            record.getPlayedAt()
                    });
                }

                if (errorMsg != null) {
                    appendSystemChatMessage("Could not refresh RMI stats: " + errorMsg);
                }
            }
        };
        worker.execute();
    }

    public void updateLobbyUsers(List<String> usernames) {
        SwingUtilities.invokeLater(() -> {
            lobbyUsersModel.clear();
            if (usernames != null) {
                for (String u : usernames) {
                    lobbyUsersModel.addElement(u);
                }
                onlineCountLabel.setText("Online: " + usernames.size() + (usernames.size() == 1 ? " player" : " players"));
            } else {
                onlineCountLabel.setText("Online: 0 players");
            }
        });
    }

    public void appendChatMessage(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[" + sender + "]: " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    public void appendSystemChatMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("★ [System]: " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    public void clearChat() {
        SwingUtilities.invokeLater(() -> chatArea.setText(""));
    }
}

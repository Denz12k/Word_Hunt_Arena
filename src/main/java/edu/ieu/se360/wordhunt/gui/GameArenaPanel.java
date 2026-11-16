package edu.ieu.se360.wordhunt.gui;

import edu.ieu.se360.wordhunt.engine.DictionaryManager;
import edu.ieu.se360.wordhunt.model.PlayerScore;
import edu.ieu.se360.wordhunt.reflection.ReflectionRuleLoader;
import edu.ieu.se360.wordhunt.reflection.ShufflePowerUp;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Live 90-second Game Arena screen ({@code GameArenaPanel}) for Word Hunt Arena.
 * <ul>
 *     <li>Top Bar: 90s visual countdown {@link JProgressBar} (turns red in the final 15s) + live status notification banner.</li>
 *     <li>Center: 12-Letter Board (2x6 grid of custom letter tiles displaying the big letter and bottom-right difficulty points),
 *         word submission {@link JTextField} + Submit button, and 3 Power-Up buttons (Hint via RMI, 2x Score via Socket, Shuffle via Reflection/Collections.shuffle).</li>
 *     <li>Side/Bottom Panels: Live Match Scoreboard ({@link JTable}) and Locked Words feed ({@link DefaultListModel} + {@link JList}).</li>
 * </ul>
 */
public class GameArenaPanel extends JPanel {

    private final MainClientApp mainApp;
    private final DictionaryManager dictionaryManager;
    private final ReflectionRuleLoader reflectionRuleLoader;

    // Top Bar Components
    private JProgressBar timerProgressBar;
    private JLabel statusBannerLabel;
    private JLabel hintDisplayLabel;
    private JButton leaveMatchButton;

    // 12-Letter Board (2x6 Grid)
    private final List<LetterTileButton> tileButtons = new ArrayList<>(12);
    private List<Character> currentBoardLetters = new ArrayList<>();
    private int matchDurationSeconds = 90;

    // Word Input & Power-Up Controls
    private JTextField wordInputField;
    private JButton submitWordButton;
    private JButton clearWordButton;

    private JButton hintButton;
    private JButton doubleScoreButton;
    private JButton shuffleButton;

    // Side Panels: Live Match Scoreboard & Locked Words List
    private DefaultTableModel liveScoreboardModel;
    private JTable liveScoreboardTable;

    private DefaultListModel<String> lockedWordsListModel;
    private JList<String> lockedWordsList;

    // Snapshot of latest scoreboard for ResultDialog
    private List<PlayerScore> latestLeaderboard = Collections.emptyList();

    public GameArenaPanel(MainClientApp mainApp) {
        this.mainApp = mainApp;
        this.dictionaryManager = DictionaryManager.getInstance();
        this.reflectionRuleLoader = new ReflectionRuleLoader();
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(16, 16));
        setBackground(MainClientApp.COLOR_BG);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. Top Bar: Timer & Live Notification Banner
        add(createTopTimerAndStatusPanel(), BorderLayout.NORTH);

        // 2. Center & Right Split: Center = 12-Letter Board + Input + Power-Ups; Right = Live Scoreboard + Locked Words
        JSplitPane arenaSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createCenterBoardAndInputPanel(),
                createRightLiveFeedPanel());
        arenaSplit.setResizeWeight(0.62);
        arenaSplit.setDividerLocation(660);
        arenaSplit.setDividerSize(8);
        arenaSplit.setBorder(null);
        arenaSplit.setOpaque(false);

        add(arenaSplit, BorderLayout.CENTER);
    }

    private JPanel createTopTimerAndStatusPanel() {
        JPanel topCard = new JPanel(new BorderLayout(0, 10));
        topCard.setBackground(MainClientApp.COLOR_CARD);
        topCard.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(14, 18, 14, 18)
        ));

        // Header line with Title + Hint display + Leave Match button
        JPanel headerRow = new JPanel(new BorderLayout(12, 0));
        headerRow.setOpaque(false);

        JLabel arenaTitle = new JLabel("[*] WORD HUNT ARENA — LIVE MATCH");
        arenaTitle.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 18));
        arenaTitle.setForeground(MainClientApp.COLOR_NAVY_HEADER);

        hintDisplayLabel = new JLabel("[Hint]: Not used yet");
        hintDisplayLabel.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        hintDisplayLabel.setForeground(new Color(180, 83, 9));
        hintDisplayLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        leaveMatchButton = MainClientApp.createPrimaryButton("⬅ Leave Match", MainClientApp.COLOR_DANGER);
        leaveMatchButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 12));
        leaveMatchButton.setBorder(new EmptyBorder(6, 12, 6, 12));
        leaveMatchButton.setToolTipText("Return to the Lobby immediately");
        leaveMatchButton.addActionListener(e -> mainApp.returnToLobbyAfterMatch());

        JPanel rightHeaderGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightHeaderGroup.setOpaque(false);
        rightHeaderGroup.add(hintDisplayLabel);
        rightHeaderGroup.add(leaveMatchButton);

        headerRow.add(arenaTitle, BorderLayout.WEST);
        headerRow.add(rightHeaderGroup, BorderLayout.EAST);

        // 90s Countdown ProgressBar
        timerProgressBar = new JProgressBar(0, 90);
        timerProgressBar.setValue(90);
        timerProgressBar.setStringPainted(true);
        timerProgressBar.setString("90s remaining");
        timerProgressBar.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 15));
        timerProgressBar.setForeground(MainClientApp.COLOR_PRIMARY);
        timerProgressBar.setBackground(new Color(226, 232, 240));
        timerProgressBar.setPreferredSize(new Dimension(100, 32));

        // Status Notification Banner
        JPanel bannerBox = new JPanel(new BorderLayout());
        bannerBox.setBackground(MainClientApp.COLOR_NAVY_HEADER);
        bannerBox.setBorder(new EmptyBorder(10, 14, 10, 14));

        statusBannerLabel = new JLabel("[*] Match started! Form words of at least 3 letters from the 12 tiles below!");
        statusBannerLabel.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        statusBannerLabel.setForeground(Color.WHITE);
        statusBannerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bannerBox.add(statusBannerLabel, BorderLayout.CENTER);

        topCard.add(headerRow, BorderLayout.NORTH);
        topCard.add(timerProgressBar, BorderLayout.CENTER);
        topCard.add(bannerBox, BorderLayout.SOUTH);
        return topCard;
    }

    private JPanel createCenterBoardAndInputPanel() {
        JPanel centerContainer = new JPanel(new BorderLayout(0, 14));
        centerContainer.setOpaque(false);

        // 12-Letter Board Card (2x6 Grid)
        JPanel boardCard = new JPanel(new BorderLayout(0, 12));
        boardCard.setBackground(MainClientApp.COLOR_CARD);
        boardCard.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(16, 18, 16, 18)
        ));

        JPanel boardHeader = new JPanel(new BorderLayout());
        boardHeader.setBackground(MainClientApp.COLOR_NAVY_HEADER);
        boardHeader.setBorder(new EmptyBorder(8, 14, 8, 14));

        JLabel boardTitle = new JLabel("[*] 12-LETTER ARENA BOARD (Click tiles or type directly — Bonus: 5 letters +5p, 6+ letters +10p)");
        boardTitle.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 13));
        boardTitle.setForeground(Color.WHITE);
        boardHeader.add(boardTitle, BorderLayout.WEST);

        JPanel gridPanel = new JPanel(new GridLayout(2, 6, 12, 12));
        gridPanel.setOpaque(false);
        gridPanel.setBorder(new EmptyBorder(10, 4, 10, 4));

        tileButtons.clear();
        for (int i = 0; i < 12; i++) {
            LetterTileButton tile = new LetterTileButton('?', 1);
            tile.addActionListener(e -> {
                if (!wordInputField.isEnabled()) {
                    return;
                }
                char ch = tile.getLetter();
                if (ch >= 'A' && ch <= 'Z') {
                    wordInputField.setText(wordInputField.getText() + ch);
                    wordInputField.requestFocusInWindow();
                }
            });
            tileButtons.add(tile);
            gridPanel.add(tile);
        }

        boardCard.add(boardHeader, BorderLayout.NORTH);
        boardCard.add(gridPanel, BorderLayout.CENTER);

        // Bottom Controls Card: Word Input + Submit + 3 Power-Up Buttons
        JPanel controlsCard = new JPanel(new BorderLayout(0, 14));
        controlsCard.setBackground(MainClientApp.COLOR_CARD);
        controlsCard.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(16, 18, 16, 18)
        ));

        // Word Input Row
        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);

        JLabel wordPrompt = new JLabel("Your Word:");
        wordPrompt.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 15));
        wordPrompt.setForeground(MainClientApp.COLOR_TEXT_DARK);

        wordInputField = new JTextField();
        wordInputField.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 20));
        wordInputField.setForeground(MainClientApp.COLOR_NAVY_HEADER);
        wordInputField.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_PRIMARY, 2, true),
                new EmptyBorder(8, 14, 8, 14)
        ));

        JPanel submitBtnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        submitBtnGroup.setOpaque(false);

        clearWordButton = MainClientApp.createPrimaryButton("Clear", new Color(100, 116, 139));
        clearWordButton.setPreferredSize(new Dimension(85, 44));
        clearWordButton.addActionListener(e -> {
            wordInputField.setText("");
            wordInputField.requestFocusInWindow();
        });

        submitWordButton = MainClientApp.createPrimaryButton("Submit (Enter)", MainClientApp.COLOR_ACCENT_GREEN);
        submitWordButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 15));
        submitWordButton.setPreferredSize(new Dimension(175, 44));

        ActionListener submitAction = e -> submitCurrentWord();
        wordInputField.addActionListener(submitAction);
        submitWordButton.addActionListener(submitAction);

        submitBtnGroup.add(clearWordButton);
        submitBtnGroup.add(submitWordButton);

        inputRow.add(wordPrompt, BorderLayout.WEST);
        inputRow.add(wordInputField, BorderLayout.CENTER);
        inputRow.add(submitBtnGroup, BorderLayout.EAST);

        // Power-Ups Row (3 Joker Buttons - each usable once per match)
        JPanel powerUpsPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        powerUpsPanel.setOpaque(false);

        hintButton = MainClientApp.createPrimaryButton("[Hint] (RMI)", new Color(217, 119, 6));
        hintButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        hintButton.setPreferredSize(new Dimension(140, 44));
        hintButton.setToolTipText("Requests a first/last letter hint for an unclaimed high-value word via Java RMI (1x per match)");
        hintButton.addActionListener(e -> triggerHintPowerUp());

        doubleScoreButton = MainClientApp.createPrimaryButton("[2x Score]", new Color(225, 29, 72));
        doubleScoreButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        doubleScoreButton.setPreferredSize(new Dimension(140, 44));
        doubleScoreButton.setToolTipText("Doubles the points of your next valid claimed word (1x per match)");
        doubleScoreButton.addActionListener(e -> triggerDoubleScorePowerUp());

        shuffleButton = MainClientApp.createPrimaryButton("[Shuffle]", MainClientApp.COLOR_PRIMARY);
        shuffleButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        shuffleButton.setPreferredSize(new Dimension(140, 44));
        shuffleButton.setToolTipText("Shuffles the 12 board tiles using ShufflePowerUp / Collections.shuffle (1x per match)");
        shuffleButton.addActionListener(e -> triggerShufflePowerUp());

        powerUpsPanel.add(hintButton);
        powerUpsPanel.add(doubleScoreButton);
        powerUpsPanel.add(shuffleButton);

        controlsCard.add(inputRow, BorderLayout.NORTH);
        controlsCard.add(powerUpsPanel, BorderLayout.SOUTH);

        centerContainer.add(boardCard, BorderLayout.CENTER);
        centerContainer.add(controlsCard, BorderLayout.SOUTH);
        return centerContainer;
    }

    private JPanel createRightLiveFeedPanel() {
        JPanel rightContainer = new JPanel(new GridLayout(2, 1, 0, 14));
        rightContainer.setOpaque(false);

        // Top: Live Match Scoreboard (JTable)
        JPanel scoreboardCard = new JPanel(new BorderLayout(0, 8));
        scoreboardCard.setBackground(MainClientApp.COLOR_CARD);
        scoreboardCard.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JPanel sbHeader = new JPanel(new BorderLayout());
        sbHeader.setBackground(MainClientApp.COLOR_NAVY_HEADER);
        sbHeader.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel sbTitle = new JLabel("[*] Live Match Scoreboard");
        sbTitle.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        sbTitle.setForeground(Color.WHITE);
        sbHeader.add(sbTitle, BorderLayout.WEST);

        String[] cols = {"Rank", "Player", "Score", "Words"};
        liveScoreboardModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        liveScoreboardTable = new JTable(liveScoreboardModel);
        liveScoreboardTable.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 13));
        liveScoreboardTable.setRowHeight(26);
        liveScoreboardTable.setGridColor(new Color(226, 232, 240));
        liveScoreboardTable.setFillsViewportHeight(true);

        JTableHeader th = liveScoreboardTable.getTableHeader();
        th.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 12));
        th.setBackground(new Color(241, 245, 249));
        th.setForeground(MainClientApp.COLOR_NAVY_HEADER);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        liveScoreboardTable.getColumnModel().getColumn(0).setMaxWidth(55);
        liveScoreboardTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        liveScoreboardTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        liveScoreboardTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        scoreboardCard.add(sbHeader, BorderLayout.NORTH);
        scoreboardCard.add(new JScrollPane(liveScoreboardTable), BorderLayout.CENTER);

        // Bottom: Locked Words Live Stream (DefaultListModel / JList)
        JPanel lockedCard = new JPanel(new BorderLayout(0, 8));
        lockedCard.setBackground(MainClientApp.COLOR_CARD);
        lockedCard.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JPanel lwHeader = new JPanel(new BorderLayout());
        lwHeader.setBackground(MainClientApp.COLOR_NAVY_HEADER);
        lwHeader.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel lwTitle = new JLabel("[*] Claimed / Locked Words Feed");
        lwTitle.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        lwTitle.setForeground(Color.WHITE);
        lwHeader.add(lwTitle, BorderLayout.WEST);

        lockedWordsListModel = new DefaultListModel<>();
        lockedWordsList = new JList<>(lockedWordsListModel);
        lockedWordsList.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 13));
        lockedWordsList.setBackground(new Color(248, 250, 252));
        lockedWordsList.setForeground(MainClientApp.COLOR_TEXT_DARK);
        lockedWordsList.setFixedCellHeight(26);

        lockedCard.add(lwHeader, BorderLayout.NORTH);
        lockedCard.add(new JScrollPane(lockedWordsList), BorderLayout.CENTER);

        rightContainer.add(scoreboardCard);
        rightContainer.add(lockedCard);
        return rightContainer;
    }

    // =========================================================================
    // Match Lifecycle & Event Callbacks
    // =========================================================================

    /**
     * Resets the arena state and populates the 12 letter tiles when {@code MATCH_STARTED} arrives.
     */
    public void startNewMatch(List<Character> boardLetters, int durationSeconds) {
        SwingUtilities.invokeLater(() -> {
            this.matchDurationSeconds = durationSeconds > 0 ? durationSeconds : 90;
            this.currentBoardLetters = new ArrayList<>(boardLetters != null ? boardLetters : Collections.emptyList());

            timerProgressBar.setMaximum(this.matchDurationSeconds);
            timerProgressBar.setValue(this.matchDurationSeconds);
            timerProgressBar.setForeground(MainClientApp.COLOR_PRIMARY);
            timerProgressBar.setString(this.matchDurationSeconds + "s remaining");

            hintDisplayLabel.setText("[Hint]: Available (1x)");
            updateStatusBanner("[*] Match started! Claim words before your opponents!", new Color(255, 255, 255));

            lockedWordsListModel.clear();
            wordInputField.setText("");
            wordInputField.setEnabled(true);
            submitWordButton.setEnabled(true);
            clearWordButton.setEnabled(true);

            // Reset 3 Power-Up buttons (each usable once per match)
            hintButton.setEnabled(true);
            hintButton.setText("[Hint] (RMI)");

            doubleScoreButton.setEnabled(true);
            doubleScoreButton.setText("[2x Score]");

            shuffleButton.setEnabled(true);
            shuffleButton.setText("[Shuffle]");

            renderBoardTiles(this.currentBoardLetters);
            wordInputField.requestFocusInWindow();
        });
    }

    /**
     * Immediately disables word submission inputs and power-up controls when time expires or the match ends.
     */
    public void disableMatchControls() {
        Runnable disableTask = () -> {
            wordInputField.setEnabled(false);
            submitWordButton.setEnabled(false);
            clearWordButton.setEnabled(false);
            hintButton.setEnabled(false);
            doubleScoreButton.setEnabled(false);
            shuffleButton.setEnabled(false);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            disableTask.run();
        } else {
            SwingUtilities.invokeLater(disableTask);
        }
    }

    private void renderBoardTiles(List<Character> letters) {
        for (int i = 0; i < tileButtons.size(); i++) {
            LetterTileButton tile = tileButtons.get(i);
            if (letters != null && i < letters.size() && letters.get(i) != null) {
                char ch = Character.toUpperCase(letters.get(i));
                int pts = dictionaryManager.getLetterScore(ch);
                tile.setLetterAndPoints(ch, pts);
            } else {
                tile.setLetterAndPoints('?', 0);
            }
        }
    }

    /**
     * Updates the countdown bar on every {@code TIMER_TICK}. Turns red during the final 15 seconds.
     * When remainingSeconds reaches 0, immediately disables word input and power-up buttons.
     */
    public void updateTimerTick(int remainingSeconds) {
        SwingUtilities.invokeLater(() -> {
            int clamped = Math.max(0, remainingSeconds);
            timerProgressBar.setValue(clamped);
            timerProgressBar.setString(clamped + "s remaining");

            if (clamped <= 15) {
                timerProgressBar.setForeground(MainClientApp.COLOR_DANGER);
            } else {
                timerProgressBar.setForeground(MainClientApp.COLOR_PRIMARY);
            }

            if (clamped <= 0) {
                disableMatchControls();
            }
        });
    }

    /**
     * Called when {@code WORD_LOCKED|username|word|pointsEarned} is broadcast by the server.
     */
    public void onWordLocked(String username, String word, int pointsEarned) {
        SwingUtilities.invokeLater(() -> {
            String entry = String.format("[*] %s  —  %s (+%d pts)", username, word, pointsEarned);
            lockedWordsListModel.add(0, entry);

            String bannerMsg = String.format("[*] %s claimed '%s' (+%d pts)!", username, word, pointsEarned);
            if (username != null && username.equalsIgnoreCase(mainApp.getCurrentUsername())) {
                updateStatusBanner(bannerMsg, new Color(134, 239, 172));
                if ("2x ACTIVE!".equals(doubleScoreButton.getText()) || "[2x ACTIVE!]".equals(doubleScoreButton.getText())) {
                    doubleScoreButton.setText("[2x Used]");
                }
            } else {
                updateStatusBanner(bannerMsg, new Color(253, 224, 71));
            }
        });
    }

    /**
     * Called when {@code WORD_REJECTED|word|reason} is sent to this player.
     */
    public void onWordRejected(String word, String reason) {
        SwingUtilities.invokeLater(() -> {
            String msg = "[!] " + (reason != null && !reason.isBlank() ? reason : ("Word '" + word + "' rejected!"));
            updateStatusBanner(msg, new Color(252, 165, 165));
        });
    }

    /**
     * Called when {@code SCOREBOARD_UPDATE} arrives from the server.
     */
    public void updateScoreboard(List<PlayerScore> leaderboard) {
        SwingUtilities.invokeLater(() -> {
            this.latestLeaderboard = leaderboard != null ? new ArrayList<>(leaderboard) : Collections.emptyList();
            liveScoreboardModel.setRowCount(0);
            int rank = 1;
            for (PlayerScore ps : this.latestLeaderboard) {
                liveScoreboardModel.addRow(new Object[]{
                        "#" + rank++,
                        ps.getUsername(),
                        ps.getScore() + " pts",
                        ps.getWordsFoundCount()
                });
            }
        });
    }

    /**
     * Called when {@code 2X_ACTIVATED|username|success} arrives.
     */
    public void onDoubleScoreActivated(String username, boolean success) {
        SwingUtilities.invokeLater(() -> {
            if (username != null && username.equalsIgnoreCase(mainApp.getCurrentUsername())) {
                if (success) {
                    doubleScoreButton.setEnabled(false);
                    doubleScoreButton.setText("2x ACTIVE!");
                    updateStatusBanner("[*] 2x Score Power-Up Activated! Your next valid word earns DOUBLE points!", new Color(253, 186, 116));
                } else {
                    doubleScoreButton.setEnabled(false);
                    doubleScoreButton.setText("[2x Used]");
                    updateStatusBanner("[!] 2x Score Power-Up was already used in this match.", new Color(252, 165, 165));
                }
            }
        });
    }

    // =========================================================================
    // Player Actions (Word Submit & 3 Power-Ups)
    // =========================================================================

    private void submitCurrentWord() {
        if (!wordInputField.isEnabled()) {
            return;
        }
        String rawWord = wordInputField.getText().trim().toUpperCase(Locale.ENGLISH);
        if (rawWord.isEmpty()) {
            return;
        }
        wordInputField.setText("");
        wordInputField.requestFocusInWindow();

        if (mainApp.getGameClient() != null && mainApp.getGameClient().isConnected()) {
            mainApp.getGameClient().submitWord(mainApp.getCurrentUsername(), rawWord);
        }
    }

    /**
     * Power-Up 1: {@code [Hint] (RMI)} — invokes {@code HintRemoteService.requestHint(username)} on a {@link SwingWorker}.
     * Re-enables the button if a network error occurs or if the hint could not be consumed yet.
     */
    private void triggerHintPowerUp() {
        hintButton.setEnabled(false);
        hintButton.setText("[Hint] Loading...");

        final String username = mainApp.getCurrentUsername();
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            private String errorMsg = null;

            @Override
            protected String doInBackground() {
                try {
                    return mainApp.getRmiClientHelper().requestHint(username);
                } catch (Exception e) {
                    errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    String hint = get();
                    if (hint != null && !hint.isBlank()) {
                        hintButton.setEnabled(false);
                        hintButton.setText("[Hint] Used");
                        hintDisplayLabel.setText("[Hint]: " + hint);
                        updateStatusBanner("[*] RMI Hint received:  " + hint, new Color(253, 224, 71));
                    } else if (errorMsg != null) {
                        if (wordInputField.isEnabled()) {
                            hintButton.setEnabled(true);
                        }
                        hintButton.setText("[Hint] (RMI)");
                        updateStatusBanner("[!] Hint RMI Error: " + errorMsg, new Color(252, 165, 165));
                    } else {
                        if (wordInputField.isEnabled() && "[Hint]: Available (1x)".equals(hintDisplayLabel.getText())) {
                            hintButton.setEnabled(true);
                            hintButton.setText("[Hint] (RMI)");
                        } else {
                            hintButton.setEnabled(false);
                            hintButton.setText("[Hint] Used");
                        }
                        hintDisplayLabel.setText("[Hint]: None available");
                        updateStatusBanner("[!] No unclaimed hint words available or hint already used.", new Color(253, 224, 71));
                    }
                } catch (Exception ex) {
                    if (wordInputField.isEnabled()) {
                        hintButton.setEnabled(true);
                    }
                    hintButton.setText("[Hint] (RMI)");
                }
                wordInputField.requestFocusInWindow();
            }
        };
        worker.execute();
    }

    /**
     * Power-Up 2: {@code [2x Score]} — sends {@code ACTIVATE_2X|username} to the server and shows
     * {@code "2x Requesting..."} until {@code onDoubleScoreActivated} confirms {@code "2x ACTIVE!"}.
     */
    private void triggerDoubleScorePowerUp() {
        doubleScoreButton.setEnabled(false);
        doubleScoreButton.setText("2x Requesting...");
        if (mainApp.getGameClient() != null && mainApp.getGameClient().isConnected()) {
            mainApp.getGameClient().activateDoubleScore(mainApp.getCurrentUsername());
        }
        wordInputField.requestFocusInWindow();
    }

    /**
     * Power-Up 3: {@code [Shuffle]} — uses {@link ShufflePowerUp} loaded via {@link ReflectionRuleLoader}
     * (which calls {@link Collections#shuffle(List)}) to rearrange the 12 board tiles and disables the button.
     */
    private void triggerShufflePowerUp() {
        shuffleButton.setEnabled(false);
        shuffleButton.setText("[Shuffled]");

        PlayerScore dummyScore = new PlayerScore(mainApp.getCurrentUsername());
        reflectionRuleLoader.activatePowerUp("SHUFFLE", dummyScore);
        ShufflePowerUp shufflePowerUp = (ShufflePowerUp) reflectionRuleLoader.getPowerUp("SHUFFLE");

        if (shufflePowerUp != null) {
            this.currentBoardLetters = new ArrayList<>(shufflePowerUp.shuffleLetters(this.currentBoardLetters));
        } else {
            Collections.shuffle(this.currentBoardLetters);
        }

        renderBoardTiles(this.currentBoardLetters);
        updateStatusBanner("[*] Board letters shuffled!", new Color(191, 219, 254));
        wordInputField.requestFocusInWindow();
    }

    private void updateStatusBanner(String message, Color textColor) {
        statusBannerLabel.setText(message);
        statusBannerLabel.setForeground(textColor);
    }

    public List<PlayerScore> getLatestLeaderboard() {
        return latestLeaderboard;
    }

    // =========================================================================
    // Custom 12-Letter Board Tile Component
    // =========================================================================

    /**
     * Custom Swing button representing one of the 12 board letter tiles.
     * Renders the uppercase letter prominently in the center and the letter difficulty points
     * (e.g., {@code "5p"}) in the bottom-right corner.
     */
    private static class LetterTileButton extends JButton {
        private char letter;
        private int points;

        public LetterTileButton(char letter, int points) {
            this.letter = letter;
            this.points = points;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(88, 88));
        }

        public void setLetterAndPoints(char letter, int points) {
            this.letter = letter;
            this.points = points;
            repaint();
        }

        public char getLetter() {
            return letter;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Tile card background with subtle hover/press state
            if (getModel().isPressed()) {
                g2.setColor(new Color(219, 234, 254));
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(239, 246, 255));
            } else {
                g2.setColor(new Color(248, 250, 252));
            }
            g2.fillRoundRect(2, 2, w - 4, h - 4, 16, 16);

            // Tile border
            g2.setColor(getModel().isRollover() ? MainClientApp.COLOR_PRIMARY : new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(2, 2, w - 4, h - 4, 16, 16);

            // Center Big Letter
            String letterStr = String.valueOf(letter);
            g2.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 34));
            g2.setColor(MainClientApp.COLOR_NAVY_HEADER);
            FontMetrics fmLetter = g2.getFontMetrics();
            int letterX = (w - fmLetter.stringWidth(letterStr)) / 2;
            int letterY = (h - fmLetter.getHeight()) / 2 + fmLetter.getAscent() - 4;
            g2.drawString(letterStr, letterX, letterY);

            // Bottom-Right Difficulty Score (e.g. "5p")
            String ptsStr = points + "p";
            g2.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 12));
            if (points >= 5) {
                g2.setColor(new Color(225, 29, 72)); // High value letter highlight
            } else if (points >= 3) {
                g2.setColor(MainClientApp.COLOR_PRIMARY);
            } else {
                g2.setColor(MainClientApp.COLOR_TEXT_MUTED);
            }
            FontMetrics fmPts = g2.getFontMetrics();
            int ptsX = w - fmPts.stringWidth(ptsStr) - 10;
            int ptsY = h - 9;
            g2.drawString(ptsStr, ptsX, ptsY);

            g2.dispose();
        }
    }
}

package edu.ieu.se360.wordhunt.gui;

import edu.ieu.se360.wordhunt.engine.ScoreCalculator;
import edu.ieu.se360.wordhunt.model.PlayerScore;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * End-of-Match Summary Modal Dialog ({@code ResultDialog}).
 * Displays:
 * <ul>
 *     <li>Winner Announcement Banner & Winning Score</li>
 *     <li>Final Match Standings ({@link JTable})</li>
 *     <li>Highest-Value Missed Words ({@link JList}) with calculated point values</li>
 *     <li>"Back to Lobby" button that closes the dialog and switches the main window back to {@link LobbyPanel}</li>
 * </ul>
 */
public class ResultDialog extends JDialog {

    private final MainClientApp mainApp;
    private final ScoreCalculator scoreCalculator = new ScoreCalculator();
    private final AtomicBoolean returnedToLobby = new AtomicBoolean(false);

    public ResultDialog(MainClientApp parentFrame,
                        String winnerUsername,
                        int winnerScore,
                        List<PlayerScore> finalStandings,
                        List<String> missedWords) {
        super(parentFrame, "Match Finished — Word Hunt Arena", true);
        this.mainApp = parentFrame;
        initUI(winnerUsername, winnerScore, finalStandings, missedWords);
    }

    private void navigateBackToLobbyOnce() {
        if (returnedToLobby.compareAndSet(false, true) && mainApp != null) {
            mainApp.returnToLobbyAfterMatch();
        }
    }

    private void initUI(String winnerUsername,
                        int winnerScore,
                        List<PlayerScore> finalStandings,
                        List<String> missedWords) {
        setSize(680, 540);
        setLocationRelativeTo(mainApp);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                navigateBackToLobbyOnce();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                navigateBackToLobbyOnce();
            }
        });

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(MainClientApp.COLOR_BG);
        root.setBorder(new EmptyBorder(18, 22, 18, 22));

        // 1. Top Winner Banner
        JPanel winnerBanner = new JPanel();
        winnerBanner.setLayout(new BoxLayout(winnerBanner, BoxLayout.Y_AXIS));
        winnerBanner.setBackground(MainClientApp.COLOR_NAVY_HEADER);
        winnerBanner.setBorder(new EmptyBorder(18, 24, 18, 24));

        String safeWinner = (winnerUsername == null || winnerUsername.isBlank() || "None".equalsIgnoreCase(winnerUsername))
                ? "No Winner"
                : winnerUsername;

        JLabel trophyTitle = new JLabel("[*] MATCH OVER — WINNER: " + safeWinner.toUpperCase());
        trophyTitle.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 22));
        trophyTitle.setForeground(new Color(253, 224, 71));
        trophyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel scoreSubtitle = new JLabel("Winning Score: " + winnerScore + " pts");
        scoreSubtitle.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 15));
        scoreSubtitle.setForeground(Color.WHITE);
        scoreSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        winnerBanner.add(trophyTitle);
        winnerBanner.add(Box.createVerticalStrut(6));
        winnerBanner.add(scoreSubtitle);

        root.add(winnerBanner, BorderLayout.NORTH);

        // 2. Center Split: Final Standings Table + Missed High-Value Words
        JPanel centerGrid = new JPanel(new GridLayout(1, 2, 14, 0));
        centerGrid.setOpaque(false);

        // Left Card: Final Match Standings
        JPanel standingsCard = new JPanel(new BorderLayout(0, 8));
        standingsCard.setBackground(MainClientApp.COLOR_CARD);
        standingsCard.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel standingsTitle = new JLabel("[*] Final Match Standings");
        standingsTitle.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        standingsTitle.setForeground(MainClientApp.COLOR_NAVY_HEADER);
        standingsCard.add(standingsTitle, BorderLayout.NORTH);

        String[] cols = {"Rank", "Player", "Score", "Words"};
        DefaultTableModel standingsModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        if (finalStandings != null && !finalStandings.isEmpty()) {
            int rank = 1;
            for (PlayerScore ps : finalStandings) {
                standingsModel.addRow(new Object[]{
                        "#" + rank++,
                        ps.getUsername(),
                        ps.getScore() + " pts",
                        ps.getWordsFoundCount()
                });
            }
        } else if (!"No Winner".equals(safeWinner) && !safeWinner.toLowerCase().startsWith("none")) {
            standingsModel.addRow(new Object[]{"#1", safeWinner, winnerScore + " pts", "-"});
        }

        JTable standingsTable = new JTable(standingsModel);
        standingsTable.setFont(new Font(MainClientApp.FONT_FAMILY, Font.PLAIN, 13));
        standingsTable.setRowHeight(26);
        standingsTable.setGridColor(new Color(226, 232, 240));
        standingsTable.setFillsViewportHeight(true);

        JTableHeader th = standingsTable.getTableHeader();
        th.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 12));
        th.setBackground(new Color(241, 245, 249));
        th.setForeground(MainClientApp.COLOR_NAVY_HEADER);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        standingsTable.getColumnModel().getColumn(0).setMaxWidth(55);
        standingsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        standingsCard.add(new JScrollPane(standingsTable), BorderLayout.CENTER);

        // Right Card: Highest-Value Missed Words
        JPanel missedCard = new JPanel(new BorderLayout(0, 8));
        missedCard.setBackground(MainClientApp.COLOR_CARD);
        missedCard.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel missedTitle = new JLabel("[*] Highest-Value Missed Words");
        missedTitle.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        missedTitle.setForeground(MainClientApp.COLOR_NAVY_HEADER);
        missedCard.add(missedTitle, BorderLayout.NORTH);

        DefaultListModel<String> missedListModel = new DefaultListModel<>();
        if (missedWords != null && !missedWords.isEmpty()) {
            int idx = 1;
            for (String word : missedWords) {
                if (word != null && !word.isBlank()) {
                    int score = scoreCalculator.calculateWordScore(word.trim(), false);
                    missedListModel.addElement(String.format("%d. %s   (+%d pts)", idx++, word.trim(), score));
                }
            }
        }
        if (missedListModel.isEmpty()) {
            missedListModel.addElement("[*] Incredible! All top words were found!");
        }

        JList<String> missedList = new JList<>(missedListModel);
        missedList.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 14));
        missedList.setBackground(new Color(248, 250, 252));
        missedList.setForeground(new Color(180, 83, 9));
        missedList.setFixedCellHeight(30);
        missedCard.add(new JScrollPane(missedList), BorderLayout.CENTER);

        centerGrid.add(standingsCard);
        centerGrid.add(missedCard);
        root.add(centerGrid, BorderLayout.CENTER);

        // 3. Bottom Action Bar ("Back to Lobby")
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        bottomPanel.setOpaque(false);

        JButton backToLobbyButton = MainClientApp.createPrimaryButton("⬅ Back to Lobby", MainClientApp.COLOR_PRIMARY);
        backToLobbyButton.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 15));
        backToLobbyButton.setPreferredSize(new Dimension(240, 44));
        backToLobbyButton.addActionListener(e -> {
            dispose();
            navigateBackToLobbyOnce();
        });

        bottomPanel.add(backToLobbyButton);
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }
}

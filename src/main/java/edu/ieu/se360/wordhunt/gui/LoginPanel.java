package edu.ieu.se360.wordhunt.gui;

import edu.ieu.se360.wordhunt.config.ConfigManager;
import edu.ieu.se360.wordhunt.model.User;
import edu.ieu.se360.wordhunt.network.GameServer;
import edu.ieu.se360.wordhunt.network.Protocol;
import edu.ieu.se360.wordhunt.rmi.RmiServerManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Login & Registration screen for Word Hunt Arena.
 * Connects via {@code RmiClientHelper} ({@code AuthRemoteService}) to authenticate or register,
 * then connects the TCP {@code GameClient} and navigates to the {@code LobbyPanel}.
 */
public class LoginPanel extends JPanel {

    private final MainClientApp mainApp;

    private JTextField hostField;
    private JTextField tcpPortField;
    private JTextField rmiPortField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private JLabel statusLabel;

    public LoginPanel(MainClientApp mainApp) {
        this.mainApp = mainApp;
        initUI();
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        setBackground(MainClientApp.COLOR_BG);

        JPanel cardPanel = new JPanel();
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        cardPanel.setBackground(MainClientApp.COLOR_CARD);
        cardPanel.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(32, 42, 32, 42)
        ));
        cardPanel.setPreferredSize(new Dimension(480, 560));

        // Header Banner inside Card
        JPanel headerBox = new JPanel();
        headerBox.setLayout(new BoxLayout(headerBox, BoxLayout.Y_AXIS));
        headerBox.setBackground(MainClientApp.COLOR_NAVY_HEADER);
        headerBox.setBorder(new EmptyBorder(20, 24, 20, 24));
        headerBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JLabel titleLabel = new JLabel("[*] WORD HUNT ARENA");
        titleLabel.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Multiplayer Real-Time Word Game");
        subtitleLabel.setFont(new Font(MainClientApp.FONT_FAMILY, Font.PLAIN, 13));
        subtitleLabel.setForeground(new Color(195, 210, 235));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerBox.add(titleLabel);
        headerBox.add(Box.createVerticalStrut(4));
        headerBox.add(subtitleLabel);

        cardPanel.add(headerBox);
        cardPanel.add(Box.createVerticalStrut(22));

        // Form Fields Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(MainClientApp.COLOR_CARD);
        formPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        ConfigManager cfg = ConfigManager.getInstance();

        // Row 0: Server IP / Host
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        formPanel.add(createFieldLabel("Server IP / Host:"), gbc);

        gbc.gridy = 1;
        hostField = createStyledTextField(cfg.getServerHost());
        formPanel.add(hostField, gbc);

        // Row 2: TCP Port and RMI Port side by side
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        formPanel.add(createFieldLabel("TCP Socket Port:"), gbc);

        gbc.gridx = 1;
        formPanel.add(createFieldLabel("RMI Registry Port:"), gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        tcpPortField = createStyledTextField(String.valueOf(cfg.getTcpPort()));
        formPanel.add(tcpPortField, gbc);

        gbc.gridx = 1;
        rmiPortField = createStyledTextField(String.valueOf(cfg.getRmiPort()));
        formPanel.add(rmiPortField, gbc);

        // Row 4: Username
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        formPanel.add(createFieldLabel("Username:"), gbc);

        gbc.gridy = 5;
        usernameField = createStyledTextField("");
        usernameField.setToolTipText("Enter your player username");
        formPanel.add(usernameField, gbc);

        // Row 6: Password
        gbc.gridy = 6;
        formPanel.add(createFieldLabel("Password:"), gbc);

        gbc.gridy = 7;
        passwordField = new JPasswordField();
        passwordField.setFont(new Font(MainClientApp.FONT_FAMILY, Font.PLAIN, 14));
        passwordField.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));
        formPanel.add(passwordField, gbc);

        cardPanel.add(formPanel);
        cardPanel.add(Box.createVerticalStrut(16));

        // Status / Feedback Label
        statusLabel = new JLabel("Enter your credentials to join the arena.");
        statusLabel.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 12));
        statusLabel.setForeground(MainClientApp.COLOR_TEXT_MUTED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        cardPanel.add(statusLabel);
        cardPanel.add(Box.createVerticalStrut(16));

        // Buttons Panel (Login & Register)
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 14, 0));
        buttonPanel.setBackground(MainClientApp.COLOR_CARD);
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        loginButton = MainClientApp.createPrimaryButton("Login", MainClientApp.COLOR_PRIMARY);
        registerButton = MainClientApp.createPrimaryButton("Register", MainClientApp.COLOR_ACCENT_GREEN);

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        cardPanel.add(buttonPanel);

        // Bind listeners
        ActionListener loginAction = e -> performAuth(false);
        loginButton.addActionListener(loginAction);
        passwordField.addActionListener(loginAction);
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());

        registerButton.addActionListener(e -> performAuth(true));

        add(cardPanel);
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(MainClientApp.FONT_FAMILY, Font.BOLD, 12));
        label.setForeground(MainClientApp.COLOR_TEXT_DARK);
        return label;
    }

    private JTextField createStyledTextField(String defaultText) {
        JTextField field = new JTextField(defaultText);
        field.setFont(new Font(MainClientApp.FONT_FAMILY, Font.PLAIN, 14));
        field.setBorder(new CompoundBorder(
                new LineBorder(MainClientApp.COLOR_BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));
        return field;
    }

    /**
     * Executes authentication or registration asynchronously via {@link SwingWorker}
     * so the Swing Event Dispatch Thread never freezes.
     */
    private void performAuth(boolean isRegister) {
        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            host = "localhost";
            hostField.setText(host);
        }
        int tcpPort = Protocol.parseIntSafe(tcpPortField.getText(), GameServer.DEFAULT_TCP_PORT);
        int rmiPort = Protocol.parseIntSafe(rmiPortField.getText(), RmiServerManager.DEFAULT_RMI_PORT);
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Please enter both username and password.", MainClientApp.COLOR_DANGER);
            return;
        }

        setControlsEnabled(false);
        showStatus(isRegister ? "Registering account via RMI..." : "Authenticating via RMI...", MainClientApp.COLOR_PRIMARY);

        final String targetHost = host;
        final int targetTcpPort = tcpPort;
        final int targetRmiPort = rmiPort;

        SwingWorker<User, Void> worker = new SwingWorker<>() {
            private String errorMessage = null;

            @Override
            protected User doInBackground() {
                try {
                    mainApp.configureConnectionEndpoints(targetHost, targetTcpPort, targetRmiPort);
                    User user;
                    if (isRegister) {
                        user = mainApp.getRmiClientHelper().register(username, password);
                        if (user == null) {
                            errorMessage = "Registration failed: Username '" + username + "' is already taken or invalid.";
                            return null;
                        }
                    } else {
                        user = mainApp.getRmiClientHelper().login(username, password);
                        if (user == null) {
                            errorMessage = "Login failed: Invalid username or password.";
                            return null;
                        }
                    }

                    // Connect TCP Socket GameClient and join lobby
                    mainApp.connectSocketForUser(user);
                    return user;
                } catch (Exception ex) {
                    errorMessage = "Connection Error: " + ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
                try {
                    User authenticatedUser = get();
                    if (authenticatedUser != null) {
                        passwordField.setText("");
                        showStatus("Connected as " + authenticatedUser.getUsername() + "!", MainClientApp.COLOR_ACCENT_GREEN);
                        mainApp.onLoginSuccess(authenticatedUser);
                    } else {
                        showStatus(errorMessage != null ? errorMessage : "Authentication failed.", MainClientApp.COLOR_DANGER);
                    }
                } catch (Exception ex) {
                    showStatus("Error: " + ex.getMessage(), MainClientApp.COLOR_DANGER);
                }
            }
        };
        worker.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        tcpPortField.setEnabled(enabled);
        rmiPortField.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    public void showStatus(String message, Color color) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        } else {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(message);
                statusLabel.setForeground(color);
            });
        }
    }

    public void resetFieldsForLogout() {
        passwordField.setText("");
        showStatus("Enter your credentials to join the arena.", MainClientApp.COLOR_TEXT_MUTED);
        setControlsEnabled(true);
    }
}

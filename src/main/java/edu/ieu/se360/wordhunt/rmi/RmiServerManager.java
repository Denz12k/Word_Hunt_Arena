package edu.ieu.se360.wordhunt.rmi;

import edu.ieu.se360.wordhunt.config.ConfigManager;
import edu.ieu.se360.wordhunt.database.MatchDAO;
import edu.ieu.se360.wordhunt.database.UserDAO;
import edu.ieu.se360.wordhunt.engine.GameSession;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.function.Supplier;

/**
 * Manages the lifecycle of the Java RMI Registry (default port 1099) and binds
 * {@link AuthRemoteService}, {@link LeaderboardRemoteService}, and {@link HintRemoteService}.
 */
public class RmiServerManager {
    public static final int DEFAULT_RMI_PORT = 1099;

    public static final String AUTH_SERVICE_NAME = "AuthRemoteService";
    public static final String LEADERBOARD_SERVICE_NAME = "LeaderboardRemoteService";
    public static final String HINT_SERVICE_NAME = "HintRemoteService";

    // Alternative alias binding names for convenience
    public static final String AUTH_SERVICE_ALIAS = "AuthService";
    public static final String LEADERBOARD_SERVICE_ALIAS = "LeaderboardService";
    public static final String HINT_SERVICE_ALIAS = "HintService";

    private final int port;
    private final UserDAO userDAO;
    private final MatchDAO matchDAO;

    private Registry registry;
    private AuthRemoteServiceImpl authService;
    private LeaderboardRemoteServiceImpl leaderboardService;
    private HintRemoteServiceImpl hintService;
    private volatile boolean running;

    public RmiServerManager() {
        this(ConfigManager.getInstance().getRmiPort(), new UserDAO(), new MatchDAO());
    }

    public RmiServerManager(int port) {
        this(port, new UserDAO(), new MatchDAO());
    }

    public RmiServerManager(int port, UserDAO userDAO, MatchDAO matchDAO) {
        this.port = port > 0 ? port : DEFAULT_RMI_PORT;
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
        this.matchDAO = matchDAO != null ? matchDAO : new MatchDAO();
    }

    /**
     * Starts or attaches to the RMI Registry on the configured port and binds all remote services.
     */
    public synchronized void start() throws RemoteException {
        if (running) {
            return;
        }
        try {
            registry = LocateRegistry.createRegistry(port);
            System.out.println("[RMI Server] Created new RMI Registry on port " + port);
        } catch (ExportException e) {
            registry = LocateRegistry.getRegistry(port);
            System.out.println("[RMI Server] Attached to existing RMI Registry on port " + port);
        }

        authService = new AuthRemoteServiceImpl(userDAO);
        leaderboardService = new LeaderboardRemoteServiceImpl(userDAO, matchDAO);
        if (hintService == null) {
            hintService = new HintRemoteServiceImpl();
        }

        registry.rebind(AUTH_SERVICE_NAME, authService);
        registry.rebind(AUTH_SERVICE_ALIAS, authService);

        registry.rebind(LEADERBOARD_SERVICE_NAME, leaderboardService);
        registry.rebind(LEADERBOARD_SERVICE_ALIAS, leaderboardService);

        registry.rebind(HINT_SERVICE_NAME, hintService);
        registry.rebind(HINT_SERVICE_ALIAS, hintService);

        running = true;
        System.out.println("[RMI Server] Bound services: "
                + AUTH_SERVICE_NAME + ", "
                + LEADERBOARD_SERVICE_NAME + ", "
                + HINT_SERVICE_NAME + " on port " + port);
    }

    /**
     * Updates the active {@link GameSession} reference used by {@link HintRemoteServiceImpl}.
     */
    public void setActiveGameSession(GameSession session) {
        if (hintService != null) {
            hintService.setActiveSession(session);
        }
    }

    /**
     * Sets a dynamic supplier for the active {@link GameSession} on {@link HintRemoteServiceImpl}.
     */
    public void setSessionSupplier(Supplier<GameSession> supplier) {
        if (hintService != null) {
            hintService.setSessionSupplier(supplier);
        }
    }

    /**
     * Unbinds and unexports the RMI services cleanly.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (registry != null) {
            unbindQuietly(AUTH_SERVICE_NAME);
            unbindQuietly(AUTH_SERVICE_ALIAS);
            unbindQuietly(LEADERBOARD_SERVICE_NAME);
            unbindQuietly(LEADERBOARD_SERVICE_ALIAS);
            unbindQuietly(HINT_SERVICE_NAME);
            unbindQuietly(HINT_SERVICE_ALIAS);
        }
        unexportQuietly(authService);
        unexportQuietly(leaderboardService);
        unexportQuietly(hintService);
        System.out.println("[RMI Server] Stopped RMI services on port " + port);
    }

    private void unbindQuietly(String name) {
        try {
            registry.unbind(name);
        } catch (Exception ignored) {
        }
    }

    private void unexportQuietly(UnicastRemoteObject obj) {
        if (obj != null) {
            try {
                UnicastRemoteObject.unexportObject(obj, true);
            } catch (NoSuchObjectException ignored) {
            }
        }
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    public Registry getRegistry() {
        return registry;
    }

    public AuthRemoteServiceImpl getAuthService() {
        return authService;
    }

    public LeaderboardRemoteServiceImpl getLeaderboardService() {
        return leaderboardService;
    }

    public HintRemoteServiceImpl getHintService() {
        return hintService;
    }
}

package edu.ieu.se360.wordhunt.rmi;

import edu.ieu.se360.wordhunt.config.ConfigManager;
import edu.ieu.se360.wordhunt.model.MatchRecord;
import edu.ieu.se360.wordhunt.model.User;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.List;

/**
 * Client-side helper for looking up and invoking the Java RMI services
 * ({@link AuthRemoteService}, {@link LeaderboardRemoteService}, {@link HintRemoteService}).
 */
public class RmiClientHelper {
    private String host;
    private int port;

    private AuthRemoteService authService;
    private LeaderboardRemoteService leaderboardService;
    private HintRemoteService hintService;

    public RmiClientHelper() {
        this(ConfigManager.getInstance().getServerHost(), ConfigManager.getInstance().getRmiPort());
    }

    public RmiClientHelper(String host, int port) {
        this.host = (host == null || host.isBlank()) ? "localhost" : host.trim();
        this.port = port > 0 ? port : RmiServerManager.DEFAULT_RMI_PORT;
    }

    /**
     * Connects to the remote RMI Registry and looks up all three remote services.
     */
    public synchronized void connect() throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        this.authService = (AuthRemoteService) registry.lookup(RmiServerManager.AUTH_SERVICE_NAME);
        this.leaderboardService = (LeaderboardRemoteService) registry.lookup(RmiServerManager.LEADERBOARD_SERVICE_NAME);
        this.hintService = (HintRemoteService) registry.lookup(RmiServerManager.HINT_SERVICE_NAME);
    }

    public synchronized AuthRemoteService getAuthService() throws RemoteException {
        if (authService == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(host, port);
                authService = (AuthRemoteService) registry.lookup(RmiServerManager.AUTH_SERVICE_NAME);
            } catch (NotBoundException e) {
                throw new RemoteException("RMI service not bound: " + RmiServerManager.AUTH_SERVICE_NAME, e);
            }
        }
        return authService;
    }

    public synchronized LeaderboardRemoteService getLeaderboardService() throws RemoteException {
        if (leaderboardService == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(host, port);
                leaderboardService = (LeaderboardRemoteService) registry.lookup(RmiServerManager.LEADERBOARD_SERVICE_NAME);
            } catch (NotBoundException e) {
                throw new RemoteException("RMI service not bound: " + RmiServerManager.LEADERBOARD_SERVICE_NAME, e);
            }
        }
        return leaderboardService;
    }

    public synchronized HintRemoteService getHintService() throws RemoteException {
        if (hintService == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(host, port);
                hintService = (HintRemoteService) registry.lookup(RmiServerManager.HINT_SERVICE_NAME);
            } catch (NotBoundException e) {
                throw new RemoteException("RMI service not bound: " + RmiServerManager.HINT_SERVICE_NAME, e);
            }
        }
        return hintService;
    }

    // Convenience wrapper methods for Swing GUI and client usage

    public User login(String username, String password) throws RemoteException {
        return getAuthService().login(username, password);
    }

    public User register(String username, String password) throws RemoteException {
        return getAuthService().register(username, password);
    }

    public List<User> getTopPlayers(int limit) throws RemoteException {
        List<User> list = getLeaderboardService().getTopPlayers(limit);
        return list != null ? list : Collections.emptyList();
    }

    public List<MatchRecord> getMatchHistory(String username) throws RemoteException {
        List<MatchRecord> list = getLeaderboardService().getMatchHistory(username);
        return list != null ? list : Collections.emptyList();
    }

    public String requestHint(String username) throws RemoteException {
        return getHintService().requestHint(username);
    }

    // Static lookup helpers for direct one-line resolution

    public static AuthRemoteService lookupAuthService(String host, int port) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (AuthRemoteService) registry.lookup(RmiServerManager.AUTH_SERVICE_NAME);
    }

    public static LeaderboardRemoteService lookupLeaderboardService(String host, int port) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (LeaderboardRemoteService) registry.lookup(RmiServerManager.LEADERBOARD_SERVICE_NAME);
    }

    public static HintRemoteService lookupHintService(String host, int port) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        return (HintRemoteService) registry.lookup(RmiServerManager.HINT_SERVICE_NAME);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = (host == null || host.isBlank()) ? "localhost" : host.trim();
        resetCachedStubs();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port > 0 ? port : RmiServerManager.DEFAULT_RMI_PORT;
        resetCachedStubs();
    }

    public synchronized void resetCachedStubs() {
        this.authService = null;
        this.leaderboardService = null;
        this.hintService = null;
    }
}

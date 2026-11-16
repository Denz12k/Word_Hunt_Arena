package edu.ieu.se360.wordhunt.rmi;

import edu.ieu.se360.wordhunt.engine.GameSession;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.function.Supplier;

/**
 * UnicastRemoteObject implementation of {@link HintRemoteService}.
 * Delegates hint generation to the currently active {@link GameSession}, returning an
 * unclaimed word's first and last letters (e.g., {@code "S _ _ _ _ E"}) and marking the
 * player's hint power-up as used.
 */
public class HintRemoteServiceImpl extends UnicastRemoteObject implements HintRemoteService {
    private static final long serialVersionUID = 1L;

    private volatile GameSession activeSession;
    private volatile Supplier<GameSession> sessionSupplier;

    public HintRemoteServiceImpl() throws RemoteException {
        super();
    }

    public HintRemoteServiceImpl(GameSession activeSession) throws RemoteException {
        super();
        this.activeSession = activeSession;
    }

    public HintRemoteServiceImpl(Supplier<GameSession> sessionSupplier) throws RemoteException {
        super();
        this.sessionSupplier = sessionSupplier;
    }

    /**
     * Updates the reference to the currently active {@link GameSession}.
     */
    public void setActiveSession(GameSession activeSession) {
        this.activeSession = activeSession;
    }

    /**
     * Sets a dynamic supplier for resolving the currently active {@link GameSession}.
     */
    public void setSessionSupplier(Supplier<GameSession> sessionSupplier) {
        this.sessionSupplier = sessionSupplier;
    }

    public GameSession getActiveSession() {
        if (sessionSupplier != null) {
            GameSession supplied = sessionSupplier.get();
            if (supplied != null) {
                return supplied;
            }
        }
        return activeSession;
    }

    @Override
    public synchronized String requestHint(String username) throws RemoteException {
        if (username == null || username.isBlank()) {
            return null;
        }
        GameSession session = getActiveSession();
        if (session == null || !session.isActive()) {
            System.out.println("[RMI Hint] Hint requested by '" + username + "', but no active match is running.");
            return null;
        }
        String hint = session.useHintPowerUp(username.trim());
        if (hint != null) {
            System.out.println("[RMI Hint] Generated hint '" + hint + "' for user: " + username.trim());
        } else {
            System.out.println("[RMI Hint] Hint request denied for '" + username.trim() + "' (already used or no unclaimed words).");
        }
        return hint;
    }
}

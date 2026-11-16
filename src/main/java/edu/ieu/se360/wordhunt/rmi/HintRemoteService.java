package edu.ieu.se360.wordhunt.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface for requesting a word hint during an active match over Java RMI.
 */
public interface HintRemoteService extends Remote {

    /**
     * Requests a hint for an unclaimed valid word in the active {@code GameSession}
     * (formatted as first and last letter, e.g., {@code "S _ _ _ _ E"}) and marks the
     * player's hint power-up as used for the current match.
     *
     * @param username the username of the player requesting a hint
     * @return the formatted hint string (e.g. {@code "S _ _ _ _ E"}), or {@code null} if
     *         no active match exists, the player already used their hint, or no unclaimed words remain
     * @throws RemoteException if an RMI communication error occurs
     */
    String requestHint(String username) throws RemoteException;
}

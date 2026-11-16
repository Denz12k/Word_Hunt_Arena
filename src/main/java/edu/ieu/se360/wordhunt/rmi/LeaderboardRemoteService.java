package edu.ieu.se360.wordhunt.rmi;

import edu.ieu.se360.wordhunt.model.MatchRecord;
import edu.ieu.se360.wordhunt.model.User;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote interface for querying global leaderboards and user match history over Java RMI.
 */
public interface LeaderboardRemoteService extends Remote {

    /**
     * Retrieves the top players ordered by total score descending.
     *
     * @param limit maximum number of top players to return
     * @return list of top {@link User} objects
     * @throws RemoteException if an RMI communication error occurs
     */
    List<User> getTopPlayers(int limit) throws RemoteException;

    /**
     * Retrieves the match history for a specific username.
     *
     * @param username the username to query
     * @return list of {@link MatchRecord} objects for matches involving the user
     * @throws RemoteException if an RMI communication error occurs
     */
    List<MatchRecord> getMatchHistory(String username) throws RemoteException;
}

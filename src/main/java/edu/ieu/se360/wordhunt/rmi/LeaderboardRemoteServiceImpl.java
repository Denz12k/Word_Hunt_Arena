package edu.ieu.se360.wordhunt.rmi;

import edu.ieu.se360.wordhunt.database.MatchDAO;
import edu.ieu.se360.wordhunt.database.UserDAO;
import edu.ieu.se360.wordhunt.model.MatchRecord;
import edu.ieu.se360.wordhunt.model.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * UnicastRemoteObject implementation of {@link LeaderboardRemoteService} backed by
 * {@link UserDAO} and {@link MatchDAO}.
 */
public class LeaderboardRemoteServiceImpl extends UnicastRemoteObject implements LeaderboardRemoteService {
    private static final long serialVersionUID = 1L;

    private final UserDAO userDAO;
    private final MatchDAO matchDAO;

    public LeaderboardRemoteServiceImpl() throws RemoteException {
        this(new UserDAO(), new MatchDAO());
    }

    public LeaderboardRemoteServiceImpl(UserDAO userDAO, MatchDAO matchDAO) throws RemoteException {
        super();
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
        this.matchDAO = matchDAO != null ? matchDAO : new MatchDAO();
    }

    @Override
    public List<User> getTopPlayers(int limit) throws RemoteException {
        try {
            int effectiveLimit = limit > 0 ? limit : 10;
            return userDAO.getTopPlayers(effectiveLimit);
        } catch (Exception e) {
            throw new RemoteException("Failed to fetch top players: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MatchRecord> getMatchHistory(String username) throws RemoteException {
        try {
            if (username == null || username.isBlank()) {
                return matchDAO.getAllMatches();
            }
            return matchDAO.getUserMatchHistory(username.trim());
        } catch (Exception e) {
            throw new RemoteException("Failed to fetch match history for '" + username + "': " + e.getMessage(), e);
        }
    }

    public UserDAO getUserDAO() {
        return userDAO;
    }

    public MatchDAO getMatchDAO() {
        return matchDAO;
    }
}

package edu.ieu.se360.wordhunt.rmi;

import edu.ieu.se360.wordhunt.database.UserDAO;
import edu.ieu.se360.wordhunt.model.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * UnicastRemoteObject implementation of {@link AuthRemoteService} backed by {@link UserDAO}.
 */
public class AuthRemoteServiceImpl extends UnicastRemoteObject implements AuthRemoteService {
    private static final long serialVersionUID = 1L;

    private final UserDAO userDAO;

    public AuthRemoteServiceImpl() throws RemoteException {
        this(new UserDAO());
    }

    public AuthRemoteServiceImpl(UserDAO userDAO) throws RemoteException {
        super();
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
    }

    @Override
    public synchronized User login(String username, String password) throws RemoteException {
        if (username == null || username.isBlank() || password == null) {
            return null;
        }
        try {
            User authenticated = userDAO.authenticate(username.trim(), password);
            if (authenticated != null) {
                authenticated.setPasswordHash(null);
                System.out.println("[RMI Auth] User logged in successfully: " + authenticated.getUsername());
            } else {
                System.out.println("[RMI Auth] Failed login attempt for username: " + username.trim());
            }
            return authenticated;
        } catch (Exception e) {
            throw new RemoteException("Database error during login for user '" + username + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized User register(String username, String password) throws RemoteException {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return null;
        }
        String trimmed = username.trim();
        try {
            boolean created = userDAO.registerUser(trimmed, password);
            if (!created) {
                System.out.println("[RMI Auth] Registration failed (username already exists or invalid): " + trimmed);
                return null;
            }
            User newUser = userDAO.getUserByUsername(trimmed);
            if (newUser != null) {
                newUser.setPasswordHash(null);
            }
            System.out.println("[RMI Auth] Registered new user: " + trimmed);
            return newUser;
        } catch (Exception e) {
            throw new RemoteException("Database error during registration for user '" + trimmed + "': " + e.getMessage(), e);
        }
    }

    public UserDAO getUserDAO() {
        return userDAO;
    }
}

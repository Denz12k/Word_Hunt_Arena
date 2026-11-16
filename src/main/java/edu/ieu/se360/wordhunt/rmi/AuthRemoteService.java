package edu.ieu.se360.wordhunt.rmi;

import edu.ieu.se360.wordhunt.model.User;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface for User Authentication and Registration over Java RMI.
 */
public interface AuthRemoteService extends Remote {

    /**
     * Authenticates a user with the provided username and password.
     *
     * @param username the player's username
     * @param password the player's raw password
     * @return the authenticated {@link User} object, or {@code null} if credentials are invalid
     * @throws RemoteException if an RMI communication error occurs
     */
    User login(String username, String password) throws RemoteException;

    /**
     * Registers a new user account with the provided username and password.
     *
     * @param username the desired unique username
     * @param password the raw password
     * @return the newly created {@link User} object, or {@code null} if the username already exists or inputs are invalid
     * @throws RemoteException if an RMI communication error occurs
     */
    User register(String username, String password) throws RemoteException;
}

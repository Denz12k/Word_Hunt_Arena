package edu.ieu.se360.wordhunt.config;

import java.io.Serializable;

/**
 * POJO representing the application configuration loaded from config.json.
 */
public class AppConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String serverHost = "localhost";
    private int tcpPort = 5555;
    private int rmiPort = 1099;
    private int matchDurationSeconds = 90;
    private int boardSize = 12;
    private int minWordLength = 3;

    public AppConfig() {
    }

    public AppConfig(String serverHost, int tcpPort, int rmiPort, int matchDurationSeconds, int boardSize, int minWordLength) {
        this.serverHost = serverHost;
        this.tcpPort = tcpPort;
        this.rmiPort = rmiPort;
        this.matchDurationSeconds = matchDurationSeconds;
        this.boardSize = boardSize;
        this.minWordLength = minWordLength;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public int getRmiPort() {
        return rmiPort;
    }

    public void setRmiPort(int rmiPort) {
        this.rmiPort = rmiPort;
    }

    public int getMatchDurationSeconds() {
        return matchDurationSeconds;
    }

    public void setMatchDurationSeconds(int matchDurationSeconds) {
        this.matchDurationSeconds = matchDurationSeconds;
    }

    public int getBoardSize() {
        return boardSize;
    }

    public void setBoardSize(int boardSize) {
        this.boardSize = boardSize;
    }

    public int getMinWordLength() {
        return minWordLength;
    }

    public void setMinWordLength(int minWordLength) {
        this.minWordLength = minWordLength;
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "serverHost='" + serverHost + '\'' +
                ", tcpPort=" + tcpPort +
                ", rmiPort=" + rmiPort +
                ", matchDurationSeconds=" + matchDurationSeconds +
                ", boardSize=" + boardSize +
                ", minWordLength=" + minWordLength +
                '}';
    }
}

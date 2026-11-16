package edu.ieu.se360.wordhunt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Singleton manager that loads and provides access to config.json using Gson.
 */
public class ConfigManager {
    private static final String DEFAULT_CONFIG_RESOURCE = "/config.json";
    private static volatile ConfigManager instance;

    private AppConfig config;
    private final Gson gson;

    private ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadFromResource(DEFAULT_CONFIG_RESOURCE);
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }

    /**
     * Loads configuration from the specified classpath resource.
     */
    public synchronized void loadFromResource(String resourcePath) {
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        try (InputStream is = ConfigManager.class.getResourceAsStream(normalizedPath)) {
            if (is != null) {
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    AppConfig loaded = gson.fromJson(reader, AppConfig.class);
                    if (loaded != null) {
                        this.config = loaded;
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to load config from resource " + resourcePath + ": " + e.getMessage());
        }
        if (this.config == null) {
            this.config = new AppConfig();
        }
    }

    /**
     * Loads configuration from an external file path.
     */
    public synchronized void loadFromFile(Path filePath) throws IOException {
        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            AppConfig loaded = gson.fromJson(reader, AppConfig.class);
            if (loaded != null) {
                this.config = loaded;
            }
        }
    }

    /**
     * Saves current configuration to an external file path as formatted JSON.
     */
    public synchronized void saveToFile(Path filePath) throws IOException {
        String json = gson.toJson(this.config);
        Files.writeString(filePath, json, StandardCharsets.UTF_8);
    }

    public AppConfig getConfig() {
        return config;
    }

    public String getServerHost() {
        return config.getServerHost();
    }

    public void setServerHost(String serverHost) {
        config.setServerHost(serverHost);
    }

    public int getTcpPort() {
        return config.getTcpPort();
    }

    public void setTcpPort(int tcpPort) {
        config.setTcpPort(tcpPort);
    }

    public int getRmiPort() {
        return config.getRmiPort();
    }

    public void setRmiPort(int rmiPort) {
        config.setRmiPort(rmiPort);
    }

    public int getMatchDurationSeconds() {
        return config.getMatchDurationSeconds();
    }

    public void setMatchDurationSeconds(int matchDurationSeconds) {
        config.setMatchDurationSeconds(matchDurationSeconds);
    }

    public int getBoardSize() {
        return config.getBoardSize();
    }

    public void setBoardSize(int boardSize) {
        config.setBoardSize(boardSize);
    }

    public int getMinWordLength() {
        return config.getMinWordLength();
    }

    public void setMinWordLength(int minWordLength) {
        config.setMinWordLength(minWordLength);
    }
}

package com.dbexplorer.health;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Per-connection dashboard configuration.
 * Persisted to ~/.dbexplorer/dashboard.json.
 */
public class DashboardConfig {

    public static final int DEFAULT_POLL_INTERVAL = 10;
    public static final int MIN_POLL_INTERVAL     = 5;
    public static final int MAX_POLL_INTERVAL     = 30;

    private int pollIntervalSeconds = DEFAULT_POLL_INTERVAL;
    private Map<String, Boolean> enabledPerConnection = new HashMap<>();

    // --- Accessors ---

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    /**
     * @throws IllegalArgumentException if interval is outside [5, 30]
     */
    public void setPollIntervalSeconds(int seconds) {
        if (seconds < MIN_POLL_INTERVAL || seconds > MAX_POLL_INTERVAL) {
            throw new IllegalArgumentException(
                "Poll interval must be between " + MIN_POLL_INTERVAL +
                " and " + MAX_POLL_INTERVAL + " seconds, got: " + seconds);
        }
        this.pollIntervalSeconds = seconds;
    }

    public boolean isEnabledFor(String connectionId) {
        return enabledPerConnection.getOrDefault(connectionId, false);
    }

    public void setEnabledFor(String connectionId, boolean enabled) {
        enabledPerConnection.put(connectionId, enabled);
    }

    public Map<String, Boolean> getEnabledPerConnection() {
        return enabledPerConnection;
    }

    // --- Persistence ---

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configPath() {
        return Paths.get(System.getProperty("user.home"), ".dbexplorer", "dashboard.json");
    }

    public static DashboardConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                DashboardConfig cfg = GSON.fromJson(r, DashboardConfig.class);
                if (cfg != null) {
                    // Clamp poll interval in case file was hand-edited
                    if (cfg.pollIntervalSeconds < MIN_POLL_INTERVAL ||
                        cfg.pollIntervalSeconds > MAX_POLL_INTERVAL) {
                        cfg.pollIntervalSeconds = DEFAULT_POLL_INTERVAL;
                    }
                    if (cfg.enabledPerConnection == null) {
                        cfg.enabledPerConnection = new HashMap<>();
                    }
                    return cfg;
                }
            } catch (Exception e) {
                System.err.println("[DashboardConfig] Failed to load config, using defaults: " + e.getMessage());
            }
        }
        return new DashboardConfig();
    }

    public static void save(DashboardConfig config) {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(config, w);
            }
        } catch (IOException e) {
            System.err.println("[DashboardConfig] Failed to save config: " + e.getMessage());
        }
    }
}

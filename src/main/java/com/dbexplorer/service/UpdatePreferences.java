package com.dbexplorer.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Manages the startup update check preference, persisted to
 * ~/.dbexplorer/update-prefs.json.
 */
public class UpdatePreferences {

    private static final Path PREFS_FILE = Path.of(
            System.getProperty("user.home"), ".dbexplorer", "update-prefs.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean startupCheckEnabled = true;

    public boolean isStartupCheckEnabled() {
        return startupCheckEnabled;
    }

    public void setStartupCheckEnabled(boolean enabled) {
        this.startupCheckEnabled = enabled;
    }

    /**
     * Persists the current preferences to disk.
     */
    public void save() {
        saveTo(PREFS_FILE);
    }

    /** Package-private: saves to a custom path (used in tests). */
    void saveTo(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("Failed to save update preferences: " + e.getMessage());
        }
    }

    /**
     * Loads preferences from disk, returning defaults if the file does not exist
     * or cannot be read.
     */
    public static UpdatePreferences load() {
        return loadFrom(PREFS_FILE);
    }

    /** Package-private: loads from a custom path (used in tests). */
    static UpdatePreferences loadFrom(Path path) {
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                UpdatePreferences prefs = GSON.fromJson(json, UpdatePreferences.class);
                if (prefs != null) {
                    return prefs;
                }
            } catch (IOException e) {
                System.err.println("Failed to load update preferences: " + e.getMessage());
            }
        }
        return new UpdatePreferences(); // defaults: startupCheckEnabled = true
    }
}

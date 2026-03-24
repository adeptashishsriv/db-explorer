package com.dbexplorer.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.IntelliJTheme;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Manages available themes and applies them at runtime.
 * Includes built-in FlatLaf themes plus custom colorful JSON themes.
 * Persists the user's last selection via Java Preferences.
 */
public final class ThemeManager {

    private static final String PREF_KEY = "dbexplorer.theme";
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);

    // Ordered map: display name -> LookAndFeel class name (or "custom:" prefix for JSON themes)
    private static final Map<String, String> THEMES = new LinkedHashMap<>();

    // Custom JSON theme resource paths
    private static final String[] CUSTOM_THEMES = {
        "Ocean Blue|/themes/ocean-blue.theme.json",
        "Forest Green|/themes/forest-green.theme.json",
        "Sunset Purple|/themes/sunset-purple.theme.json",
        "Cherry Red|/themes/cherry-red.theme.json",
        "Amber Warm|/themes/amber-warm.theme.json",
        "Arctic Frost|/themes/arctic-frost.theme.json",
        "Rose Garden|/themes/rose-garden.theme.json"
    };

    static {
        // Built-in FlatLaf themes
        THEMES.put("Flat Dark", FlatDarkLaf.class.getName());
        THEMES.put("Flat Darcula", FlatDarculaLaf.class.getName());
        THEMES.put("Flat Light", FlatLightLaf.class.getName());
        THEMES.put("Flat IntelliJ", FlatIntelliJLaf.class.getName());
        // Custom colorful themes
        for (String entry : CUSTOM_THEMES) {
            String[] parts = entry.split("\\|");
            THEMES.put(parts[0], "custom:" + parts[1]);
        }
        // System themes
        THEMES.put("System", UIManager.getSystemLookAndFeelClassName());
        THEMES.put("Metal", UIManager.getCrossPlatformLookAndFeelClassName());
    }

    private static final List<Runnable> themeChangeListeners = new ArrayList<>();

    private ThemeManager() {}

    /** Register a callback to be invoked after a theme change. */
    public static void addThemeChangeListener(Runnable listener) {
        themeChangeListeners.add(listener);
    }

    public static void removeThemeChangeListener(Runnable listener) {
        themeChangeListeners.remove(listener);
    }

    public static String[] getThemeNames() {
        return THEMES.keySet().toArray(String[]::new);
    }

    public static String getSavedThemeName() {
        return PREFS.get(PREF_KEY, "Flat Dark");
    }

    /** Apply the initial theme before any UI is created. */
    public static void applyInitialTheme() {
        String name = getSavedThemeName();
        String value = THEMES.getOrDefault(name, FlatDarkLaf.class.getName());
        try {
            if (value.startsWith("custom:")) {
                applyCustomTheme(value.substring(7));
            } else {
                UIManager.setLookAndFeel(value);
            }
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ignored) {}
        }
    }

    /** Switch theme at runtime and update all open windows. */
    public static void applyTheme(String themeName) {
        String value = THEMES.get(themeName);
        if (value == null) return;

        try {
            if (value.startsWith("custom:")) {
                applyCustomTheme(value.substring(7));
            } else {
                UIManager.setLookAndFeel(value);
            }
            for (Window w : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(w);
            }
            PREFS.put(PREF_KEY, themeName);
            themeChangeListeners.forEach(Runnable::run);
        } catch (Exception e) {
            System.err.println("Failed to apply theme: " + themeName + " - " + e.getMessage());
        }
    }

    /** Load and apply a custom FlatLaf JSON theme from classpath resources. */
    private static void applyCustomTheme(String resourcePath) throws Exception {
        InputStream is = ThemeManager.class.getResourceAsStream(resourcePath);
        if (is == null) throw new IllegalArgumentException("Theme not found: " + resourcePath);
        IntelliJTheme.setup(is);
        is.close();
    }
}

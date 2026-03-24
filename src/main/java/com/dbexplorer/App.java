package com.dbexplorer;

import com.dbexplorer.ui.MainFrame;
import com.dbexplorer.ui.ThemeManager;

import javax.swing.*;

public class App {
    public static void main(String[] args) {
        ThemeManager.applyInitialTheme();

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}

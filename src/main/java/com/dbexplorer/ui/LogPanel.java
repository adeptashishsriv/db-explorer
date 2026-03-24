package com.dbexplorer.ui;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Console/log panel that displays query logs, execution times, and errors.
 */
public class LogPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final JTextArea logArea;

    public LogPanel() {
        super(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(43, 43, 43));
        logArea.setForeground(new Color(187, 187, 187));
        logArea.setCaretColor(Color.WHITE);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Console / Logs"));
        add(scroll, BorderLayout.CENTER);
    }

    public void logInfo(String message) {
        append("[INFO  " + timestamp() + "] " + message);
    }

    public void logQuery(String sql, long timeMs) {
        append("[QUERY " + timestamp() + "] " + sql.trim().replaceAll("\\s+", " "));
        append("[TIME  " + timestamp() + "] Executed in " + timeMs + " ms");
    }

    public void logError(String message, Throwable t) {
        append("[ERROR " + timestamp() + "] " + message);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            append(sw.toString());
        }
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> logArea.setText(""));
    }

    private void append(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private String timestamp() {
        return LocalTime.now().format(TIME_FMT);
    }
}

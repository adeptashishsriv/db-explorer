package com.dbexplorer.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Panel that displays query execution plan output as monospaced text.
 */
public class ExplainPlanPanel extends JPanel {

    private final JTextArea planArea;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public ExplainPlanPanel() {
        super(new BorderLayout());
        planArea = new JTextArea();
        planArea.setEditable(false);
        planArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scroll = new JScrollPane(planArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Execution Plan"));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("Generating plan...");
        progressBar.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(scroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void displayPlan(String plan, long timeMs) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(false);
            planArea.setText(plan);
            planArea.setCaretPosition(0);
            statusLabel.setText("Plan generated in " + timeMs + " ms");
        });
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    /** Show the animated progress bar while generating the plan. */
    public void showLoading() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(true);
            statusLabel.setText("Generating plan...");
        });
    }

    /** Hide the progress bar. */
    public void hideLoading() {
        SwingUtilities.invokeLater(() -> progressBar.setVisible(false));
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            planArea.setText("");
            progressBar.setVisible(false);
            statusLabel.setText(" ");
        });
    }
}

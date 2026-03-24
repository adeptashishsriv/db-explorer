package com.dbexplorer.ui;

import com.dbexplorer.model.LazyQueryResult;
import com.dbexplorer.model.QueryResult;
import com.dbexplorer.service.QueryExecutor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.util.List;
import java.util.Vector;

/**
 * Displays query results in a JTable with dynamic columns.
 * Supports lazy loading: fetches the next page of rows when the user scrolls near the bottom.
 */
public class ResultPanel extends JPanel {

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;
    private final JScrollPane scrollPane;
    private final JProgressBar progressBar;

    private LazyQueryResult currentLazyResult;
    private QueryExecutor queryExecutor;
    private boolean fetching = false;

    public ResultPanel() {
        super(new BorderLayout());
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(true);

        scrollPane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Detect scroll near bottom to trigger lazy fetch
        scrollPane.getVerticalScrollBar().addAdjustmentListener(this::onScroll);

        // Indeterminate progress bar — hidden by default
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("Executing query...");
        progressBar.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        // Bottom strip: progress bar + status label
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void setQueryExecutor(QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    /** Show the animated progress bar while a query is running. */
    public void showLoading() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(true);
            statusLabel.setText("Executing...");
        });
    }

    /** Hide the progress bar when the query finishes. */
    public void hideLoading() {
        SwingUtilities.invokeLater(() -> progressBar.setVisible(false));
    }

    /**
     * Display the first page of a lazy result set.
     */
    public void displayLazyResult(LazyQueryResult lazyResult) {
        closeLazyResult();
        this.currentLazyResult = lazyResult;

        SwingUtilities.invokeLater(() -> {
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);

            for (String col : lazyResult.getColumns()) {
                tableModel.addColumn(col);
            }

            // Fetch first page
            fetchNextPage();
        });
    }

    /**
     * Display DynamoDB PartiQL results directly (no lazy loading).
     */
    public void displayDynamoResult(List<String> columns, List<List<Object>> rows, long timeMs) {
        closeLazyResult();
        SwingUtilities.invokeLater(() -> {
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
            for (String col : columns) {
                tableModel.addColumn(col);
            }
            for (List<Object> row : rows) {
                tableModel.addRow(new Vector<>(row));
            }
            columnsAutoSized = false;
            autoSizeColumnsOnce();
            statusLabel.setText(rows.size() + " row(s) returned in " + timeMs + " ms");
        });
    }

    /**
     * Display a non-result-set outcome (UPDATE/INSERT/DELETE).
     */
    public void displayResult(QueryResult result) {
        closeLazyResult();
        SwingUtilities.invokeLater(() -> {
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
            statusLabel.setText(result.affectedRows() + " row(s) affected in "
                    + result.executionTimeMs() + " ms");
        });
    }

    private void onScroll(AdjustmentEvent e) {
        if (currentLazyResult == null || currentLazyResult.isExhausted() || fetching) return;

        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        int extent = vBar.getModel().getExtent();
        int max = vBar.getMaximum();
        int value = vBar.getValue();

        // Trigger fetch when scrolled within 90% of the content
        if (value + extent >= max * 0.9) {
            fetchNextPage();
        }
    }

    private void fetchNextPage() {
        if (currentLazyResult == null || currentLazyResult.isExhausted() || fetching) return;
        if (queryExecutor == null) return;

        fetching = true;
        SwingUtilities.invokeLater(() ->
                statusLabel.setText(currentLazyResult.getFetchedRowCount()
                        + " row(s) loaded | fetching more..."));

        queryExecutor.fetchNextPageAsync(currentLazyResult,
                (List<List<Object>> page) -> SwingUtilities.invokeLater(() -> {
                    for (var row : page) {
                        tableModel.addRow(new Vector<>(row));
                    }
                    autoSizeColumnsOnce();
                    updateStatusLabel();
                    fetching = false;
                }),
                (ex) -> SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error fetching rows: " + ex.getMessage());
                    fetching = false;
                })
        );
    }

    private void updateStatusLabel() {
        if (currentLazyResult == null) return;
        int count = currentLazyResult.getFetchedRowCount();
        String suffix = currentLazyResult.isExhausted()
                ? " (all rows loaded)"
                : " (scroll down for more)";
        statusLabel.setText(count + " row(s) loaded in "
                + currentLazyResult.getExecutionTimeMs() + " ms" + suffix);
    }

    private boolean columnsAutoSized = false;

    private void autoSizeColumnsOnce() {
        if (columnsAutoSized) return;
        columnsAutoSized = true;
        for (int i = 0; i < table.getColumnCount(); i++) {
            int maxWidth = 80;
            for (int r = 0; r < Math.min(table.getRowCount(), 100); r++) {
                var renderer = table.getCellRenderer(r, i);
                var comp = table.prepareRenderer(renderer, r, i);
                maxWidth = Math.max(maxWidth, comp.getPreferredSize().width + 12);
            }
            table.getColumnModel().getColumn(i).setPreferredWidth(Math.min(maxWidth, 300));
        }
    }

    /**
     * Display an error inline in the result area so the user sees it clearly.
     * Shows a styled error panel with icon, title, and detail message.
     */
    public void displayError(String title, String detail) {
        closeLazyResult();
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);

            // Build error display as a single-row, single-column table
            tableModel.addColumn("Error");
            tableModel.addRow(new Object[]{title + (detail != null ? " — " + detail : "")});

            // Color the status label red
            statusLabel.setForeground(new Color(220, 50, 50));
            statusLabel.setText("\u26A0 " + title);

            // Auto-size so the full message is visible
            if (table.getColumnCount() > 0) {
                table.getColumnModel().getColumn(0).setPreferredWidth(800);
            }
        });
    }

    public void clear() {
        closeLazyResult();
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            statusLabel.setText(" ");
        });
    }

    private void closeLazyResult() {
        if (currentLazyResult != null) {
            currentLazyResult.close();
            currentLazyResult = null;
        }
        columnsAutoSized = false;
        fetching = false;
    }
}

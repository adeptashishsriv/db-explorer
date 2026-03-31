package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.AdjustmentEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.TableColumnModel;

import com.dbexplorer.model.LazyQueryResult;
import com.dbexplorer.model.QueryResult;
import com.dbexplorer.service.QueryExecutor;

/**
 * Displays query results in a JTable with lazy loading.
 *
 * Performance improvements:
 * - Rows stored as String[] in LazyQueryResult (less GC pressure)
 * - Batch row insertion: fires a single tableDataChanged event per page
 *   instead of one event per row
 * - Column auto-sizing uses char-width estimates from LazyQueryResult
 *   (no prepareRenderer loop on the EDT)
 * - Fetch size raised to 500
 * - First page fetch starts immediately on the background thread that
 *   delivers the LazyQueryResult, avoiding an extra EDT round-trip
 */
public class ResultPanel extends JPanel {

    // Approximate pixel width per character for the default monospaced table font
    private static final int CHAR_PX = 8;
    private static final int COL_MIN = 60;
    private static final int COL_MAX = 400;

    private final JTable          table;
    private final SortableTableModel tableModel;
    private final JLabel          statusLabel;
    private final JScrollPane     scrollPane;
    private final JProgressBar    progressBar;

    private LazyQueryResult currentLazyResult;
    private QueryExecutor   queryExecutor;
    private volatile boolean fetching = false;

    public ResultPanel() {
        super(new BorderLayout());

        tableModel = new SortableTableModel();

        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(true);

        // Install sort indicator as the default header renderer so it survives
        // fireTableStructureChanged() rebuilding the column model.
        table.getTableHeader().setDefaultRenderer(
                new SortIndicatorHeaderRenderer(tableModel, table.getTableHeader().getDefaultRenderer()));

        table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                if (col >= 0) {
                    tableModel.cycleSort(col);
                    table.getTableHeader().repaint();
                }
            }
        });
        // Faster rendering — skip individual cell focus borders
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 0));

        scrollPane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(this::onScroll);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("Executing query...");
        progressBar.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(progressBar,  BorderLayout.NORTH);
        bottomPanel.add(statusLabel,  BorderLayout.SOUTH);

        add(scrollPane,   BorderLayout.CENTER);
        add(bottomPanel,  BorderLayout.SOUTH);
    }

    public void setQueryExecutor(QueryExecutor qe) { this.queryExecutor = qe; }

    public void showLoading() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(true);
            statusLabel.setText("Executing...");
        });
    }

    public void hideLoading() {
        SwingUtilities.invokeLater(() -> progressBar.setVisible(false));
    }

    // ── Lazy result display ───────────────────────────────────────────────────

    /**
     * Called from the background thread that just received the LazyQueryResult.
     * The first page is already pre-fetched in QueryExecutor, so we render it
     * immediately on the EDT without any additional async hop.
     */
    public void displayLazyResult(LazyQueryResult lazyResult) {
        closeLazyResult();
        this.currentLazyResult = lazyResult;

        // Take the first page (pre-fetched by QueryExecutor) and clear the reference
        List<String[]> firstPage = lazyResult.takeFirstPage();
        int[] colWidths = lazyResult.getMaxColWidths().clone();

        SwingUtilities.invokeLater(() -> {
            tableModel.resetSort();
            ColumnType[] columnTypes = new ColumnType[lazyResult.getColumnTypes().length];
            for (int i = 0; i < lazyResult.getColumnTypes().length; i++) {
                columnTypes[i] = ColumnType.fromSqlType(lazyResult.getColumnTypes()[i]);
            }
            tableModel.initColumns(lazyResult.getColumns(), columnTypes);
            appendPageToTable(firstPage);
            applyColumnWidths(colWidths);
            updateStatusLabel();
        });
    }

    /**
     * Called from the background thread with the first page already fetched.
     * Replaces displayLazyResult to avoid re-fetching.
     */
    public void displayLazyResultWithFirstPage(LazyQueryResult lazyResult, List<String[]> firstPage) {
        closeLazyResult();
        this.currentLazyResult = lazyResult;

        int[] colWidths = lazyResult.getMaxColWidths().clone();

        SwingUtilities.invokeLater(() -> {
            tableModel.resetSort();
            ColumnType[] columnTypes = new ColumnType[lazyResult.getColumnTypes().length];
            for (int i = 0; i < lazyResult.getColumnTypes().length; i++) {
                columnTypes[i] = ColumnType.fromSqlType(lazyResult.getColumnTypes()[i]);
            }
            tableModel.initColumns(lazyResult.getColumns(), columnTypes);
            appendPageToTable(firstPage);
            applyColumnWidths(colWidths);
            updateStatusLabel();
        });
    }

    public void displayDynamoResult(List<String> columns, List<List<Object>> rows, long timeMs) {
        closeLazyResult();
        SwingUtilities.invokeLater(() -> {
            tableModel.resetSort();
            ColumnType[] allTextTypes = new ColumnType[columns.size()];
            for (int i = 0; i < allTextTypes.length; i++) allTextTypes[i] = ColumnType.TEXT;
            tableModel.initColumns(columns, allTextTypes);

            // Batch insert — suppress events, then fire one dataChanged
            tableModel.setRowCount(rows.size());
            for (int r = 0; r < rows.size(); r++) {
                List<Object> row = rows.get(r);
                for (int c = 0; c < row.size(); c++)
                    tableModel.setValueAt(row.get(c), r, c);
            }
            applyColumnWidths(null);
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            statusLabel.setText(rows.size() + " row(s) returned in " + timeMs + " ms");
        });
    }

    public void displayResult(QueryResult result) {
        closeLazyResult();
        SwingUtilities.invokeLater(() -> {
            tableModel.resetSort();
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            statusLabel.setText(result.affectedRows() + " row(s) affected in "
                    + result.executionTimeMs() + " ms");
        });
    }

    public void displayError(String title, String detail) {
        closeLazyResult();
        SwingUtilities.invokeLater(() -> {
            tableModel.resetSort();
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
            tableModel.addColumn("Error");
            tableModel.addRow(new Object[]{title + (detail != null ? " — " + detail : "")});
            statusLabel.setForeground(new Color(220, 50, 50));
            statusLabel.setText("\u26A0 " + title);
            if (table.getColumnCount() > 0)
                table.getColumnModel().getColumn(0).setPreferredWidth(800);
        });
    }

    public void clear() {
        closeLazyResult();
        SwingUtilities.invokeLater(() -> {
            tableModel.resetSort();
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            statusLabel.setText(" ");
        });
    }

    // ── Scroll-triggered lazy fetch ───────────────────────────────────────────

    private void onScroll(AdjustmentEvent e) {
        if (currentLazyResult == null || currentLazyResult.isExhausted() || fetching) return;
        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        int value  = vBar.getValue();
        int extent = vBar.getModel().getExtent();
        int max    = vBar.getMaximum();
        if (value + extent >= max * 0.88) fetchNextPage();
    }

    private void fetchNextPage() {
        if (currentLazyResult == null || currentLazyResult.isExhausted() || fetching) return;
        if (queryExecutor == null) return;

        fetching = true;
        final int rowsBefore = currentLazyResult.getFetchedRowCount();
        SwingUtilities.invokeLater(() ->
                statusLabel.setText(rowsBefore + " row(s) loaded | fetching more…"));

        queryExecutor.fetchNextPageAsync(currentLazyResult,
                (List<String[]> page) -> SwingUtilities.invokeLater(() -> {
                    appendPageToTable(page);
                    applyColumnWidths(currentLazyResult.getMaxColWidths());
                    updateStatusLabel();
                    fetching = false;
                }),
                (ex) -> SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error fetching rows: " + ex.getMessage());
                    fetching = false;
                })
        );
    }

    /**
     * Batch-insert a page of String[] rows into the table model.
     */
    private void appendPageToTable(List<String[]> page) {
        if (page.isEmpty()) return;
        tableModel.appendRows(page);
    }

    /**
     * Size columns using the max observed character widths tracked in LazyQueryResult.
     * O(columns) — no renderer calls, no EDT-blocking loops.
     * Only applied once (first page).
     */
    private boolean columnWidthsApplied = false;

    private void applyColumnWidths(int[] maxCharWidths) {
        if (columnWidthsApplied) return;
        columnWidthsApplied = true;

        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            int charW = (maxCharWidths != null && i < maxCharWidths.length)
                    ? maxCharWidths[i] : 10;
            // Add header label width as a floor
            String header = (String) cm.getColumn(i).getHeaderValue();
            charW = Math.max(charW, header != null ? header.length() : 4);
            int px = Math.max(COL_MIN, Math.min(COL_MAX, charW * CHAR_PX + 16));
            cm.getColumn(i).setPreferredWidth(px);
        }
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

    private void closeLazyResult() {
        if (currentLazyResult != null) {
            currentLazyResult.close();
            currentLazyResult = null;
        }
        columnWidthsApplied = false;
        fetching = false;
    }
}

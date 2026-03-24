package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.SQLException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.model.DatabaseType;
import com.dbexplorer.model.LazyQueryResult;
import com.dbexplorer.model.QueryResult;
import com.dbexplorer.service.ConnectionManager;
import com.dbexplorer.service.DynamoDbExecutor;
import com.dbexplorer.service.QueryExecutor;

public class MainFrame extends JFrame {

    private final ConnectionManager connectionManager;
    private final QueryExecutor queryExecutor;

    private final ConnectionListPanel connectionListPanel;
    private final SqlEditorPanel sqlEditorPanel;
    private final LogPanel logPanel;
    private final JLabel statusLabel;
    
    private JButton newTabBtn;
    private ThemeAnimationOverlay animationOverlay;

    public MainFrame() {
        super("DB Explorer");
        connectionManager = new ConnectionManager();
        queryExecutor = new QueryExecutor();

        connectionListPanel = new ConnectionListPanel(connectionManager);
        sqlEditorPanel = new SqlEditorPanel();
        sqlEditorPanel.setQueryExecutor(queryExecutor);
        logPanel = new LogPanel();
        statusLabel = new JLabel("No active connection");

        initLayout();
        initToolbar();
        initEventHandlers();
        initWindowBehavior();

        // Set window icon from resource
        java.awt.Image windowIcon = AboutDialog.loadWindowIcon();
        if (windowIcon != null) setIconImage(windowIcon);

        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Install animation overlay as glass pane
        animationOverlay = new ThemeAnimationOverlay();
        animationOverlay.setBounds(0, 0, 1200, 800);
        setGlassPane(animationOverlay);

        // Keep overlay sized to frame
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                animationOverlay.setBounds(0, 0, getWidth(), getHeight());
            }
        });
    }

    private void initLayout() {
        setLayout(new BorderLayout());

        // Console log is shared across all tabs, sits at the very bottom
        JPanel consoleWrapper = new JPanel(new BorderLayout());
        consoleWrapper.setBorder(BorderFactory.createTitledBorder("Console"));
        consoleWrapper.add(logPanel, BorderLayout.CENTER);
        consoleWrapper.setPreferredSize(new Dimension(0, 140));

        // The editor panel already contains per-tab splits (editor + results)
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                sqlEditorPanel, consoleWrapper);
        rightSplit.setDividerLocation(550);
        rightSplit.setResizeWeight(0.85);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                connectionListPanel, rightSplit);
        mainSplit.setDividerLocation(260);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        add(mainSplit, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    private void initToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorderPainted(true);

        JButton addBtn = makeToolButton("Add Connection", "➕");
        addBtn.addActionListener(e -> showAddConnectionDialog());

        JButton disconnectBtn = makeToolButton("Disconnect Tab", "⛔");
        disconnectBtn.addActionListener(e -> disconnectActiveTab());

        JButton runBtn = makeToolButton("Run Query (Ctrl+Enter)", "▶");
        runBtn.setForeground(new Color(40, 160, 40));
        runBtn.addActionListener(e -> runQuery());

        newTabBtn = makeToolButton("New Tab", "📄");
        // Updated action for New Tab button
        newTabBtn.addActionListener(e -> openNewTabWithSelectedConnection());
        newTabBtn.setEnabled(false); // Initially disabled

        JButton clearBtn = makeToolButton("Clear Console", "🗑");
        clearBtn.setForeground(new Color(180, 40, 40));
        clearBtn.addActionListener(e -> {
            logPanel.clear();
            SqlEditorPanel.TabState ts = sqlEditorPanel.getActiveTabState();
            if (ts != null) {
                ts.resultPanel.clear();
                ts.explainPlanPanel.clear();
            }
        });

        JButton explainBtn = makeToolButton("Explain Plan", "📊");
        explainBtn.addActionListener(e -> runExplainPlan());

        JComboBox<String> themeCombo = new JComboBox<>(ThemeManager.getThemeNames());
        themeCombo.setSelectedItem(ThemeManager.getSavedThemeName());
        themeCombo.setMaximumSize(new Dimension(170, 28));
        themeCombo.setToolTipText("Switch Theme");
        themeCombo.addActionListener(e -> {
            String selected = (String) themeCombo.getSelectedItem();
            if (selected != null) {
                // Play animation first
                ThemeAnimationOverlay.AnimationType anim =
                        ThemeAnimationOverlay.animationFor(selected);

                ThemeManager.applyTheme(selected);
                SwingUtilities.updateComponentTreeUI(this);

                // Re-install glass pane after updateComponentTreeUI (it resets it)
                setGlassPane(animationOverlay);
                animationOverlay.setBounds(0, 0, getWidth(), getHeight());

                if (anim != ThemeAnimationOverlay.AnimationType.NONE) {
                    animationOverlay.play(anim);
                }
            }
        });

        toolbar.add(addBtn);
        toolbar.add(disconnectBtn);
        toolbar.addSeparator();
        toolbar.add(runBtn);
        toolbar.add(newTabBtn);
        toolbar.add(explainBtn);
        toolbar.addSeparator();
        toolbar.add(clearBtn);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(new JLabel("Theme: "));
        toolbar.add(themeCombo);

        JButton aboutBtn = makeToolButton("About", "ℹ");
        // Make the About button a bit more prominent
        aboutBtn.setFont(aboutBtn.getFont().deriveFont(Font.BOLD, 14f));
        // Use a distinct color for the about button to make it pop
        // A nice shade of blue often works well for information/help
        aboutBtn.setForeground(new Color(0, 120, 215)); 
        
        aboutBtn.addActionListener(e -> new AboutDialog(this).setVisible(true));
        toolbar.addSeparator();
        toolbar.add(aboutBtn);

        add(toolbar, BorderLayout.NORTH);
    }

    private JButton makeToolButton(String tooltip, String icon) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setFocusable(false);
        btn.setFont(btn.getFont().deriveFont(16f));
        return btn;
    }

    private void initEventHandlers() {
        connectionListPanel.setOnConnect(this::toggleConnection);
        connectionListPanel.setOnEdit(this::showEditConnectionDialog);
        connectionListPanel.setOnDelete(this::deleteConnection);
        connectionListPanel.setOnOpenQueryTab(this::openQueryTabForConnection);
        connectionListPanel.setOnViewData(this::viewTableData);
        connectionListPanel.addTreeSelectionListener(e -> {
            ConnectionInfo info = connectionListPanel.getSelectedConnection();
            newTabBtn.setEnabled(info != null);
        });
        sqlEditorPanel.setOnRunQuery(this::runQuery);
        sqlEditorPanel.addChangeListener(e -> updateStatus());
    }

    private void initWindowBehavior() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                queryExecutor.shutdown();
                connectionManager.disconnectAll();
                dispose();
                System.exit(0);
            }
        });
    }

    // --- Connection actions ---

    private void showAddConnectionDialog() {
        ConnectionDialog dialog = new ConnectionDialog(this, connectionManager, null);
        dialog.setVisible(true);
        ConnectionInfo result = dialog.getResult();
        if (result != null) {
            connectionManager.addConnection(result);
            connectionListPanel.refreshList();
            logPanel.logInfo("Connection saved: " + result.getName());
        }
    }

    private void showEditConnectionDialog(ConnectionInfo info) {
        ConnectionDialog dialog = new ConnectionDialog(this, connectionManager, info);
        dialog.setVisible(true);
        ConnectionInfo result = dialog.getResult();
        if (result != null) {
            connectionManager.updateConnection(result);
            connectionListPanel.refreshList();
            logPanel.logInfo("Connection updated: " + result.getName());
        }
    }

    private void deleteConnection(ConnectionInfo info) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete connection '" + info.getName() + "'?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            connectionManager.deleteConnection(info.getId());
            sqlEditorPanel.clearConnectionFromTabs(info.getId());
            connectionListPanel.refreshList();
            logPanel.logInfo("Connection deleted: " + info.getName());
            updateStatus();
        }
    }

    private void toggleConnection(ConnectionInfo info) {
        if (connectionManager.isConnected(info.getId())) {
            connectionManager.disconnect(info.getId());
            sqlEditorPanel.clearConnectionFromTabs(info.getId());
            logPanel.logInfo("Disconnected: " + info.getName());
        } else {
            try {
                connectionManager.connect(info);
                String connDetail = info.getDbType() == DatabaseType.DYNAMODB
                        ? info.getAwsRegion() : info.getJdbcUrl();
                logPanel.logInfo("Connected: " + info.getName()
                        + " (" + connDetail + ")");
                sqlEditorPanel.setActiveTabConnection(info);
                connectionListPanel.refreshList();
                connectionListPanel.loadSchemasForConnection(info);
            } catch (SQLException ex) {
                logPanel.logError("Connection failed: " + info.getName(), ex);
                JOptionPane.showMessageDialog(this,
                        "Connection failed:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        connectionListPanel.refreshList();
        updateStatus();
    }

    private void openQueryTabForConnection(ConnectionInfo info) {
        if (!connectionManager.isConnected(info.getId())) {
            try {
                connectionManager.connect(info);
                String connDetail = info.getDbType() == DatabaseType.DYNAMODB
                        ? info.getAwsRegion() : info.getJdbcUrl();
                logPanel.logInfo("Connected: " + info.getName()
                        + " (" + connDetail + ")");
                connectionListPanel.refreshList();
                connectionListPanel.loadSchemasForConnection(info);
            } catch (SQLException ex) {
                logPanel.logError("Connection failed: " + info.getName(), ex);
                JOptionPane.showMessageDialog(this,
                        "Connection failed:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        sqlEditorPanel.addNewTab(info);
        updateStatus();
    }
    
    private void viewTableData(ConnectionInfo info, String sql) {
         if (!connectionManager.isConnected(info.getId())) {
            try {
                connectionManager.connect(info);
            } catch (SQLException ex) {
                logPanel.logError("Connection failed: " + info.getName(), ex);
                JOptionPane.showMessageDialog(this,
                        "Connection failed:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        // Open new tab with the SQL
        sqlEditorPanel.addNewTab(info, sql);
        
        // Execute it immediately
        SwingUtilities.invokeLater(this::runQuery);
        updateStatus();
    }

    private void openNewTabWithSelectedConnection() {
        // Get the selected connection from the left pane
        ConnectionInfo selectedInfo = connectionListPanel.getSelectedConnection();
        
        if (selectedInfo != null) {
            // Check if connected, if not try to connect
            if (!connectionManager.isConnected(selectedInfo.getId())) {
                try {
                    connectionManager.connect(selectedInfo);
                    String connDetail = selectedInfo.getDbType() == DatabaseType.DYNAMODB
                            ? selectedInfo.getAwsRegion() : selectedInfo.getJdbcUrl();
                    logPanel.logInfo("Connected: " + selectedInfo.getName()
                            + " (" + connDetail + ")");
                    connectionListPanel.refreshList();
                    connectionListPanel.loadSchemasForConnection(selectedInfo);
                } catch (SQLException ex) {
                    logPanel.logError("Connection failed: " + selectedInfo.getName(), ex);
                    JOptionPane.showMessageDialog(this,
                            "Connection failed:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return; // Don't open tab if connection fails
                }
            }
            // Open new tab tied to this connection
            sqlEditorPanel.addNewTab(selectedInfo);
            updateStatus();
        } else {
            // No connection selected, just open a blank tab
            sqlEditorPanel.addNewTab();
        }
    }

    private void disconnectActiveTab() {
        ConnectionInfo tabConn = sqlEditorPanel.getActiveTabConnection();
        if (tabConn == null) {
            logPanel.logInfo("Current tab has no connection.");
            return;
        }
        sqlEditorPanel.setActiveTabConnection(null);
        logPanel.logInfo("Tab disconnected from: " + tabConn.getName());
        updateStatus();
    }

    // --- Query execution (uses the current tab's own result panels) ---

    private void runQuery() {
        SqlEditorPanel.TabState ts = sqlEditorPanel.getActiveTabState();
        if (ts == null) return;

        ConnectionInfo tabConn = ts.connectionInfo;
        if (tabConn == null) {
            JOptionPane.showMessageDialog(this,
                    "This tab has no connection.\n"
                    + "Double-click a connection or right-click → 'Open Query Tab' to bind one.",
                    "No Connection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sql = sqlEditorPanel.getActiveSQL();
        if (sql == null || sql.isBlank()) {
            logPanel.logInfo("Nothing to execute.");
            return;
        }

        ResultPanel rp = ts.resultPanel;
        ts.bottomTabs.setSelectedComponent(rp);

        // DynamoDB path — use PartiQL via DynamoDbExecutor
        if (tabConn.getDbType() == DatabaseType.DYNAMODB) {
            DynamoDbExecutor dynamo = connectionManager.getDynamoDbExecutor();
            if (!dynamo.isConnected(tabConn.getId())) {
                // Attempt auto-reconnect using connection info from tab
                try {
                    connectionManager.connect(tabConn);
                    logPanel.logInfo("Reconnected to DynamoDB: " + tabConn.getName());
                    connectionListPanel.refreshList();
                } catch (SQLException e) {
                    rp.displayError("Connection Lost",
                            "DynamoDB connection lost for " + tabConn.getName() + " and reconnection failed: " + e.getMessage());
                    logPanel.logError("DynamoDB connection lost for " + tabConn.getName()
                            + ". Reconnect and try again.", e);
                    return;
                }
            }

            statusLabel.setText("Executing PartiQL on " + tabConn.getName() + "...");
            logPanel.logInfo("[" + tabConn.getName() + "] PartiQL: "
                    + sql.trim().replaceAll("\\s+", " "));
            rp.showLoading();

            dynamo.executePartiQLAsync(tabConn.getId(), sql,
                    (DynamoDbExecutor.PartiQLResult result) -> SwingUtilities.invokeLater(() -> {
                        rp.hideLoading();
                        rp.displayDynamoResult(result.columns(), result.rows(), result.executionTimeMs());
                        logPanel.logInfo("PartiQL returned " + result.rowCount()
                                + " item(s) in " + result.executionTimeMs() + " ms");
                        statusLabel.setText(result.rowCount() + " item(s) | "
                                + result.executionTimeMs() + " ms | " + tabConn.getName());
                    }),
                    (Exception ex) -> SwingUtilities.invokeLater(() -> {
                        rp.hideLoading();
                        String msg = extractErrorMessage(ex);
                        rp.displayError("DynamoDB Error", msg);
                        logPanel.logError("DynamoDB Error: " + ex.getMessage(), ex);
                        statusLabel.setText("Query failed | " + tabConn.getName());
                    })
            );
            return;
        }

        // JDBC path
        Connection conn = connectionManager.getActiveConnection(tabConn.getId());
        if (conn == null) {
            // Attempt auto-reconnect using connection info from tab
             try {
                 conn = connectionManager.connect(tabConn);
                 logPanel.logInfo("Reconnected to: " + tabConn.getName());
                 connectionListPanel.refreshList(); // Refresh UI status
             } catch (SQLException e) {
                 rp.displayError("Connection Lost",
                         "Connection lost for " + tabConn.getName() + " and reconnection failed: " + e.getMessage());
                 logPanel.logError("Connection lost for " + tabConn.getName()
                         + ". Reconnect and try again.", e);
                 return;
             }
        }

        statusLabel.setText("Executing on " + tabConn.getName() + "...");
        logPanel.logInfo("[" + tabConn.getName() + "] Executing: "
                + sql.trim().replaceAll("\\s+", " "));
        rp.showLoading();

        queryExecutor.executeAsync(conn, sql,
                (LazyQueryResult lazyResult) -> SwingUtilities.invokeLater(() -> {
                    rp.hideLoading();
                    rp.displayLazyResult(lazyResult);
                    logPanel.logQuery(sql, lazyResult.getExecutionTimeMs());
                    statusLabel.setText("Query executed in "
                            + lazyResult.getExecutionTimeMs() + " ms | fetch size "
                            + LazyQueryResult.DEFAULT_FETCH_SIZE
                            + " | " + tabConn.getName());
                }),
                (QueryResult result) -> SwingUtilities.invokeLater(() -> {
                    rp.hideLoading();
                    rp.displayResult(result);
                    logPanel.logQuery(sql, result.executionTimeMs());
                    statusLabel.setText(result.affectedRows() + " rows affected | "
                            + result.executionTimeMs() + " ms | " + tabConn.getName());
                }),
                (SQLException ex) -> SwingUtilities.invokeLater(() -> {
                    rp.hideLoading();
                    String errTitle = "SQL Error [" + ex.getErrorCode() + "]";
                    rp.displayError(errTitle, ex.getMessage());
                    logPanel.logError(errTitle + " "
                            + ex.getSQLState() + ": " + ex.getMessage(), ex);
                    statusLabel.setText("Query failed | " + tabConn.getName());
                })
        );
    }

    private void runExplainPlan() {
        SqlEditorPanel.TabState ts = sqlEditorPanel.getActiveTabState();
        if (ts == null) return;

        ConnectionInfo tabConn = ts.connectionInfo;
        if (tabConn == null) {
            JOptionPane.showMessageDialog(this,
                    "This tab has no connection.\n"
                    + "Double-click a connection or right-click → 'Open Query Tab' to bind one.",
                    "No Connection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // DynamoDB doesn't support EXPLAIN
        if (tabConn.getDbType() == DatabaseType.DYNAMODB) {
            JOptionPane.showMessageDialog(this,
                    "Explain Plan is not supported for DynamoDB connections.",
                    "Not Supported", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Connection conn = connectionManager.getActiveConnection(tabConn.getId());
        if (conn == null) {
             // Try auto-reconnect for explain plan too
             try {
                 conn = connectionManager.connect(tabConn);
                 logPanel.logInfo("Reconnected to: " + tabConn.getName());
                 connectionListPanel.refreshList();
             } catch (SQLException e) {
                ExplainPlanPanel ep = ts.explainPlanPanel;
                ts.bottomTabs.setSelectedComponent(ep);
                ep.setStatus("\u26A0 Connection lost for " + tabConn.getName() + ". Reconnect failed.");
                logPanel.logError("Connection lost for " + tabConn.getName()
                        + ". Reconnect and try again.", null);
                return;
             }
        }

        String sql = sqlEditorPanel.getActiveSQL();
        if (sql == null || sql.isBlank()) {
            logPanel.logInfo("Nothing to explain.");
            return;
        }

        ExplainPlanPanel ep = ts.explainPlanPanel;
        ts.bottomTabs.setSelectedComponent(ep);
        ep.showLoading();
        logPanel.logInfo("[" + tabConn.getName() + "] Explain: "
                + sql.trim().replaceAll("\\s+", " "));

        long start = System.currentTimeMillis();
        queryExecutor.explainAsync(conn, sql, tabConn.getDbType(),
                (String plan) -> {
                    long elapsed = System.currentTimeMillis() - start;
                    SwingUtilities.invokeLater(() -> {
                        ep.hideLoading();
                        ep.displayPlan(plan, elapsed);
                        logPanel.logInfo("Explain plan generated in " + elapsed + " ms");
                        statusLabel.setText("Plan ready | " + elapsed + " ms | "
                                + tabConn.getName());
                    });
                },
                (SQLException ex) -> SwingUtilities.invokeLater(() -> {
                    ep.hideLoading();
                    ep.setStatus("Error: " + ex.getMessage());
                    logPanel.logError("Explain failed [" + ex.getErrorCode() + "] "
                            + ex.getSQLState() + ": " + ex.getMessage(), ex);
                    statusLabel.setText("Explain failed | " + tabConn.getName());
                })
        );
    }

    private void updateStatus() {
        ConnectionInfo tabConn = sqlEditorPanel.getActiveTabConnection();
        if (tabConn != null && connectionManager.isConnected(tabConn.getId())) {
            String detail = tabConn.getDbType() == DatabaseType.DYNAMODB
                    ? tabConn.getAwsRegion() : tabConn.getJdbcUrl();
            statusLabel.setText("Connected: " + tabConn.getName()
                    + " | " + detail);
        } else {
            statusLabel.setText("No connection on this tab");
        }
    }

    /** Extract a user-friendly message from an exception, unwrapping common wrappers. */
    private static String extractErrorMessage(Exception ex) {
        Throwable cause = ex;
        // Unwrap RuntimeException / ExecutionException wrappers
        while (cause.getCause() != null && cause.getCause() != cause
                && (cause instanceof RuntimeException || cause.getClass().getName().contains("ExecutionException"))) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        if (msg == null || msg.isBlank()) msg = cause.getClass().getSimpleName();
        return msg;
    }
}

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
import javax.swing.UIManager;

import com.dbexplorer.health.DashboardConfig;
import com.dbexplorer.health.HealthCollector;
import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.model.DatabaseType;
import com.dbexplorer.model.LazyQueryResult;
import com.dbexplorer.model.QueryResult;
import com.dbexplorer.service.ConnectionManager;
import com.dbexplorer.service.DdlExportService;
import com.dbexplorer.service.DynamoDbExecutor;
import com.dbexplorer.service.QueryExecutor;
public class MainFrame extends JFrame {

    private final ConnectionManager connectionManager;
    private final QueryExecutor queryExecutor;

    private final ConnectionListPanel connectionListPanel;
    private final SqlEditorPanel sqlEditorPanel;
    private final LogPanel logPanel;
    private final JLabel statusLabel;

    // Dashboard
    private final HealthCollector healthCollector;
    private final DashboardConfig dashboardConfig;
    private DashboardPanel dashboardPanel;
    private JSplitPane outerSplit;
    private JButton dashboardBtn;

    // Heap indicator in status bar
    private final JLabel heapLabel = new JLabel();
    private javax.swing.Timer heapTimer;

    private JButton newTabBtn;
    private JButton cancelBtn;
    private ThemeAnimationOverlay animationOverlay;

    public MainFrame() {
        super("DB Explorer");
        connectionManager = new ConnectionManager();
        queryExecutor = new QueryExecutor();

        connectionListPanel = new ConnectionListPanel(connectionManager);
        sqlEditorPanel = new SqlEditorPanel();
        sqlEditorPanel.setQueryExecutor(queryExecutor);
        sqlEditorPanel.setConnectionManager(connectionManager);
        logPanel = new LogPanel();
        statusLabel = new JLabel("No active connection");

        // Dashboard — load config and create components (disabled by default)
        dashboardConfig  = DashboardConfig.load();
        healthCollector  = new HealthCollector();
        dashboardPanel   = new DashboardPanel(healthCollector, dashboardConfig);

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

        // Heap indicator — right corner of status bar
        heapLabel.setFont(heapLabel.getFont().deriveFont(Font.PLAIN, 11f));
        heapLabel.setToolTipText("JVM Heap: used / committed / max");
        updateHeapLabel();
        statusBar.add(heapLabel, BorderLayout.EAST);

        add(mainSplit, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        // Outer split: main content | dashboard panel (hidden by default)
        dashboardPanel.setVisible(false);
        outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainSplit, dashboardPanel);
        outerSplit.setResizeWeight(1.0);
        outerSplit.setDividerSize(0);
        remove(mainSplit);
        add(outerSplit, BorderLayout.CENTER);
    }

    private void initToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorderPainted(true);

        JButton addBtn = makeToolButton("Add Connection", DbIcons.TB_ADD);
        addBtn.addActionListener(e -> showAddConnectionDialog());

        JButton disconnectBtn = makeToolButton("Disconnect DB", DbIcons.TB_DISCONNECT);
        disconnectBtn.setToolTipText("Disconnect selected connection (tabs keep their binding)");
        disconnectBtn.addActionListener(e -> disconnectSelectedConnection());

        JButton runBtn = makeToolButton("Run Query (Ctrl+Enter)", DbIcons.TB_RUN);
        runBtn.addActionListener(e -> runQuery());

        cancelBtn = makeToolButton("Cancel Running Query", DbIcons.TB_CANCEL);
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> cancelQuery());

        newTabBtn = makeToolButton("New Tab", DbIcons.TB_NEW_TAB);
        newTabBtn.addActionListener(e -> openNewTabWithSelectedConnection());
        newTabBtn.setEnabled(false);

        JButton clearBtn = makeToolButton("Clear Console", DbIcons.TB_CLEAR);
        clearBtn.addActionListener(e -> {
            logPanel.clear();
            SqlEditorPanel.TabState ts = sqlEditorPanel.getActiveTabState();
            if (ts != null) {
                ts.resultPanel.clear();
                ts.explainPlanPanel.clear();
            }
        });

        JButton explainBtn = makeToolButton("Explain Plan", DbIcons.TB_EXPLAIN);
        explainBtn.addActionListener(e -> runExplainPlan());

        JComboBox<String> themeCombo = new JComboBox<>(ThemeManager.getThemeNames());
        themeCombo.setSelectedItem(ThemeManager.getSavedThemeName());
        themeCombo.setMaximumSize(new Dimension(170, 28));
        themeCombo.setToolTipText("Switch Theme");
        themeCombo.addActionListener(e -> {
            String selected = (String) themeCombo.getSelectedItem();
            if (selected != null) {
                ThemeAnimationOverlay.AnimationType anim =
                        ThemeAnimationOverlay.animationFor(selected);
                ThemeManager.applyTheme(selected);
                // ThemeManager already calls updateComponentTreeUI on all windows.
                // Re-set the glass pane since updateComponentTreeUI replaces it.
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
        toolbar.add(cancelBtn);
        toolbar.add(newTabBtn);
        toolbar.add(explainBtn);
        toolbar.addSeparator();
        toolbar.add(clearBtn);
        toolbar.addSeparator();
        dashboardBtn = makeToolButton("Toggle Health Dashboard", DbIcons.TB_DASHBOARD);
        dashboardBtn.addActionListener(e -> toggleDashboard());
        toolbar.add(dashboardBtn);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(new JLabel("Theme: "));
        toolbar.add(themeCombo);

        JButton aboutBtn = makeToolButton("About", DbIcons.TB_ABOUT);
        aboutBtn.addActionListener(e -> new AboutDialog(this).setVisible(true));
        toolbar.addSeparator();
        toolbar.add(aboutBtn);

        add(toolbar, BorderLayout.NORTH);
    }

    private JButton makeToolButton(String tooltip, javax.swing.Icon icon) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        // Subtle hover effect
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setContentAreaFilled(true);
                btn.setOpaque(true);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setContentAreaFilled(false);
                btn.setOpaque(false);
            }
        });
        return btn;
    }

    private void initEventHandlers() {
        connectionListPanel.setOnConnect(this::toggleConnection);
        connectionListPanel.setOnEdit(this::showEditConnectionDialog);
        connectionListPanel.setOnDelete(this::deleteConnection);
        connectionListPanel.setOnOpenQueryTab(this::openQueryTabForConnection);
        connectionListPanel.setOnViewData(this::viewTableData);
        connectionListPanel.setOnShowDiagram(this::showSchemaDiagram);
        connectionListPanel.setOnExportDdl(this::showDdlExport);
        connectionListPanel.setOnExportTable(this::showTableExport);
        connectionListPanel.addTreeSelectionListener(e -> {
            ConnectionInfo info = connectionListPanel.getSelectedConnection();
            newTabBtn.setEnabled(info != null);
            if (info != null) dashboardPanel.onConnectionChanged(info);
        });
        sqlEditorPanel.setOnRunQuery(this::runQuery);
        sqlEditorPanel.addChangeListener(e -> updateStatus());
    }

    private void initWindowBehavior() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // Update heap label every 2 seconds
        heapTimer = new javax.swing.Timer(2000, e -> updateHeapLabel());
        heapTimer.setInitialDelay(0);
        heapTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                heapTimer.stop();
                healthCollector.stop();
                queryExecutor.shutdown();
                connectionManager.disconnectAll();
                dispose();
                System.exit(0);
            }
        });
    }

    private void updateHeapLabel() {
        java.lang.management.MemoryUsage mem =
            java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long used      = mem.getUsed()      / (1024 * 1024);
        long committed = mem.getCommitted() / (1024 * 1024);
        long max       = mem.getMax()       / (1024 * 1024);
        int pct = max > 0 ? (int)(mem.getUsed() * 100L / mem.getMax()) : 0;

        String text = String.format("Heap: %d MB / %d MB  (%d%%)", used, max, pct);
        heapLabel.setText(text);

        // Color: amber above 70%, red above 90%
        if (pct > 90) {
            heapLabel.setForeground(new Color(0xEF, 0x44, 0x44));
        } else if (pct > 70) {
            heapLabel.setForeground(new Color(0xF5, 0x9E, 0x0B));
        } else {
            heapLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
        heapLabel.setToolTipText(String.format(
            "Used: %d MB  |  Allocated: %d MB  |  Max: %d MB", used, committed, max));
    }

    // --- Dashboard toggle ---

    private void toggleDashboard() {
        ConnectionInfo info = connectionListPanel.getSelectedConnection();
        boolean nowVisible = !dashboardPanel.isVisible();

        if (nowVisible) {
            if (info == null) {
                JOptionPane.showMessageDialog(this,
                    "Please select a connection first to enable the Health Dashboard.",
                    "No Connection Selected", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            dashboardPanel.setVisible(true);
            outerSplit.setDividerSize(5);
            outerSplit.setEnabled(true);
            outerSplit.setDividerLocation(getWidth() - 310);
            dashboardConfig.setEnabledFor(info.getId(), true);
            DashboardConfig.save(dashboardConfig);
            healthCollector.start(info, dashboardConfig, dashboardPanel::updateSnapshot);
            logPanel.logInfo("Health Dashboard enabled for: " + info.getName());
        } else {
            dashboardPanel.setVisible(false);
            outerSplit.setDividerSize(0);
            healthCollector.stop();
            if (info != null) {
                dashboardConfig.setEnabledFor(info.getId(), false);
                DashboardConfig.save(dashboardConfig);
            }
            logPanel.logInfo("Health Dashboard disabled.");
        }
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
                "Delete connection '" + info.getName() + "'?\n"
                + "Existing query tabs bound to this connection will be marked disconnected.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            connectionManager.deleteConnection(info.getId());
            SqlAutoComplete.invalidateCache(info.getId());
            sqlEditorPanel.refreshTabHeaders(info.getId(), false);
            connectionListPanel.refreshList();
            logPanel.logInfo("Connection deleted: " + info.getName());
            updateStatus();
        }
    }

    private void toggleConnection(ConnectionInfo info) {
        if (connectionManager.isConnected(info.getId())) {
            connectionManager.disconnect(info.getId());
            // Tabs keep their binding — just update the status dot to red
            sqlEditorPanel.refreshTabHeaders(info.getId(), false);
            SqlAutoComplete.invalidateCache(info.getId());
            logPanel.logInfo("Disconnected: " + info.getName());
        } else {
            try {
                connectionManager.connect(info);
                String connDetail = info.getDbType() == DatabaseType.DYNAMODB
                        ? info.getAwsRegion() : info.getJdbcUrl();
                logPanel.logInfo("Connected: " + info.getName()
                        + " (" + connDetail + ")");
                // Tabs keep their binding — just update the status dot to green
                sqlEditorPanel.refreshTabHeaders(info.getId(), true);
                connectionListPanel.refreshList();
                connectionListPanel.loadSchemasForConnection(info);
                // Warm autocomplete cache on background thread
                java.sql.Connection jdbcConn = connectionManager.getActiveConnection(info.getId());
                if (jdbcConn != null) SqlAutoComplete.warmCache(info, jdbcConn);
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
                java.sql.Connection jdbcConn = connectionManager.getActiveConnection(info.getId());
                if (jdbcConn != null) SqlAutoComplete.warmCache(info, jdbcConn);
            } catch (SQLException ex) {
                logPanel.logError("Connection failed: " + info.getName(), ex);
                JOptionPane.showMessageDialog(this,
                        "Connection failed:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        sqlEditorPanel.addNewTab(info);
        sqlEditorPanel.refreshTabHeaders(info.getId(), true);
        updateStatus();
    }
    
    private void showSchemaDiagram(ConnectionInfo info, String schema) {
        java.sql.Connection conn = connectionManager.getActiveConnection(info.getId());
        if (conn == null) {
            JOptionPane.showMessageDialog(this,
                    "Connect to " + info.getName() + " first.",
                    "Not Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new SchemaDiagramDialog(this, info, conn, schema).setVisible(true);
    }

    private void showDdlExport(ConnectionInfo info, String schema) {
        java.sql.Connection conn = connectionManager.getActiveConnection(info.getId());
        if (conn == null) {
            JOptionPane.showMessageDialog(this,
                    "Connect to " + info.getName() + " first.",
                    "Not Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new DdlExportDialog(this, info, conn, schema).setVisible(true);
    }

    /**
     * Handles table-level export actions.
     * The encoded key format is: "ACTION\0schema\0table"
     * where ACTION is one of DDL, INSERT, UPDATE, CSV.
     * Opens a TableExportDialog pre-selected to the requested tab.
     */
    private void showTableExport(ConnectionInfo info, String encoded) {
        java.sql.Connection conn = connectionManager.getActiveConnection(info.getId());
        if (conn == null) {
            JOptionPane.showMessageDialog(this,
                    "Connect to " + info.getName() + " first.",
                    "Not Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // encoded = "ACTION\0schema\0table"
        String[] parts  = encoded.split("\0", 3);
        String action   = parts[0];
        String schema   = parts.length > 1 ? parts[1] : null;
        String table    = parts.length > 2 ? parts[2] : parts[1];

        if ("MAT_VIEW_DDL".equals(action)) {
            String matSchema = schema;
            String matName   = table;
            DatabaseType dbType = info.getDbType();
            String title = "DDL Export — " + info.getName()
                    + (matSchema != null ? " / " + matSchema + "." + matName : " / " + matName);
            String fileName = (matSchema != null ? matSchema + "_" : "") + matName + "_ddl";
            DdlExportService svc = new DdlExportService();
            new DdlExportDialog(this, title, fileName,
                    (java.util.concurrent.Callable<String>) () -> svc.exportMatViewDdl(conn, dbType, matSchema, matName))
                    .setVisible(true);
            return;
        }

        TableExportDialog dlg = new TableExportDialog(this, info, conn, schema, table);
        // Select the right tab based on action
        int tabIdx = switch (action) {
            case "INSERT" -> 1;
            case "UPDATE" -> 2;
            case "CSV"    -> 3;
            default       -> 0; // DDL
        };
        dlg.selectTab(tabIdx);
        dlg.setVisible(true);
    }

    private void viewTableData(ConnectionInfo info, String sql) {         if (!connectionManager.isConnected(info.getId())) {
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
                    java.sql.Connection jdbcConn = connectionManager.getActiveConnection(selectedInfo.getId());
                    if (jdbcConn != null) SqlAutoComplete.warmCache(selectedInfo, jdbcConn);
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
            sqlEditorPanel.refreshTabHeaders(selectedInfo.getId(), true);
            updateStatus();
        } else {
            // No connection selected, just open a blank tab
            sqlEditorPanel.addNewTab();
        }
    }

    private void disconnectSelectedConnection() {
        // Prefer the connection selected in the tree; fall back to the active tab's connection
        ConnectionInfo tabConn = connectionListPanel.getSelectedConnection();
        if (tabConn == null) {
            tabConn = sqlEditorPanel.getActiveTabConnection();
        }
        if (tabConn == null) {
            logPanel.logInfo("No connection selected.");
            return;
        }
        if (!connectionManager.isConnected(tabConn.getId())) {
            logPanel.logInfo(tabConn.getName() + " is already disconnected.");
            return;
        }
        connectionManager.disconnect(tabConn.getId());
        sqlEditorPanel.refreshTabHeaders(tabConn.getId(), false);
        SqlAutoComplete.invalidateCache(tabConn.getId());
        connectionListPanel.refreshList();
        logPanel.logInfo("Disconnected: " + tabConn.getName());
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
                    sqlEditorPanel.refreshTabHeaders(tabConn.getId(), true);
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
                 sqlEditorPanel.refreshTabHeaders(tabConn.getId(), true);
                 SqlAutoComplete.warmCache(tabConn, conn);
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

        cancelBtn.setEnabled(true);
        queryExecutor.executeAsync(conn, sql,
                (LazyQueryResult lazyResult) -> SwingUtilities.invokeLater(() -> {
                    cancelBtn.setEnabled(false);
                    rp.hideLoading();
                    rp.displayLazyResult(lazyResult);
                    logPanel.logQuery(sql, lazyResult.getExecutionTimeMs());
                    statusLabel.setText("Query executed in "
                            + lazyResult.getExecutionTimeMs() + " ms | fetch size "
                            + LazyQueryResult.DEFAULT_FETCH_SIZE
                            + " | " + tabConn.getName());
                }),
                (QueryResult result) -> SwingUtilities.invokeLater(() -> {
                    cancelBtn.setEnabled(false);
                    rp.hideLoading();
                    rp.displayResult(result);
                    logPanel.logQuery(sql, result.executionTimeMs());
                    statusLabel.setText(result.affectedRows() + " rows affected | "
                            + result.executionTimeMs() + " ms | " + tabConn.getName());
                }),
                (SQLException ex) -> SwingUtilities.invokeLater(() -> {
                    cancelBtn.setEnabled(false);
                    rp.hideLoading();
                    if (isTimeoutException(ex) && !isCancelException(ex)) {
                        rp.displayError("Query timed out",
                                "The query exceeded the " + com.dbexplorer.service.QueryExecutor.QUERY_TIMEOUT_SECONDS
                                + "s timeout and was cancelled automatically.");
                        logPanel.logInfo("Query timed out after "
                                + com.dbexplorer.service.QueryExecutor.QUERY_TIMEOUT_SECONDS + "s.");
                        statusLabel.setText("Query timed out | " + tabConn.getName());
                    } else if (isCancelException(ex)) {
                        rp.displayError("Query cancelled", "The query was cancelled by the user.");
                        logPanel.logInfo("Query cancelled by user.");
                        statusLabel.setText("Query cancelled | " + tabConn.getName());
                    } else {
                        String errTitle = "SQL Error [" + ex.getErrorCode() + "]";
                        rp.displayError(errTitle, ex.getMessage());
                        logPanel.logError(errTitle + " "
                                + ex.getSQLState() + ": " + ex.getMessage(), ex);
                        statusLabel.setText("Query failed | " + tabConn.getName());
                    }
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
                 sqlEditorPanel.refreshTabHeaders(tabConn.getId(), true);
                 SqlAutoComplete.warmCache(tabConn, conn);
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
                        ep.displayPlan(plan, elapsed, tabConn.getDbType());
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

    private void cancelQuery() {
        queryExecutor.cancelCurrent();
        cancelBtn.setEnabled(false);
        logPanel.logInfo("Cancel requested.");
    }

    private static boolean isCancelException(SQLException ex) {
        String state = ex.getSQLState();
        String msg   = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        return "57014".equals(state) || msg.contains("cancel") || msg.contains("interrupt");
    }

    private static boolean isTimeoutException(SQLException ex) {
        return ex instanceof java.sql.SQLTimeoutException
                || "57014".equals(ex.getSQLState())
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout"));
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

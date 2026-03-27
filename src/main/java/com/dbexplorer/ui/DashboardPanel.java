package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.dbexplorer.health.ActiveQuery;
import com.dbexplorer.health.ConnectionStatus;
import com.dbexplorer.health.DashboardConfig;
import com.dbexplorer.health.DbMetadata;
import com.dbexplorer.health.HealthCollector;
import com.dbexplorer.health.JvmStats;
import com.dbexplorer.health.LiveConnection;
import com.dbexplorer.health.ServerStats;
import com.dbexplorer.health.StatsModel;
import com.dbexplorer.model.ConnectionInfo;

/**
 * Right-side collapsible panel that renders a StatsModel snapshot.
 * Must only be updated on the EDT (via HealthCollector's invokeLater calls).
 */
public class DashboardPanel extends JPanel {

    private static final Color COLOR_VALID   = new Color(0x22, 0xC5, 0x5E);
    private static final Color COLOR_INVALID = new Color(0xEF, 0x44, 0x44);
    private static final Color COLOR_AMBER   = new Color(0xF5, 0x9E, 0x0B);
    private static final int   PANEL_WIDTH   = 300;

    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final NumberFormat NUM_FMT =
        NumberFormat.getNumberInstance(Locale.getDefault());

    // ── Connection Health ──
    private final JLabel lblConnStatus   = new JLabel("●");
    private final JLabel lblLastCheck    = new JLabel("—");
    private final JLabel lblReconnects   = new JLabel("0");

    // ── DB Metadata ──
    private final JLabel lblProduct      = new JLabel("—");
    private final JLabel lblDriver       = new JLabel("—");
    private final JLabel lblMaxConn      = new JLabel("—");
    private final JLabel lblFeatures     = new JLabel("—");

    // ── Server Activity ──
    private final JLabel lblActiveSess   = new JLabel("—");
    private final JLabel lblTotalConn    = new JLabel("—");
    private final JLabel lblCommits      = new JLabel("—");
    private final JLabel lblRollbacks    = new JLabel("—");
    private final JLabel lblCacheHit     = new JLabel("—");
    private final JLabel lblLockWaits    = new JLabel("—");
    private final JLabel lblDeadlocks    = new JLabel("—");
    private final JLabel lblSeqScans     = new JLabel("—");
    private final JLabel lblIdxScans     = new JLabel("—");
    private final JLabel lblSlowQueries  = new JLabel("—");
    private final JLabel lblDbSize       = new JLabel("—");
    private final JLabel lblServerNote   = new JLabel();
    private final DefaultTableModel activeQueriesModel =
        new DefaultTableModel(new String[]{"PID","State","Duration (ms)","Query"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
    private final JTable activeQueriesTable = new JTable(activeQueriesModel);

    // ── Live Connections ──
    private final JLabel lblConnCount    = new JLabel("0 active connection(s)");
    private final JLabel lblConnNote     = new JLabel();
    private final DefaultTableModel liveConnsModel =
        new DefaultTableModel(new String[]{"Conn ID","Username","Host","State","Query","Duration (ms)"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
    private final JTable liveConnsTable  = new JTable(liveConnsModel);
    private List<LiveConnection> lastLiveConns = List.of();

    // ── SQL Warnings ──
    private final DefaultTableModel warningsModel =
        new DefaultTableModel(new String[]{"Time","SQLState","Message"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
    private final JTable warningsTable   = new JTable(warningsModel);

    // ── JVM Resources ──
    private final JProgressBar heapBar   = new JProgressBar(0, 100);
    private final JLabel lblHeap         = new JLabel("—");
    private final JLabel lblThreads      = new JLabel("—");
    private final JLabel lblGc           = new JLabel("—");

    // ── Footer ──
    private final JLabel lblLastRefreshed = new JLabel("Not yet refreshed");

    public DashboardPanel(HealthCollector collector, DashboardConfig config) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildSection("Connection Health", buildConnectionHealthPanel()));
        content.add(buildSection("Database Metadata", buildMetadataPanel()));
        content.add(buildSection("Server Activity (all clients)", buildServerActivityPanel()));
        content.add(buildSection("Live Connections", buildLiveConnectionsPanel()));
        content.add(buildSection("SQL Warnings", buildWarningsPanel()));
        content.add(buildSection("JVM Resources", buildJvmPanel()));

        JScrollPane scroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        footer.add(new JLabel("Last refreshed:"));
        footer.add(lblLastRefreshed);
        add(footer, BorderLayout.SOUTH);

        setupLiveConnsRenderer();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Must be called on the EDT. */
    public void updateSnapshot(StatsModel snap) {
        updateConnectionHealth(snap);
        updateMetadata(snap);
        updateServerActivity(snap);
        updateLiveConnections(snap);
        updateWarnings(snap);
        updateJvm(snap);
        if (snap.lastRefreshed != null)
            lblLastRefreshed.setText(DT_FMT.format(snap.lastRefreshed));
    }

    public void onConnectionChanged(ConnectionInfo info) {
        // Reset all labels to "—" when connection switches
        lblConnStatus.setText("●");
        lblConnStatus.setForeground(COLOR_INVALID);
        lblLastCheck.setText("—");
        lblReconnects.setText("0");
        lblProduct.setText("—"); lblDriver.setText("—");
        lblMaxConn.setText("—"); lblFeatures.setText("—");
        clearTable(activeQueriesModel);
        clearTable(liveConnsModel);
        clearTable(warningsModel);
        lblLastRefreshed.setText("Not yet refreshed");
    }

    public void applyTheme() {
        SwingUtilities.updateComponentTreeUI(this);
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    private JPanel buildSection(String title, JComponent body) {
        JPanel section = new JPanel(new BorderLayout());
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JButton header = new JButton("▼ " + title);
        header.setHorizontalAlignment(SwingConstants.LEFT);
        header.setBorderPainted(false);
        header.setContentAreaFilled(false);
        header.setFocusPainted(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        header.addActionListener(e -> {
            boolean visible = body.isVisible();
            body.setVisible(!visible);
            header.setText((visible ? "▶ " : "▼ ") + title);
        });

        section.add(header, BorderLayout.NORTH);
        section.add(body, BorderLayout.CENTER);
        section.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
            UIManager.getColor("Separator.foreground")));
        return section;
    }

    private JPanel buildConnectionHealthPanel() {
        JPanel p = new JPanel(new GridLayout(0, 2, 4, 2));
        p.setBorder(new EmptyBorder(4, 8, 4, 4));
        lblConnStatus.setForeground(COLOR_INVALID);
        lblConnStatus.setFont(lblConnStatus.getFont().deriveFont(16f));
        p.add(new JLabel("Status:")); p.add(lblConnStatus);
        p.add(new JLabel("Last check:")); p.add(lblLastCheck);
        p.add(new JLabel("Reconnects:")); p.add(lblReconnects);
        return p;
    }

    private JPanel buildMetadataPanel() {
        JPanel p = new JPanel(new GridLayout(0, 2, 4, 2));
        p.setBorder(new EmptyBorder(4, 8, 4, 4));
        p.add(new JLabel("Product:")); p.add(lblProduct);
        p.add(new JLabel("Driver:"));  p.add(lblDriver);
        p.add(new JLabel("Max conn:")); p.add(lblMaxConn);
        p.add(new JLabel("Features:")); p.add(lblFeatures);
        return p;
    }

    private JPanel buildServerActivityPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(4, 8, 4, 4));

        JPanel top = new JPanel(new BorderLayout());

        JPanel grid = new JPanel(new GridLayout(0, 2, 4, 2));
        grid.add(new JLabel("Active sessions:")); grid.add(lblActiveSess);
        grid.add(new JLabel("Total connections:")); grid.add(lblTotalConn);
        grid.add(new JLabel("Commits:")); grid.add(lblCommits);
        grid.add(new JLabel("Rollbacks:")); grid.add(lblRollbacks);
        grid.add(new JLabel("Cache hit ratio:")); grid.add(lblCacheHit);
        grid.add(new JLabel("Lock waits:")); grid.add(lblLockWaits);
        grid.add(new JLabel("Deadlocks:")); grid.add(lblDeadlocks);
        grid.add(new JLabel("Seq scans:")); grid.add(lblSeqScans);
        grid.add(new JLabel("Index scans:")); grid.add(lblIdxScans);
        grid.add(new JLabel("Slow queries:")); grid.add(lblSlowQueries);
        grid.add(new JLabel("DB size:")); grid.add(lblDbSize);

        lblServerNote.setFont(lblServerNote.getFont().deriveFont(Font.ITALIC, 10f));

        JPanel noteWrapper = new JPanel(new BorderLayout());
        noteWrapper.add(lblServerNote, BorderLayout.CENTER);

        top.add(grid, BorderLayout.NORTH);
        top.add(noteWrapper, BorderLayout.CENTER);

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.add(new JLabel("Running queries:"), BorderLayout.NORTH);
        activeQueriesTable.setFillsViewportHeight(true);
        activeQueriesTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane aqScroll = new JScrollPane(activeQueriesTable);
        aqScroll.setPreferredSize(new Dimension(0, 80));
        tableWrapper.add(aqScroll, BorderLayout.CENTER);

        p.add(top, BorderLayout.NORTH);
        p.add(tableWrapper, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildLiveConnectionsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(4, 8, 4, 4));

        JPanel top = new JPanel(new BorderLayout());
        top.add(lblConnCount, BorderLayout.NORTH);

        liveConnsTable.setFillsViewportHeight(true);
        liveConnsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane scroll = new JScrollPane(liveConnsTable);
        scroll.setPreferredSize(new Dimension(0, 100));

        lblConnNote.setFont(lblConnNote.getFont().deriveFont(Font.ITALIC, 10f));

        p.add(top, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(lblConnNote, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildWarningsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(4, 8, 4, 4));
        warningsTable.setFillsViewportHeight(true);
        warningsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane scroll = new JScrollPane(warningsTable);
        scroll.setPreferredSize(new Dimension(0, 80));
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildJvmPanel() {
        JPanel p = new JPanel(new GridLayout(0, 2, 4, 2));
        p.setBorder(new EmptyBorder(4, 8, 4, 4));
        heapBar.setStringPainted(true);
        p.add(new JLabel("Heap:")); p.add(heapBar);
        p.add(new JLabel("Heap detail:")); p.add(lblHeap);
        p.add(new JLabel("Threads:")); p.add(lblThreads);
        p.add(new JLabel("GC:")); p.add(lblGc);
        return p;
    }

    // -------------------------------------------------------------------------
    // Update helpers
    // -------------------------------------------------------------------------

    private void updateConnectionHealth(StatsModel snap) {
        boolean valid = snap.connectionStatus == ConnectionStatus.VALID;
        lblConnStatus.setForeground(valid ? COLOR_VALID : COLOR_INVALID);
        lblConnStatus.setToolTipText(snap.connectionStatus.name());
        lblLastCheck.setText(snap.lastValidCheck != null ? snap.lastValidCheck : "—");
        lblReconnects.setText(String.valueOf(snap.reconnectAttempts));
    }

    private void updateMetadata(StatsModel snap) {
        if (snap.dbMetadata == null) return;
        DbMetadata m = snap.dbMetadata;
        lblProduct.setText(m.productName() + " " + m.productVersion());
        lblDriver.setText(m.driverName() + " " + m.driverVersion());
        lblMaxConn.setText(m.maxConnections() > 0 ? String.valueOf(m.maxConnections()) : "unlimited");
        lblFeatures.setText(
            (m.supportsTransactions() ? "TX " : "") +
            (m.supportsSavepoints()   ? "SP " : "") +
            (m.supportsBatchUpdates() ? "BA " : "") +
            (m.supportsStoredProcedures() ? "SP" : ""));
    }

    private void updateServerActivity(StatsModel snap) {
        ServerStats s = snap.serverStats;
        if (s == null || s.isEmpty()) {
            lblServerNote.setText("<html><i>Server Activity statistics are not available for this " +
                "database type.<br>Connection health, metadata, warnings, and JVM stats are still collected.</i></html>");
            lblActiveSess.setText("—"); lblTotalConn.setText("—");
            lblCommits.setText("—"); lblRollbacks.setText("—");
            lblCacheHit.setText("—"); lblLockWaits.setText("—");
            lblDeadlocks.setText("—"); lblSeqScans.setText("—");
            lblIdxScans.setText("—"); lblSlowQueries.setText("—");
            lblDbSize.setText("—");
            clearTable(activeQueriesModel);
            return;
        }
        lblServerNote.setText("");
        lblActiveSess.setText(s.activeSessionCount()  != null ? NUM_FMT.format(s.activeSessionCount())  : "—");
        lblTotalConn.setText(s.totalConnectionCount() != null ? NUM_FMT.format(s.totalConnectionCount()) : "—");
        lblCommits.setText(s.totalCommits()           != null ? NUM_FMT.format(s.totalCommits())         : "—");
        lblRollbacks.setText(s.totalRollbacks()       != null ? NUM_FMT.format(s.totalRollbacks())       : "—");
        lblCacheHit.setText(s.cacheHitRatio()         != null ? String.format("%.1f%%", s.cacheHitRatio() * 100) : "—");
        lblLockWaits.setText(s.lockWaitCount()        != null ? NUM_FMT.format(s.lockWaitCount())        : "—");
        lblDeadlocks.setText(s.deadlockCount()        != null ? NUM_FMT.format(s.deadlockCount())        : "—");
        lblSeqScans.setText(s.seqScanCount()          != null ? NUM_FMT.format(s.seqScanCount())         : "—");
        lblIdxScans.setText(s.idxScanCount()          != null ? NUM_FMT.format(s.idxScanCount())         : "—");
        lblSlowQueries.setText(s.slowQueryCount()     != null ? NUM_FMT.format(s.slowQueryCount())       : "—");
        lblDbSize.setText(s.databaseSizeBytes()       != null ? formatBytes(s.databaseSizeBytes())       : "—");

        clearTable(activeQueriesModel);
        for (ActiveQuery q : s.activeQueries()) {
            activeQueriesModel.addRow(new Object[]{
                q.pid(), q.state(), NUM_FMT.format(q.durationMs()), q.queryText()});
        }
    }

    private void updateLiveConnections(StatsModel snap) {
        List<LiveConnection> conns = snap.serverStats != null
            ? snap.serverStats.liveConnections() : List.of();
        lastLiveConns = conns;
        lblConnCount.setText(NUM_FMT.format(conns.size()) + " active connection(s)");

        String note = conns.stream()
            .filter(c -> c.note() != null).map(LiveConnection::note).findFirst().orElse(null);
        lblConnNote.setText(note != null ? "<html><i>" + note + "</i></html>" : "");

        clearTable(liveConnsModel);
        for (LiveConnection c : conns) {
            liveConnsModel.addRow(new Object[]{
                c.connectionId(), c.username(), c.host(), c.state(),
                c.currentQuery() != null ? c.currentQuery() : "—",
                c.durationMs()   != null ? NUM_FMT.format(c.durationMs()) : "—"
            });
        }
    }

    private void updateWarnings(StatsModel snap) {
        clearTable(warningsModel);
        if (snap.warningLog == null) return;
        snap.warningLog.stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(10)
            .forEach(w -> warningsModel.addRow(new Object[]{
                DT_FMT.format(w.timestamp()), w.sqlState(), w.message()}));
    }

    private void updateJvm(StatsModel snap) {
        if (snap.jvmStats == null) return;
        JvmStats j = snap.jvmStats;
        int pct = j.heapMaxBytes() > 0
            ? (int) (j.heapUsedBytes() * 100L / j.heapMaxBytes()) : 0;
        heapBar.setValue(pct);
        heapBar.setString(pct + "%");
        heapBar.setForeground(pct > 90 ? COLOR_INVALID : pct > 70 ? COLOR_AMBER : null);
        lblHeap.setText(formatBytes(j.heapUsedBytes()) + " / " + formatBytes(j.heapMaxBytes()));
        lblThreads.setText(String.valueOf(j.liveThreadCount()));
        lblGc.setText(NUM_FMT.format(j.gcCollectionCount()) + " runs / " +
            NUM_FMT.format(j.gcCollectionTimeMs()) + " ms");
    }

    // -------------------------------------------------------------------------
    // Live connections renderer — highlights Health_Connection row
    // -------------------------------------------------------------------------

    private void setupLiveConnsRenderer() {
        liveConnsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
                if (row < lastLiveConns.size() && lastLiveConns.get(row).isHealthConn()) {
                    c.setFont(c.getFont().deriveFont(Font.BOLD));
                    if (!isSelected) {
                        Color accent = UIManager.getColor("Component.accentColor");
                        if (accent != null) {
                            c.setBackground(accent.brighter());
                        }
                    }
                } else if (!isSelected) {
                    c.setBackground(table.getBackground());
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                }
                return c;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static void clearTable(DefaultTableModel model) {
        model.setRowCount(0);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format("%.1f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L)     return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024L)         return String.format("%.1f KB", bytes / 1_024.0);
        return bytes + " B";
    }
}

package com.dbexplorer.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.service.ConnectionManager;
import com.dbexplorer.service.QueryExecutor;

/**
 * Multi-tab SQL editor panel. Each tab is a vertical split: editor on top,
 * its own Results + Explain Plan tabs on the bottom.
 * Each tab tracks its own ConnectionInfo.
 */
public class SqlEditorPanel extends JPanel {

    private final JTabbedPane tabbedPane;
    private int tabCounter = 0;
    private Runnable onRunQuery;
    private QueryExecutor queryExecutor;
    private ConnectionManager connectionManager;

    /** Per-tab state keyed by the tab's root component (the JSplitPane). */
    private final Map<Component, TabState> tabStates = new HashMap<>();

    /** Holds per-tab objects. */
    public static class TabState {
        public final JTextPane editor;
        public final ResultPanel resultPanel;
        public final ExplainPlanPanel explainPlanPanel;
        public final JTabbedPane bottomTabs;
        // connectionInfo is permanently bound at tab creation — never cleared
        public final ConnectionInfo connectionInfo;
        public final SqlAutoComplete autoComplete;

        TabState(JTextPane editor, ResultPanel resultPanel,
                 ExplainPlanPanel explainPlanPanel, JTabbedPane bottomTabs,
                 ConnectionInfo connectionInfo, SqlAutoComplete autoComplete) {
            this.editor = editor;
            this.resultPanel = resultPanel;
            this.explainPlanPanel = explainPlanPanel;
            this.bottomTabs = bottomTabs;
            this.connectionInfo = connectionInfo;
            this.autoComplete = autoComplete;
        }
    }

    private final JPanel welcomePanel;

    public SqlEditorPanel() {
        super(new BorderLayout());
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        welcomePanel = createWelcomePanel();

        add(welcomePanel, BorderLayout.CENTER);

        // Show welcome when all tabs are closed
        tabbedPane.addChangeListener(e -> checkEmpty());

        // When theme changes, force the tabbedPane and all its tab header components
        // to pick up the new UIManager colors — prevents stale tab backgrounds
        ThemeManager.addThemeChangeListener(() -> SwingUtilities.invokeLater(() -> {
            SwingUtilities.updateComponentTreeUI(tabbedPane);
            tabbedPane.revalidate();
            tabbedPane.repaint();
        }));
    }

    public void setOnRunQuery(Runnable onRunQuery) { this.onRunQuery = onRunQuery; }

    public void setConnectionManager(ConnectionManager cm) { this.connectionManager = cm; }

    public void setQueryExecutor(QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
        // Retroactively set on any tabs already created (e.g. the default first tab)
        for (TabState ts : tabStates.values()) {
            ts.resultPanel.setQueryExecutor(queryExecutor);
        }
    }

    public void addNewTab() { addNewTab(null); }

    public void addNewTab(ConnectionInfo connectionInfo) {
        addNewTab(connectionInfo, null);
    }
    
    public void addNewTab(ConnectionInfo connectionInfo, String initialSql) {
        // Switch from welcome panel to tabbed pane if needed
        if (tabbedPane.getParent() == null) {
            remove(welcomePanel);
            add(tabbedPane, BorderLayout.CENTER);
            revalidate();
            repaint();
        }

        tabCounter++;
        String baseTitle = "Query " + tabCounter;
        String displayTitle = connectionInfo != null
                ? baseTitle + " — " + connectionInfo.getName() : baseTitle;

        JTextPane editor = createEditor();
        if (initialSql != null) {
            editor.setText(initialSql);
        }

        JPanel noWrapPanel = new JPanel(new BorderLayout());
        noWrapPanel.add(editor);
        JScrollPane editorScroll = new JScrollPane(noWrapPanel);

        ResultPanel resultPanel = new ResultPanel();
        if (queryExecutor != null) resultPanel.setQueryExecutor(queryExecutor);
        ExplainPlanPanel explainPlanPanel = new ExplainPlanPanel();

        JTabbedPane bottomTabs = new JTabbedPane();
        bottomTabs.addTab("Results", resultPanel);
        bottomTabs.addTab("Explain Plan", explainPlanPanel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, bottomTabs);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.5);

        tabbedPane.addTab(displayTitle, splitPane);
        int idx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(idx, createTabHeader(baseTitle,
                connectionInfo != null ? connectionInfo.getName() : null, splitPane));
        tabbedPane.setSelectedIndex(idx);

        TabState state = new TabState(editor, resultPanel, explainPlanPanel, bottomTabs,
                connectionInfo, buildAutoComplete(editor, connectionInfo));
        tabStates.put(splitPane, state);

        editor.requestFocusInWindow();
    }

    /** Get the state for the currently active tab. */
    public TabState getActiveTabState() {
        Component comp = tabbedPane.getSelectedComponent();
        return comp != null ? tabStates.get(comp) : null;
    }

    public ConnectionInfo getActiveTabConnection() {
        TabState s = getActiveTabState();
        return s != null ? s.connectionInfo : null;
    }

    /**
     * No longer changes the tab's connection — tabs are permanently bound.
     * Kept for API compatibility but does nothing.
     */
    public void setActiveTabConnection(ConnectionInfo connectionInfo) {
        // intentionally empty — connection binding is immutable after tab creation
    }

    /**
     * Refreshes the status dot on all tab headers for the given connectionId.
     * Call this after connect/disconnect so the dot updates without changing the binding.
     */
    public void refreshTabHeaders(String connectionId, boolean connected) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            TabState s = tabStates.get(comp);
            if (s != null && s.connectionInfo != null
                    && s.connectionInfo.getId().equals(connectionId)) {
                updateTabHeaderStatus(i, s.connectionInfo.getName(), connected);
            }
        }
    }

    /**
     * No longer clears the connection binding — tabs are permanently bound.
     * Only refreshes the header dot to show disconnected state.
     */
    public void clearConnectionFromTabs(String connectionId) {
        refreshTabHeaders(connectionId, false);
    }

    private void updateTabHeaderStatus(int idx, String connectionName, boolean connected) {
        Component header = tabbedPane.getTabComponentAt(idx);
        if (!(header instanceof JPanel panel)) return;
        for (Component c : panel.getComponents()) {
            if (c instanceof JLabel lbl && lbl.getClientProperty("statusDot") != null) {
                lbl.setIcon(connected ? DOT_GREEN : DOT_RED);
                lbl.setToolTipText(connected ? "Connected" : "Disconnected");
                lbl.repaint();
            }
        }
    }

    /** A small filled circle icon used as a connection status indicator. */
    private static Icon makeStatusDot(Color color) {
        return new Icon() {
            @Override public int getIconWidth()  { return 10; }
            @Override public int getIconHeight() { return 10; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x + 1, y + 1, 8, 8);
                g2.setColor(color.darker());
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawOval(x + 1, y + 1, 8, 8);
                g2.dispose();
            }
        };
    }

    private static final Icon DOT_GREEN = makeStatusDot(new Color(34, 197, 94));
    private static final Icon DOT_RED   = makeStatusDot(new Color(239, 68, 68));
    private static final Icon DOT_GREY  = makeStatusDot(new Color(160, 160, 160));

    /** Build a SqlAutoComplete for a new tab, or null if no ConnectionManager is set. */
    private SqlAutoComplete buildAutoComplete(JTextPane editor, ConnectionInfo info) {
        if (connectionManager == null) return null;
        ConnectionManager cm = connectionManager;
        return new SqlAutoComplete(
                editor,
                () -> info,
                () -> info != null ? cm.getActiveConnection(info.getId()) : null
        );
    }

    private JTextPane createEditor() {
        JTextPane editor = new JTextPane();
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        new SqlSyntaxHighlighter(editor);

        KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);
        editor.getInputMap().put(ctrlEnter, "runQuery");
        editor.getActionMap().put("runQuery", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (onRunQuery != null) onRunQuery.run();
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "insertTab");
        editor.getActionMap().put("insertTab", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    editor.getDocument().insertString(editor.getCaretPosition(), "    ", null);
                } catch (Exception ignored) {}
            }
        });

        UndoManager undoManager = new UndoManager();
        undoManager.setLimit(500);
        editor.getDocument().addUndoableEditListener((UndoableEditEvent evt) -> {
            if (evt.getEdit() instanceof AbstractDocument.DefaultDocumentEvent docEvent) {
                if (docEvent.getType() == AbstractDocument.DefaultDocumentEvent.EventType.CHANGE) {
                    return;
                }
            }
            undoManager.addEdit(evt.getEdit());
        });

        KeyStroke ctrlZ = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
        editor.getInputMap().put(ctrlZ, "undo");
        editor.getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (undoManager.canUndo()) undoManager.undo();
            }
        });

        KeyStroke ctrlY = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
        editor.getInputMap().put(ctrlY, "redo");
        editor.getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (undoManager.canRedo()) undoManager.redo();
            }
        });

        KeyStroke ctrlShiftZ = KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        editor.getInputMap().put(ctrlShiftZ, "redo");

        return editor;
    }

    private JPanel createTabHeader(String baseTitle, String connectionName,
                                    JSplitPane tabComponent) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        header.setOpaque(false);

        // Status dot icon label — green if connected, grey if no connection
        JLabel dotLabel = new JLabel(connectionName != null ? DOT_GREEN : DOT_GREY);
        dotLabel.putClientProperty("statusDot", Boolean.TRUE);
        dotLabel.setToolTipText(connectionName != null ? "Connected" : "No connection");
        header.add(dotLabel);

        // Tab title label
        String fullTitle = connectionName != null ? baseTitle + " — " + connectionName : baseTitle;
        JLabel titleLabel = new JLabel(fullTitle);
        titleLabel.putClientProperty("tabTitle", Boolean.TRUE);
        titleLabel.putClientProperty("baseTitle", baseTitle);
        header.add(titleLabel);

        JButton closeBtn = new JButton("\u00d7");
        closeBtn.setFont(closeBtn.getFont().deriveFont(Font.BOLD, 14f));
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusable(false);
        closeBtn.setMargin(new Insets(0, 2, 0, 2));
        closeBtn.addActionListener(e -> {
            int i = tabbedPane.indexOfTabComponent(header);
            if (i != -1) {
                Component comp = tabbedPane.getComponentAt(i);
                tabStates.remove(comp);
                tabbedPane.removeTabAt(i);
                checkEmpty();
            }
        });
        header.add(closeBtn);
        return header;
    }

    public String getActiveSQL() {
        TabState s = getActiveTabState();
        if (s == null) return "";
        String selected = s.editor.getSelectedText();
        return (selected != null && !selected.isBlank()) ? selected : s.editor.getText();
    }

    public JTextComponent getActiveEditor() {
        TabState s = getActiveTabState();
        return s != null ? s.editor : null;
    }

    public void addChangeListener(ChangeListener l) {
        tabbedPane.addChangeListener(l);
    }

    /** Switch back to welcome panel when no tabs remain. */
    private void checkEmpty() {
        if (tabbedPane.getTabCount() == 0 && welcomePanel.getParent() == null) {
            remove(tabbedPane);
            add(welcomePanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }

    private JPanel createWelcomePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(4, 0, 4, 0);

        // Icon
        java.awt.Image icon = AboutDialog.loadWindowIcon();
        if (icon != null) {
            JLabel iconLabel = new JLabel(new ImageIcon(
                    icon.getScaledInstance(128, 128, java.awt.Image.SCALE_SMOOTH)));
            iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            gbc.gridy = 0;
            gbc.insets = new Insets(0, 0, 16, 0);
            panel.add(iconLabel, gbc);
        }

        // Title
        JLabel title = new JLabel("Welcome to DB Explorer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 20, 0);
        panel.add(title, gbc);

        // Instructions
        String[] steps = {
            "\u2460  Click  \u2795  Add Connection  to create a database connection",
            "\u2461  Double-click a connection in the left panel to connect",
            "\u2462  Right-click a connection  \u2192  Open Query Tab  to start querying",
            "\u2463  Or click  \ud83d\udcc4  New Tab  in the toolbar to open a blank editor",
            "\u2464  Use  Ctrl+Enter  to execute your SQL query"
        };

        gbc.insets = new Insets(3, 0, 3, 0);
        for (int i = 0; i < steps.length; i++) {
            JLabel step = new JLabel(steps[i]);
            step.setFont(step.getFont().deriveFont(Font.PLAIN, 13f));
            step.setForeground(UIManager.getColor("Label.disabledForeground") != null
                    ? UIManager.getColor("Label.disabledForeground") : Color.GRAY);
            gbc.gridy = 2 + i;
            panel.add(step, gbc);
        }

        // Keyboard shortcuts
        JLabel shortcutsTitle = new JLabel("Keyboard Shortcuts");
        shortcutsTitle.setFont(shortcutsTitle.getFont().deriveFont(Font.BOLD, 12f));
        gbc.gridy = 2 + steps.length;
        gbc.insets = new Insets(24, 0, 6, 0);
        panel.add(shortcutsTitle, gbc);

        String[] shortcuts = {
            "Ctrl+Enter  \u2014  Run Query",
            "Ctrl+Z  \u2014  Undo    |    Ctrl+Y  \u2014  Redo"
        };
        for (int i = 0; i < shortcuts.length; i++) {
            JLabel sc = new JLabel(shortcuts[i]);
            sc.setFont(sc.getFont().deriveFont(Font.PLAIN, 11f));
            sc.setForeground(Color.GRAY);
            gbc.gridy = 3 + steps.length + i;
            gbc.insets = new Insets(2, 0, 2, 0);
            panel.add(sc, gbc);
        }

        // AI tip
        int aiRow = 3 + steps.length + shortcuts.length;
        JLabel aiTitle = new JLabel("\u2728  AI Assistant");
        aiTitle.setFont(aiTitle.getFont().deriveFont(Font.BOLD, 12f));
        gbc.gridy = aiRow;
        gbc.insets = new Insets(24, 0, 6, 0);
        panel.add(aiTitle, gbc);

        JLabel aiTip = new JLabel("<html><center>Configure your AI model via <b>Settings \u2192 AI Configuration...</b><br>"
                + "to enable natural-language SQL generation.</center></html>");
        aiTip.setFont(aiTip.getFont().deriveFont(Font.PLAIN, 11f));
        aiTip.setForeground(Color.GRAY);
        gbc.gridy = aiRow + 1;
        gbc.insets = new Insets(2, 0, 2, 0);
        panel.add(aiTip, gbc);

        return panel;
    }
}

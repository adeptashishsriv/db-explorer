package com.dbexplorer.ui;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.service.QueryExecutor;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

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

    /** Per-tab state keyed by the tab's root component (the JSplitPane). */
    private final Map<Component, TabState> tabStates = new HashMap<>();

    /** Holds per-tab objects. */
    public static class TabState {
        public final JTextPane editor;
        public final ResultPanel resultPanel;
        public final ExplainPlanPanel explainPlanPanel;
        public final JTabbedPane bottomTabs;
        public ConnectionInfo connectionInfo;

        TabState(JTextPane editor, ResultPanel resultPanel,
                 ExplainPlanPanel explainPlanPanel, JTabbedPane bottomTabs) {
            this.editor = editor;
            this.resultPanel = resultPanel;
            this.explainPlanPanel = explainPlanPanel;
            this.bottomTabs = bottomTabs;
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
    }

    public void setOnRunQuery(Runnable onRunQuery) { this.onRunQuery = onRunQuery; }

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
        tabbedPane.setTabComponentAt(idx, createTabHeader(displayTitle, splitPane));
        tabbedPane.setSelectedIndex(idx);

        TabState state = new TabState(editor, resultPanel, explainPlanPanel, bottomTabs);
        state.connectionInfo = connectionInfo;
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

    public void setActiveTabConnection(ConnectionInfo connectionInfo) {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0) return;
        Component comp = tabbedPane.getComponentAt(idx);
        TabState s = tabStates.get(comp);
        if (s != null) {
            s.connectionInfo = connectionInfo;
        }
        updateTabHeader(idx, connectionInfo != null ? connectionInfo.getName() : null);
    }

    public void clearConnectionFromTabs(String connectionId) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            TabState s = tabStates.get(comp);
            if (s != null && s.connectionInfo != null
                    && s.connectionInfo.getId().equals(connectionId)) {
                s.connectionInfo = null;
                updateTabHeader(i, null);
            }
        }
    }

    private void updateTabHeader(int idx, String connectionName) {
        Component header = tabbedPane.getTabComponentAt(idx);
        if (header instanceof JPanel panel && panel.getComponentCount() > 0
                && panel.getComponent(0) instanceof JLabel label) {
            String base = label.getText();
            int dashIdx = base.indexOf(" — ");
            if (dashIdx > 0) base = base.substring(0, dashIdx);
            label.setText(connectionName != null ? base + " — " + connectionName : base);
        }
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

    private JPanel createTabHeader(String title, JSplitPane tabComponent) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);
        header.add(new JLabel(title));
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

        return panel;
    }
}

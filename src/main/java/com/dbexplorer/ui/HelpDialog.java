package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;

/**
 * Crisp, tabbed help dialog covering the main features of DB Explorer.
 */
public class HelpDialog extends JDialog {

    public enum Tab { USER_GUIDE, DB_CONNECTION, AI_CONFIG, DB_HEALTH, EXEC_PLAN, THEMES }

    private final JTabbedPane tabs = new JTabbedPane();

    public HelpDialog(Frame owner) {
        this(owner, Tab.USER_GUIDE);
    }

    public HelpDialog(Frame owner, Tab tab) {
        super(owner, "Help", true);
        setPreferredSize(new Dimension(620, 480));
        initUI();
        tabs.setSelectedIndex(tab.ordinal());
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        tabs.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));

        tabs.addTab("User Guide",        makeTab(USER_GUIDE));
        tabs.addTab("DB Connection",     makeTab(DB_CONNECTION));
        tabs.addTab("AI Configuration",  makeTab(AI_CONFIG));
        tabs.addTab("DB Health",         makeTab(DB_HEALTH));
        tabs.addTab("Execution Plan",    makeTab(EXEC_PLAN));
        tabs.addTab("Themes",            makeTab(THEMES));

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeBtn);
    }

    private JScrollPane makeTab(String html) {
        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setText(html);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        pane.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        return new JScrollPane(pane);
    }

    // -------------------------------------------------------------------------
    // Help content
    // -------------------------------------------------------------------------

    private static final String USER_GUIDE = html(
        "Quick Start",
        item("Add a connection", "Click <b>+</b> in the toolbar → fill in host, port, database, credentials → Save.") +
        item("Connect",          "Double-click a connection in the left panel.") +
        item("Run a query",      "Open a query tab → type SQL → press <b>Ctrl+Enter</b> or click <b>Run</b>.") +
        item("Export results",   "Right-click the result table or use the export toolbar button.") +
        item("Switch theme",     "Use the <b>Theme</b> dropdown in the toolbar.") +
        item("AI SQL help",      "Click the <b>AI</b> button in the toolbar after configuring a model."),
        "Keyboard Shortcuts",
        row("Ctrl+Enter", "Run query") +
        row("Ctrl+Z / Ctrl+Y", "Undo / Redo") +
        row("Ctrl+N", "New query tab") +
        row("F5", "Refresh schema tree")
    );

    private static final String DB_CONNECTION = html(
        "Creating a Database Connection",
        item("Open dialog",   "Click the <b>+</b> toolbar button or right-click the connections panel.") +
        item("Fill details",  "<b>Name</b> — a friendly label.<br>" +
                              "<b>Type</b> — PostgreSQL, MySQL, SQL Server, Oracle, SQLite, DynamoDB, etc.<br>" +
                              "<b>Host / Port</b> — server address (defaults are pre-filled).<br>" +
                              "<b>Database</b> — the specific database/schema to connect to.<br>" +
                              "<b>Username / Password</b> — your credentials.") +
        item("Test first",    "Click <b>Test Connection</b> before saving to verify the settings.") +
        item("Save",          "Click <b>Save</b>. The connection appears in the left panel.") +
        item("Connect",       "Double-click the connection, or right-click → <b>Connect</b>.") +
        item("Edit / Delete", "Right-click a connection for edit and delete options.")
    );

    private static final String AI_CONFIG = html(
        "Configuring an AI Model",
        item("Open dialog",    "Go to <b>Settings → AI Configuration…</b>") +
        item("Add new",        "Click <b>Add New</b> in the left panel.") +
        item("Provider",       "Choose <b>OpenAI</b>, <b>Claude</b>, <b>DeepSeek</b>, <b>Gemini</b>, or <b>Custom</b>.<br>" +
                               "The Base URL is filled automatically — change only for custom endpoints.") +
        item("Model",          "Enter the exact model name, e.g. <code>gpt-4o</code>, <code>claude-sonnet-4-20250514</code>.") +
        item("API Key",        "Paste your API key. It is stored encrypted on disk.") +
        item("Test",           "Click <b>Test Connection</b> — a progress bar shows while testing.<br>" +
                               "A success message confirms the model is reachable.") +
        item("Save &amp; use", "Click <b>Save</b>. Enable the config with the <b>Enable AI Assistant</b> checkbox.<br>" +
                               "Then use the <b>AI</b> toolbar button to generate SQL from natural language.")
    );

    private static final String DB_HEALTH = html(
        "Database Health Dashboard",
        item("Open",          "Click the <b>Health</b> toolbar button while connected.") +
        item("Connection",    "Shows live status: host, port, database, driver version, and ping latency.") +
        item("Server Stats",  "Displays uptime, active connections, cache hit ratio, and database size.") +
        item("Active Queries","Lists currently running queries with duration and state.") +
        item("JVM Stats",     "Heap usage, GC count, and thread count for the local JVM.") +
        item("Refresh",       "Stats refresh automatically. Use the <b>Refresh</b> button for an instant update.") +
        item("Close",         "Click the Health button again or close the side panel to hide it.")
    );

    private static final String EXEC_PLAN = html(
        "Execution Plan",
        item("Run a plan",    "Write your SELECT query in the editor, then click <b>Explain Plan</b> " +
                              "(or right-click in the editor → Explain Plan).") +
        item("Output",        "The plan is displayed in the <b>Explain Plan</b> tab below the editor.") +
        item("Reading it",    "<b>Seq Scan</b> — full table scan, consider adding an index.<br>" +
                              "<b>Index Scan</b> — efficient lookup via an index.<br>" +
                              "<b>Hash Join / Nested Loop</b> — join strategies; nested loop is best for small sets.<br>" +
                              "<b>cost=X..Y</b> — estimated startup and total cost (lower is better).<br>" +
                              "<b>rows=N</b> — estimated row count.") +
        item("Tip",           "Use <code>EXPLAIN ANALYZE</code> in the SQL editor for actual runtime stats.")
    );

    private static final String THEMES = html(
        "Why Themes Matter",
        item("Reduce eye strain",   "Dark themes (Flat Dark, Darcula, Ocean Blue) cut glare during long sessions — " +
                                    "especially in low-light environments.") +
        item("Improve focus",       "A consistent, low-contrast palette keeps attention on data, not the UI chrome.") +
        item("Accessibility",       "High-contrast and light themes help users with visual sensitivities or " +
                                    "when working on bright displays.") +
        item("Personal comfort",    "The right theme reduces fatigue and makes the tool feel like yours."),
        "How to Switch",
        item("Toolbar dropdown",    "Use the <b>Theme</b> dropdown in the top-right of the toolbar — " +
                                    "changes apply instantly, no restart needed.") +
        item("Persisted",           "Your choice is saved automatically and restored on next launch."),
        "Available Themes",
        item("Flat Dark",           "Clean dark UI — great default for most environments.") +
        item("Flat Darcula",        "Warmer dark tones, inspired by JetBrains IDEs.") +
        item("Flat Light",          "Crisp white UI — ideal for bright rooms or printed screenshots.") +
        item("Flat IntelliJ",       "Light theme matching IntelliJ IDEA's default look.") +
        item("Ocean Blue",          "Deep blue dark theme — calm and easy on the eyes.") +
        item("Forest Green",        "Earthy green tones — relaxed, nature-inspired palette.") +
        item("Sunset Purple",       "Rich purple dark theme — vibrant yet comfortable.") +
        item("Cherry Red",          "Bold red accents on dark — high energy, high contrast.") +
        item("Amber Warm",          "Warm amber tones — cozy feel for evening work.") +
        item("Arctic Frost",        "Cool icy blues on light background — clean and airy.") +
        item("Rose Garden",         "Soft rose accents — elegant light theme.") +
        item("System",              "Matches your OS native look and feel.") +
        item("Metal",               "Java's cross-platform default — consistent across all OSes.")
    );

    // -------------------------------------------------------------------------
    // HTML helpers
    // -------------------------------------------------------------------------

    private static String html(String... sections) {
        StringBuilder sb = new StringBuilder(
            "<html><body style='font-family:sans-serif;font-size:12px;margin:4px'>");
        for (int i = 0; i < sections.length; i += 2) {
            sb.append("<h3 style='margin-bottom:4px;color:#2a7ae2'>").append(sections[i]).append("</h3>");
            if (i + 1 < sections.length) {
                sb.append("<table cellpadding='3' cellspacing='0' width='100%'>")
                  .append(sections[i + 1])
                  .append("</table>");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String item(String label, String detail) {
        return "<tr><td valign='top' width='130'><b>" + label + "</b></td>" +
               "<td valign='top'>" + detail + "</td></tr>";
    }

    private static String row(String keys, String desc) {
        return "<tr><td valign='top' width='160'><code>" + keys + "</code></td>" +
               "<td valign='top'>" + desc + "</td></tr>";
    }
}

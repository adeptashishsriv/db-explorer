package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import com.dbexplorer.model.DatabaseType;

/**
 * Rich execution plan viewer.
 *
 * - PostgreSQL  → interactive JTree with cost/rows/width badges per node
 * - MySQL       → colour-coded JTable (highlights full-scan rows in amber)
 * - Oracle/MSSQL/SQLite → syntax-highlighted monospaced text
 *
 * A stats bar at the top always shows extracted timing & cost numbers.
 */
public class ExplainPlanPanel extends JPanel {

    // ── Patterns for PostgreSQL plan lines ───────────────────────────────────
    private static final Pattern PG_COST  =
            Pattern.compile("cost=(\\S+)\\s+rows=(\\d+)\\s+width=(\\d+)");
    private static final Pattern PG_ACTUAL =
            Pattern.compile("actual time=(\\S+)\\s+rows=(\\d+)\\s+loops=(\\d+)");
    private static final Pattern PG_PLANNING =
            Pattern.compile("Planning [Tt]ime:\\s+([\\d.]+)\\s+ms");
    private static final Pattern PG_EXECUTION =
            Pattern.compile("Execution [Tt]ime:\\s+([\\d.]+)\\s+ms");

    // ── MySQL column order we care about ─────────────────────────────────────
    private static final String[] MYSQL_COLS =
            {"id","select_type","table","partitions","type","possible_keys",
             "key","key_len","ref","rows","filtered","Extra"};

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color COL_COST    = new Color(59, 130, 246);   // blue
    private static final Color COL_ROWS    = new Color(16, 185, 129);   // green
    private static final Color COL_TIME    = new Color(245, 158, 11);   // amber
    private static final Color COL_WARN    = new Color(239, 68, 68);    // red
    private static final Color COL_BADGE_BG= new Color(30, 30, 50);

    // ── Widgets ───────────────────────────────────────────────────────────────
    private final JLabel statsLabel;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JPanel contentArea;   // swapped between tree / table / text
    private final CardLayout cards;

    private static final String CARD_TREE  = "tree";
    private static final String CARD_TABLE = "table";
    private static final String CARD_TEXT  = "text";

    // text card
    private final JTextArea textPane;
    // tree card
    private final JTree planTree;
    private final DefaultTreeModel treeModel;
    // table card
    private final JTable planTable;
    private final DefaultTableModel tableModel;

    public ExplainPlanPanel() {
        super(new BorderLayout(0, 0));

        // ── Stats bar ────────────────────────────────────────────────────────
        statsLabel = new JLabel(" ");
        statsLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        statsLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel statsBar = new JPanel(new BorderLayout());
        statsBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground") != null
                        ? UIManager.getColor("Separator.foreground") : Color.GRAY));
        statsBar.add(statsLabel, BorderLayout.WEST);

        // ── Text card ────────────────────────────────────────────────────────
        textPane = new JTextArea();
        textPane.setEditable(false);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textPane.setLineWrap(false);

        JScrollPane textScroll = new JScrollPane(textPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // ── Tree card ────────────────────────────────────────────────────────
        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("(empty)"));
        planTree = new JTree(treeModel);
        planTree.setRootVisible(true);
        planTree.setShowsRootHandles(true);
        planTree.setRowHeight(0);   // variable row height
        planTree.setCellRenderer(new PlanNodeRenderer());
        planTree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // ── Table card ───────────────────────────────────────────────────────
        tableModel = new DefaultTableModel() {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        planTable = new JTable(tableModel);
        planTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        planTable.setRowHeight(22);
        planTable.setFillsViewportHeight(true);
        planTable.setDefaultRenderer(Object.class, new MysqlCellRenderer());
        planTable.getTableHeader().setReorderingAllowed(false);

        // ── Card layout ──────────────────────────────────────────────────────
        cards = new CardLayout();
        contentArea = new JPanel(cards);
        contentArea.add(textScroll, CARD_TEXT);
        contentArea.add(new JScrollPane(planTree), CARD_TREE);
        JScrollPane tableScroll = new JScrollPane(planTable);
        tableScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        contentArea.add(tableScroll, CARD_TABLE);

        // ── Progress / status ────────────────────────────────────────────────
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("Generating plan…");
        progressBar.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.add(progressBar, BorderLayout.NORTH);
        bottomBar.add(statusLabel, BorderLayout.SOUTH);

        add(statsBar,    BorderLayout.NORTH);
        add(contentArea, BorderLayout.CENTER);
        add(bottomBar,   BorderLayout.SOUTH);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void displayPlan(String plan, long timeMs) {
        displayPlan(plan, timeMs, null);
    }

    public void displayPlan(String plan, long timeMs, DatabaseType dbType) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(false);
            statusLabel.setText("Plan generated in " + timeMs + " ms");

            if (plan == null || plan.isBlank()) {
                showText("(no plan output)");
                statsLabel.setText(" ");
                return;
            }

            // Auto-detect format
            if (looksLikePostgres(plan)) {
                renderPostgresPlan(plan);
            } else if (looksLikeMysqlTabular(plan)) {
                renderMysqlPlan(plan);
            } else {
                showText(plan);
                statsLabel.setText(buildGenericStats(plan, timeMs));
            }
        });
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    public void showLoading() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(true);
            statusLabel.setText("Generating plan…");
            statsLabel.setText(" ");
        });
    }

    public void hideLoading() {
        SwingUtilities.invokeLater(() -> progressBar.setVisible(false));
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            showText("");
            progressBar.setVisible(false);
            statusLabel.setText(" ");
            statsLabel.setText(" ");
        });
    }

    // ── PostgreSQL renderer ───────────────────────────────────────────────────

    private boolean looksLikePostgres(String plan) {
        // PostgreSQL EXPLAIN always has "cost=" on every node line
        return plan.contains("cost=") && plan.contains("rows=") && plan.contains("width=");
    }

    private void renderPostgresPlan(String plan) {
        String[] lines = plan.split("\n");
        DefaultMutableTreeNode root = null;
        List<Integer> indentStack = new ArrayList<>();
        List<DefaultMutableTreeNode> nodeStack = new ArrayList<>();

        String planningTime = null, executionTime = null;
        double totalCost = 0;

        for (String raw : lines) {
            if (raw.trim().isEmpty()) continue;

            // Extract summary lines — not tree nodes
            Matcher pm = PG_PLANNING.matcher(raw);
            if (pm.find()) { planningTime = pm.group(1); continue; }
            Matcher em = PG_EXECUTION.matcher(raw);
            if (em.find()) { executionTime = em.group(1); continue; }

            int arrowIdx = raw.indexOf("->");

            if (arrowIdx >= 0) {
                // Child node line: "   ->  Hash Join  (cost=...)"
                int indent = arrowIdx;
                String nodeText = raw.substring(arrowIdx + 2).trim();
                PlanNode pn = parsePlanLine(nodeText, raw);
                totalCost = Math.max(totalCost, pn.costHigh);
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(pn);

                if (root == null) {
                    root = newNode;
                    indentStack.add(indent);
                    nodeStack.add(root);
                } else {
                    // Pop until we find a node with strictly smaller indent
                    while (nodeStack.size() > 1 && indentStack.get(indentStack.size() - 1) >= indent) {
                        indentStack.remove(indentStack.size() - 1);
                        nodeStack.remove(nodeStack.size() - 1);
                    }
                    nodeStack.get(nodeStack.size() - 1).add(newNode);
                    indentStack.add(indent);
                    nodeStack.add(newNode);
                }
            } else if (root == null && raw.contains("cost=")) {
                // Root node — first line, no arrow, e.g. "Seq Scan on t  (cost=0.00..1.05 ...)"
                PlanNode pn = parsePlanLine(raw.trim(), raw);
                totalCost = Math.max(totalCost, pn.costHigh);
                root = new DefaultMutableTreeNode(pn);
                indentStack.add(0);
                nodeStack.add(root);
            }
            // Lines without "->" and without "cost=" are continuation/filter lines — skip
        }

        if (root == null) { showText(plan); return; }

        if (totalCost <= 0) totalCost = 1;
        propagateTotalCost(root, totalCost);

        treeModel.setRoot(root);
        expandAll(planTree);
        cards.show(contentArea, CARD_TREE);

        StringBuilder sb = new StringBuilder("  ");
        if (executionTime != null)
            sb.append("⏱ Execution: ").append(executionTime).append(" ms   ");
        if (planningTime != null)
            sb.append("📐 Planning: ").append(planningTime).append(" ms   ");
        sb.append("💰 Max cost: ").append(String.format("%.2f", totalCost));
        statsLabel.setText(sb.toString());
    }

    private PlanNode parsePlanLine(String nodeText, String raw) {
        PlanNode pn = new PlanNode();
        pn.label = nodeText.replaceAll("\\(cost=.*", "").trim();
        if (pn.label.isEmpty()) pn.label = nodeText;

        Matcher cm = PG_COST.matcher(raw);
        if (cm.find()) {
            String[] costs = cm.group(1).split("\\.\\.");
            try {
                pn.costLow  = Double.parseDouble(costs[0]);
                pn.costHigh = costs.length > 1 ? Double.parseDouble(costs[1]) : pn.costLow;
            } catch (NumberFormatException ignored) {}
            try { pn.estRows = Long.parseLong(cm.group(2)); } catch (NumberFormatException ignored) {}
            try { pn.width   = Integer.parseInt(cm.group(3)); } catch (NumberFormatException ignored) {}
        }

        Matcher am = PG_ACTUAL.matcher(raw);
        if (am.find()) {
            pn.actualTime = am.group(1);
            try { pn.actRows = Long.parseLong(am.group(2)); } catch (NumberFormatException ignored) {}
        }

        // Classify node type
        String up = pn.label.toUpperCase();
        if (up.contains("SEQ SCAN"))          pn.type = NodeType.SEQ_SCAN;
        else if (up.contains("INDEX"))        pn.type = NodeType.INDEX_SCAN;
        else if (up.contains("HASH"))         pn.type = NodeType.HASH;
        else if (up.contains("NESTED LOOP"))  pn.type = NodeType.NESTED_LOOP;
        else if (up.contains("MERGE"))        pn.type = NodeType.MERGE;
        else if (up.contains("SORT"))         pn.type = NodeType.SORT;
        else if (up.contains("AGGREGATE"))    pn.type = NodeType.AGGREGATE;
        else                                  pn.type = NodeType.OTHER;

        return pn;
    }

    private void propagateTotalCost(DefaultMutableTreeNode node, double total) {
        Object obj = node.getUserObject();
        if (obj instanceof PlanNode pn) pn.totalCost = total;
        for (int i = 0; i < node.getChildCount(); i++)
            propagateTotalCost((DefaultMutableTreeNode) node.getChildAt(i), total);
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    // ── MySQL renderer ────────────────────────────────────────────────────────

    private boolean looksLikeMysqlTabular(String plan) {
        return plan.contains("select_type") || plan.contains("possible_keys");
    }

    private void renderMysqlPlan(String plan) {
        // Parse pipe-delimited table
        String[] lines = plan.split("\n");
        List<String[]> rows = new ArrayList<>();
        String[] headers = null;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("+") || line.isEmpty()) continue;
            if (line.startsWith("|")) {
                String[] cells = line.split("\\|");
                String[] cleaned = new String[cells.length - 1];
                for (int i = 1; i < cells.length; i++)
                    cleaned[i - 1] = cells[i].trim();
                if (headers == null) headers = cleaned;
                else rows.add(cleaned);
            }
        }

        if (headers == null) { showText(plan); return; }

        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);
        for (String h : headers) tableModel.addColumn(h);
        for (String[] row : rows) tableModel.addRow(row);

        // Auto-size columns
        for (int c = 0; c < planTable.getColumnCount(); c++) {
            int w = planTable.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(planTable,
                            planTable.getColumnName(c), false, false, -1, c)
                    .getPreferredSize().width + 16;
            for (int r = 0; r < planTable.getRowCount(); r++) {
                Component comp = planTable.getDefaultRenderer(Object.class)
                        .getTableCellRendererComponent(planTable,
                                planTable.getValueAt(r, c), false, false, r, c);
                w = Math.max(w, comp.getPreferredSize().width + 16);
            }
            planTable.getColumnModel().getColumn(c).setPreferredWidth(Math.min(w, 220));
        }

        cards.show(contentArea, CARD_TABLE);
        statsLabel.setText("  " + rows.size() + " step(s)   ⌨ Full-scan rows highlighted in amber");
    }

    // ── Generic text renderer ─────────────────────────────────────────────────

    private void showText(String text) {
        textPane.setText(text);
        textPane.setCaretPosition(0);
        cards.show(contentArea, CARD_TEXT);
    }

    private String buildGenericStats(String plan, long timeMs) {
        return "  ⏱ " + timeMs + " ms";
    }

    // ── Tree node data ────────────────────────────────────────────────────────

    enum NodeType { SEQ_SCAN, INDEX_SCAN, HASH, NESTED_LOOP, MERGE, SORT, AGGREGATE, OTHER }

    static class PlanNode {
        String   label     = "";
        NodeType type      = NodeType.OTHER;
        double   costLow   = -1, costHigh = -1, totalCost = 1;
        long     estRows   = -1, actRows  = -1;
        int      width     = -1;
        String   actualTime = null;

        /** 0..1 relative cost fraction */
        double costFraction() {
            return totalCost > 0 && costHigh >= 0 ? Math.min(costHigh / totalCost, 1.0) : 0;
        }
    }

    // ── Tree cell renderer ────────────────────────────────────────────────────

    private static class PlanNodeRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            if (!(value instanceof DefaultMutableTreeNode dmn)) return this;
            if (!(dmn.getUserObject() instanceof PlanNode pn)) return this;

            setIcon(nodeIcon(pn.type));
            setText(buildHtml(pn, selected));
            setToolTipText(buildTooltip(pn));
            return this;
        }

        private String buildHtml(PlanNode pn, boolean selected) {
            StringBuilder sb = new StringBuilder("<html><body style='font-family:monospace;font-size:11px'>");

            // Node label
            String labelColor = selected ? "#ffffff" : labelColor(pn.type);
            sb.append("<span style='color:").append(labelColor).append(";font-weight:bold'>")
              .append(escHtml(pn.label)).append("</span>");

            // Cost badge
            if (pn.costHigh >= 0) {
                String costColor = costColor(pn.costFraction());
                sb.append("  <span style='color:").append(costColor)
                  .append("'>cost=").append(String.format("%.2f..%.2f", pn.costLow, pn.costHigh))
                  .append("</span>");
            }

            // Rows badge
            if (pn.estRows >= 0) {
                sb.append("  <span style='color:#10b981'>rows=").append(pn.estRows).append("</span>");
            }

            // Actual time
            if (pn.actualTime != null) {
                sb.append("  <span style='color:#f59e0b'>⏱ ").append(pn.actualTime).append(" ms</span>");
            }

            // Actual rows (if different from estimated)
            if (pn.actRows >= 0 && pn.actRows != pn.estRows) {
                String rowColor = (pn.estRows > 0 && Math.abs(pn.actRows - pn.estRows) > pn.estRows * 2)
                        ? "#ef4444" : "#6ee7b7";
                sb.append("  <span style='color:").append(rowColor)
                  .append("'>actual=").append(pn.actRows).append("</span>");
            }

            sb.append("</body></html>");
            return sb.toString();
        }

        private String buildTooltip(PlanNode pn) {
            StringBuilder sb = new StringBuilder("<html>");
            sb.append("<b>").append(escHtml(pn.label)).append("</b><br>");
            if (pn.costHigh >= 0)
                sb.append("Cost: ").append(pn.costLow).append("..").append(pn.costHigh).append("<br>");
            if (pn.estRows >= 0)
                sb.append("Estimated rows: ").append(pn.estRows).append("<br>");
            if (pn.width >= 0)
                sb.append("Row width: ").append(pn.width).append(" bytes<br>");
            if (pn.actualTime != null)
                sb.append("Actual time: ").append(pn.actualTime).append(" ms<br>");
            if (pn.actRows >= 0)
                sb.append("Actual rows: ").append(pn.actRows).append("<br>");
            sb.append("</html>");
            return sb.toString();
        }

        private String labelColor(NodeType t) {
            return switch (t) {
                case SEQ_SCAN    -> "#f87171";   // red — potentially expensive
                case INDEX_SCAN  -> "#34d399";   // green — good
                case HASH        -> "#60a5fa";   // blue
                case NESTED_LOOP -> "#c084fc";   // purple
                case MERGE       -> "#38bdf8";   // sky
                case SORT        -> "#fbbf24";   // amber
                case AGGREGATE   -> "#a78bfa";   // violet
                default          -> "#e2e8f0";   // light grey
            };
        }

        private String costColor(double fraction) {
            if (fraction > 0.7) return "#ef4444";   // red
            if (fraction > 0.4) return "#f59e0b";   // amber
            return "#10b981";                        // green
        }

        private Icon nodeIcon(NodeType t) {
            int size = 14;
            return new Icon() {
                @Override public int getIconWidth()  { return size; }
                @Override public int getIconHeight() { return size; }
                @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(iconColor(t));
                    switch (t) {
                        case SEQ_SCAN   -> g2.fillRect(x + 1, y + 1, size - 2, size - 2);
                        case INDEX_SCAN -> { int[] xs = {x+7,x+1,x+13}; int[] ys = {y+1,y+13,y+13};
                                            g2.fillPolygon(xs, ys, 3); }
                        case HASH       -> g2.fillOval(x + 1, y + 1, size - 2, size - 2);
                        default         -> { g2.fillRoundRect(x+1, y+1, size-2, size-2, 4, 4); }
                    }
                    g2.dispose();
                }
            };
        }

        private Color iconColor(NodeType t) {
            return switch (t) {
                case SEQ_SCAN    -> new Color(248, 113, 113);
                case INDEX_SCAN  -> new Color(52, 211, 153);
                case HASH        -> new Color(96, 165, 250);
                case NESTED_LOOP -> new Color(192, 132, 252);
                case SORT        -> new Color(251, 191, 36);
                case AGGREGATE   -> new Color(167, 139, 250);
                default          -> new Color(148, 163, 184);
            };
        }

        private static String escHtml(String s) {
            return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        }
    }

    // ── MySQL table renderer ──────────────────────────────────────────────────

    private static class MysqlCellRenderer extends DefaultTableCellRenderer {
        private static final Color WARN_BG = new Color(120, 80, 0, 80);
        private static final Color GOOD_BG = new Color(0, 100, 60, 60);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

            if (!isSelected) {
                String colName = table.getColumnName(col).toLowerCase();
                String val = value == null ? "" : value.toString().toLowerCase();

                // Highlight bad access types
                if (colName.equals("type")) {
                    if (val.equals("all") || val.equals("index")) {
                        setBackground(WARN_BG);
                        setForeground(new Color(251, 191, 36));
                    } else if (val.equals("ref") || val.equals("eq_ref") || val.equals("const")) {
                        setBackground(GOOD_BG);
                        setForeground(new Color(52, 211, 153));
                    } else {
                        setBackground(table.getBackground());
                        setForeground(table.getForeground());
                    }
                } else if (colName.equals("extra") && (val.contains("using filesort")
                        || val.contains("using temporary"))) {
                    setForeground(new Color(248, 113, 113));
                    setBackground(table.getBackground());
                } else if (colName.equals("key") && (val.isEmpty() || val.equals("null"))) {
                    setForeground(new Color(248, 113, 113));
                    setBackground(table.getBackground());
                } else {
                    setBackground(table.getBackground());
                    setForeground(table.getForeground());
                }
            }
            return this;
        }
    }
}

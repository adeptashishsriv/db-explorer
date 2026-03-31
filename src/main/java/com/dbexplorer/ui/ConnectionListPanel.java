package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.model.DatabaseType;
import com.dbexplorer.service.ConnectionManager;
import com.dbexplorer.service.SchemaExplorerService;

/**
 * Left panel showing saved connections as a tree with expandable schema objects.
 * Tree structure: Root → Connection → Schema → (Tables, Views, Sequences, Indexes, Functions, Procedures) → objects
 *
 * Resilience: every background metadata fetch calls ensureConnection() which
 * silently reconnects if the JDBC connection has gone stale (idle timeout,
 * server restart, network blip).  The user never sees a raw "Error: connection
 * closed" message — the tree just reloads transparently.
 */
public class ConnectionListPanel extends JPanel {

    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final DefaultTreeModel treeModel;
    private final ConnectionManager connectionManager;
    private final SchemaExplorerService schemaExplorer;

    private Consumer<ConnectionInfo> onConnect;
    private Consumer<ConnectionInfo> onEdit;
    private Consumer<ConnectionInfo> onDelete;
    private Consumer<ConnectionInfo> onOpenQueryTab;
    private BiConsumer<ConnectionInfo, String> onViewData;
    private BiConsumer<ConnectionInfo, String> onShowDiagram;
    private BiConsumer<ConnectionInfo, String> onExportDdl;
    // table export: (connectionInfo, schema, tableName) — encoded as "schema\0table"
    private BiConsumer<ConnectionInfo, String> onExportTable;

    // Marker strings for category nodes
    static final String CAT_TABLES = "Tables";
    static final String CAT_VIEWS = "Views";
    static final String CAT_MAT_VIEWS = "Materialized Views";
    static final String CAT_SEQUENCES = "Sequences";
    static final String CAT_INDEXES = "Indexes";
    static final String CAT_FUNCTIONS = "Functions";
    static final String CAT_PROCEDURES = "Procedures";

    public ConnectionListPanel(ConnectionManager connectionManager) {
        super(new BorderLayout());
        this.connectionManager = connectionManager;
        this.schemaExplorer = new SchemaExplorerService();
        setBorder(BorderFactory.createTitledBorder("Connections"));
        setPreferredSize(new Dimension(260, 0));

        root = new DefaultMutableTreeNode("Databases");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        tree.setCellRenderer(new SchemaTreeCellRenderer());

        // Lazy-load children when a node is expanded
        tree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                lazyLoadChildren(node);
            }
            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) {}
        });

        // Right-click context menu
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { showPopup(e); }
            private void showPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int selRow = tree.getClosestRowForLocation(e.getX(), e.getY());
                tree.setSelectionRow(selRow);
                
                TreePath path = tree.getPathForRow(selRow);
                if (path == null) return;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObj = node.getUserObject();

                if (userObj instanceof ConnectionInfo info) {
                    JPopupMenu menu = new JPopupMenu();
                    boolean connected = connectionManager.isConnected(info.getId());

                    JMenuItem connectItem = new JMenuItem(connected ? "Disconnect" : "Connect");
                    connectItem.addActionListener(a -> { if (onConnect != null) onConnect.accept(info); });
                    menu.add(connectItem);

                    if (connected) {
                        JMenuItem refreshItem = new JMenuItem("Refresh Schemas");
                        refreshItem.addActionListener(a -> loadSchemasForConnection(info));
                        menu.add(refreshItem);

                        JMenuItem openTabItem = new JMenuItem("Open Query Tab");
                        openTabItem.addActionListener(a -> {
                            if (onOpenQueryTab != null) onOpenQueryTab.accept(info);
                        });
                        menu.add(openTabItem);
                    }

                    JMenuItem editItem = new JMenuItem("Edit");
                    editItem.addActionListener(a -> { if (onEdit != null) onEdit.accept(info); });
                    menu.add(editItem);

                    JMenuItem deleteItem = new JMenuItem("Delete");
                    deleteItem.addActionListener(a -> { if (onDelete != null) onDelete.accept(info); });
                    menu.add(deleteItem);

                    menu.show(tree, e.getX(), e.getY());
                } else if (userObj instanceof SchemaNode sn) {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem diagramItem = new JMenuItem("Schema Diagram", DbIcons.MENU_DIAGRAM);
                    diagramItem.addActionListener(a -> {
                        if (onShowDiagram != null)
                            onShowDiagram.accept(sn.connectionInfo(), sn.schema());
                    });
                    menu.add(diagramItem);
                    JMenuItem ddlItem = new JMenuItem("Export DDL", DbIcons.MENU_DDL);
                    ddlItem.addActionListener(a -> {
                        if (onExportDdl != null)
                            onExportDdl.accept(sn.connectionInfo(), sn.schema());
                    });
                    menu.add(ddlItem);
                    menu.show(tree, e.getX(), e.getY());
                } else if (userObj instanceof ObjectNode on && on.isTable()) {
                    JPopupMenu menu = new JPopupMenu();

                    JMenuItem viewData = new JMenuItem("View Data", DbIcons.MENU_VIEW_DATA);
                    viewData.addActionListener(a -> {
                        if (onViewData != null) {
                            String tableName = on.name;
                            String schema = on.schema;
                            String sql;
                            if (schema != null && !schema.isEmpty() && on.connectionInfo.getDbType() != DatabaseType.MYSQL) {
                                sql = "SELECT * FROM " + schema + "." + tableName;
                            } else {
                                sql = "SELECT * FROM " + tableName;
                            }
                            onViewData.accept(on.connectionInfo, sql);
                        }
                    });
                    menu.add(viewData);

                    // Export submenu
                    JMenu exportMenu = new JMenu("Export");
                    exportMenu.setIcon(DbIcons.MENU_EXPORT);
                    String tableKey = on.schema + "\0" + on.name;

                    JMenuItem exportDdl = new JMenuItem("DDL", DbIcons.MENU_DDL);
                    exportDdl.addActionListener(a -> {
                        if (onExportTable != null) onExportTable.accept(on.connectionInfo, "DDL\0" + tableKey);
                    });
                    JMenuItem exportInsert = new JMenuItem("INSERT Statements", DbIcons.MENU_INSERT);
                    exportInsert.addActionListener(a -> {
                        if (onExportTable != null) onExportTable.accept(on.connectionInfo, "INSERT\0" + tableKey);
                    });
                    JMenuItem exportUpdate = new JMenuItem("Update SQL", DbIcons.MENU_UPDATE);
                    exportUpdate.addActionListener(a -> {
                        if (onExportTable != null) onExportTable.accept(on.connectionInfo, "UPDATE\0" + tableKey);
                    });
                    JMenuItem exportCsv = new JMenuItem("Export to CSV", DbIcons.MENU_CSV);
                    exportCsv.addActionListener(a -> {
                        if (onExportTable != null) onExportTable.accept(on.connectionInfo, "CSV\0" + tableKey);
                    });

                    exportMenu.add(exportDdl);
                    exportMenu.add(exportInsert);
                    exportMenu.add(exportUpdate);
                    exportMenu.addSeparator();
                    exportMenu.add(exportCsv);
                    menu.add(exportMenu);

                    menu.show(tree, e.getX(), e.getY());
                } else if (userObj instanceof ObjectNode on && on.isMatView()) {
                    JPopupMenu menu = new JPopupMenu();

                    JMenuItem viewData = new JMenuItem("View Data", DbIcons.MENU_VIEW_DATA);
                    viewData.addActionListener(a -> {
                        if (onViewData != null) {
                            String schema = on.schema;
                            String sql;
                            if (schema != null && !schema.isEmpty() && on.connectionInfo.getDbType() != DatabaseType.MYSQL) {
                                sql = "SELECT * FROM " + schema + "." + on.name;
                            } else {
                                sql = "SELECT * FROM " + on.name;
                            }
                            onViewData.accept(on.connectionInfo, sql);
                        }
                    });
                    menu.add(viewData);

                    JMenuItem exportDdl = new JMenuItem("Export DDL", DbIcons.MENU_DDL);
                    exportDdl.addActionListener(a -> {
                        if (onExportTable != null)
                            onExportTable.accept(on.connectionInfo, "MAT_VIEW_DDL\0" + on.schema + "\0" + on.name);
                    });
                    menu.add(exportDdl);

                    menu.show(tree, e.getX(), e.getY());
                }
            }
        });

        // Double-click to connect/disconnect
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ConnectionInfo info = getSelectedConnection();
                    if (info != null && onConnect != null) onConnect.accept(info);
                }
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
        refreshList();
    }

    /** Rebuild the top-level connection nodes. */
    public void refreshList() {
        root.removeAllChildren();
        for (ConnectionInfo info : connectionManager.getConnections()) {
            DefaultMutableTreeNode connNode = new DefaultMutableTreeNode(info);
            if (connectionManager.isConnected(info.getId())) {
                // Add a dummy child so the node is expandable; real children loaded on expand
                connNode.add(new DefaultMutableTreeNode("Loading..."));
            }
            root.add(connNode);
        }
        treeModel.reload();
        tree.expandRow(0);
    }

    // ── Connection resilience ─────────────────────────────────────────────────

    /**
     * Returns the active JDBC connection for {@code info}, or null if not connected.
     * No network probe — purely a local map lookup.
     */
    private Connection getConnection(ConnectionInfo info) {
        return connectionManager.getActiveConnection(info.getId());
    }

    /** Called after a successful connect — populates schemas (or tables for DynamoDB) under the connection node. */
    public void loadSchemasForConnection(ConnectionInfo info) {
        DefaultMutableTreeNode connNode = findConnectionNode(info);
        if (connNode == null) return;

        // DynamoDB: list tables directly under connection (no schema level)
        if (info.getDbType() == DatabaseType.DYNAMODB) {
            loadDynamoTablesForConnection(connNode, info);
            return;
        }

        // Show loading state
        connNode.removeAllChildren();
        connNode.add(new DefaultMutableTreeNode("Loading schemas..."));
        treeModel.reload(connNode);

        // Fetch schemas in background
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                Connection conn = getConnection(info);
                if (conn == null) throw new java.sql.SQLException("Not connected");
                return schemaExplorer.getSchemas(conn, info.getDbType());
            }
            @Override
            protected void done() {
                try {
                    List<String> schemas = get();
                    connNode.removeAllChildren();
                    if (schemas.isEmpty()) {
                        connNode.add(new DefaultMutableTreeNode("(no schemas)"));
                    } else {
                        for (String schema : schemas) {
                            DefaultMutableTreeNode schemaNode = new DefaultMutableTreeNode(
                                    new SchemaNode(schema, info));
                            schemaNode.add(new DefaultMutableTreeNode("Loading..."));
                            connNode.add(schemaNode);
                        }
                    }
                    treeModel.reload(connNode);
                    tree.expandPath(new TreePath(connNode.getPath()));
                } catch (Exception ex) {
                    connNode.removeAllChildren();
                    connNode.add(new DefaultMutableTreeNode("Error: " + ex.getMessage()));
                    treeModel.reload(connNode);
                }
            }
        }.execute();
    }

    /** For DynamoDB connections, list tables directly under the connection node. */
    private void loadDynamoTablesForConnection(DefaultMutableTreeNode connNode, ConnectionInfo info) {
        connNode.removeAllChildren();
        connNode.add(new DefaultMutableTreeNode("Loading tables..."));
        treeModel.reload(connNode);

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return connectionManager.getDynamoDbExecutor().listTables(info.getId());
            }
            @Override
            protected void done() {
                try {
                    List<String> tables = get();
                    connNode.removeAllChildren();
                    if (tables.isEmpty()) {
                        connNode.add(new DefaultMutableTreeNode("(no tables)"));
                    } else {
                        DefaultMutableTreeNode tablesFolder = new DefaultMutableTreeNode("Tables (" + tables.size() + ")");
                        for (String table : tables) {
                            tablesFolder.add(new DefaultMutableTreeNode(table));
                        }
                        connNode.add(tablesFolder);
                    }
                    treeModel.reload(connNode);
                    tree.expandPath(new TreePath(connNode.getPath()));
                } catch (Exception ex) {
                    connNode.removeAllChildren();
                    connNode.add(new DefaultMutableTreeNode("Error: " + ex.getMessage()));
                    treeModel.reload(connNode);
                }
            }
        }.execute();
    }

    /** Lazy-load children when a node is about to be expanded. */
    private void lazyLoadChildren(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();

        // Connection node: load schemas if first expand
        if (userObj instanceof ConnectionInfo info) {
            if (node.getChildCount() == 1
                    && node.getFirstChild() instanceof DefaultMutableTreeNode child
                    && "Loading...".equals(child.getUserObject())) {
                loadSchemasForConnection(info);
            }
            return;
        }

        // Schema node: load category folders
        if (userObj instanceof SchemaNode sn) {
            if (node.getChildCount() == 1
                    && node.getFirstChild() instanceof DefaultMutableTreeNode child
                    && "Loading...".equals(child.getUserObject())) {
                loadCategoriesForSchema(node, sn);
            }
            return;
        }

        // Category node: load objects
        if (userObj instanceof CategoryNode cn) {
            if (node.getChildCount() == 1
                    && node.getFirstChild() instanceof DefaultMutableTreeNode child
                    && "Loading...".equals(child.getUserObject())) {
                loadObjectsForCategory(node, cn);
            }
            return;
        }

        // Object node (Table, etc.): load columns if it's a table
        if (userObj instanceof ObjectNode on && on.isTable()) {
             if (node.getChildCount() == 1
                     && node.getFirstChild() instanceof DefaultMutableTreeNode child
                     && "Loading...".equals(child.getUserObject())) {
                 loadColumnsForTable(node, on);
             }
         }
    }

    private void loadCategoriesForSchema(DefaultMutableTreeNode schemaNode, SchemaNode sn) {
        schemaNode.removeAllChildren();
        String[] categories = {CAT_TABLES, CAT_VIEWS, CAT_MAT_VIEWS, CAT_SEQUENCES, CAT_INDEXES, CAT_FUNCTIONS, CAT_PROCEDURES};
        for (String cat : categories) {
            DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(
                    new CategoryNode(cat, sn.schema, sn.connectionInfo));
            catNode.add(new DefaultMutableTreeNode("Loading..."));
            schemaNode.add(catNode);
        }
        treeModel.reload(schemaNode);
    }

    private void loadObjectsForCategory(DefaultMutableTreeNode catNode, CategoryNode cn) {
        catNode.removeAllChildren();
        catNode.add(new DefaultMutableTreeNode("Loading..."));
        treeModel.reload(catNode);

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                Connection conn = getConnection(cn.connectionInfo);
                if (conn == null) throw new java.sql.SQLException("Not connected");
                DatabaseType dbType = cn.connectionInfo.getDbType();
                return switch (cn.category) {
                    case CAT_TABLES    -> schemaExplorer.getTables(conn, dbType, cn.schema);
                    case CAT_VIEWS     -> schemaExplorer.getViews(conn, dbType, cn.schema);
                    case CAT_MAT_VIEWS -> schemaExplorer.getMatViews(conn, dbType, cn.schema);
                    case CAT_SEQUENCES -> schemaExplorer.getSequences(conn, dbType, cn.schema);
                    case CAT_INDEXES   -> schemaExplorer.getIndexes(conn, dbType, cn.schema);
                    case CAT_FUNCTIONS -> schemaExplorer.getFunctions(conn, dbType, cn.schema);
                    case CAT_PROCEDURES-> schemaExplorer.getProcedures(conn, dbType, cn.schema);
                    default            -> List.of();
                };
            }
            @Override
            protected void done() {
                try {
                    List<String> objects = get();
                    catNode.removeAllChildren();
                    if (objects.isEmpty()) {
                        catNode.add(new DefaultMutableTreeNode("(empty)"));
                    } else {
                        boolean isTable = CAT_TABLES.equals(cn.category);
                        for (String name : objects) {
                            DefaultMutableTreeNode objNode = new DefaultMutableTreeNode(
                                    new ObjectNode(name, cn.category, cn.schema, cn.connectionInfo));
                            if (isTable) objNode.add(new DefaultMutableTreeNode("Loading..."));
                            catNode.add(objNode);
                        }
                    }
                } catch (Exception ex) {
                    catNode.removeAllChildren();
                    catNode.add(new DefaultMutableTreeNode("Error: " + ex.getMessage()));
                }
                treeModel.reload(catNode);
            }
        }.execute();
    }

    private void loadColumnsForTable(DefaultMutableTreeNode tableNode, ObjectNode on) {
        tableNode.removeAllChildren();
        tableNode.add(new DefaultMutableTreeNode("Loading..."));
        treeModel.reload(tableNode);

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                Connection conn = getConnection(on.connectionInfo);
                if (conn == null) throw new java.sql.SQLException("Not connected");
                return schemaExplorer.getColumns(conn, on.connectionInfo.getDbType(), on.schema, on.name);
            }
            @Override
            protected void done() {
                try {
                    List<String> columns = get();
                    tableNode.removeAllChildren();
                    if (columns.isEmpty()) {
                        tableNode.add(new DefaultMutableTreeNode("(no columns)"));
                    } else {
                        for (String col : columns) {
                            tableNode.add(new DefaultMutableTreeNode(col));
                        }
                    }
                } catch (Exception ex) {
                    tableNode.removeAllChildren();
                    tableNode.add(new DefaultMutableTreeNode("Error: " + ex.getMessage()));
                }
                treeModel.reload(tableNode);
            }
        }.execute();
    }

    /** Walk up the tree from the selected node to find the owning ConnectionInfo. */
    public ConnectionInfo getSelectedConnection() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        for (int i = 0; i < path.getPathCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getPathComponent(i);
            if (node.getUserObject() instanceof ConnectionInfo info) return info;
        }
        return null;
    }

    private DefaultMutableTreeNode findConnectionNode(ConnectionInfo info) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if (child.getUserObject() instanceof ConnectionInfo ci
                    && ci.getId().equals(info.getId())) {
                return child;
            }
        }
        return null;
    }

    public void addTreeSelectionListener(TreeSelectionListener listener) {
        tree.addTreeSelectionListener(listener);
    }

    public void setOnConnect(Consumer<ConnectionInfo> onConnect) { this.onConnect = onConnect; }
    public void setOnEdit(Consumer<ConnectionInfo> onEdit) { this.onEdit = onEdit; }
    public void setOnDelete(Consumer<ConnectionInfo> onDelete) { this.onDelete = onDelete; }
    public void setOnOpenQueryTab(Consumer<ConnectionInfo> onOpenQueryTab) { this.onOpenQueryTab = onOpenQueryTab; }
    public void setOnViewData(BiConsumer<ConnectionInfo, String> onViewData) { this.onViewData = onViewData; }
    public void setOnShowDiagram(BiConsumer<ConnectionInfo, String> onShowDiagram) { this.onShowDiagram = onShowDiagram; }
    public void setOnExportDdl(BiConsumer<ConnectionInfo, String> onExportDdl) { this.onExportDdl = onExportDdl; }
    public void setOnExportTable(BiConsumer<ConnectionInfo, String> onExportTable) { this.onExportTable = onExportTable; }

    // --- Inner data classes for tree node user objects ---

    /** Represents a schema node in the tree. */
    record SchemaNode(String schema, ConnectionInfo connectionInfo) {
        @Override public String toString() { return schema; }
    }

    /** Represents a category folder (Tables, Views, etc.) under a schema. */
    record CategoryNode(String category, String schema, ConnectionInfo connectionInfo) {
        @Override public String toString() { return category; }
    }

    /** Represents a database object (Table, View, etc.). */
    record ObjectNode(String name, String category, String schema, ConnectionInfo connectionInfo) {
        @Override public String toString() { return name; }
        public boolean isTable() { return CAT_TABLES.equals(category); }
        public boolean isMatView() { return CAT_MAT_VIEWS.equals(category); }
    }

    // --- Custom tree cell renderer with icons ---

    private class SchemaTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (!(value instanceof DefaultMutableTreeNode node)) return this;

            Object obj = node.getUserObject();
            if (obj instanceof ConnectionInfo info) {
                setText(info.getName());
                boolean connected = connectionManager.isConnected(info.getId());
                if (info.getDbType() == DatabaseType.DYNAMODB) {
                    setIcon(DbIcons.DATABASE_DYNAMO);
                } else if (info.getDbType() == DatabaseType.SQLITE) {
                    setIcon(DbIcons.DATABASE_SQLITE);
                } else if (info.getDbType() == DatabaseType.GENERIC) {
                    setIcon(DbIcons.DATABASE_GENERIC);
                } else {
                    setIcon(connected ? DbIcons.DATABASE_CONNECTED : DbIcons.DATABASE_DISCONNECTED);
                }
                setForeground(connected ? new Color(0, 160, 0) : null);
            } else if (obj instanceof SchemaNode sn) {
                setText(sn.schema());
                setIcon(DbIcons.SCHEMA);
            } else if (obj instanceof CategoryNode cn) {
                setIcon(expanded ? DbIcons.FOLDER_OPEN : DbIcons.FOLDER_CLOSED);
                setText(cn.category());
            } else if (obj instanceof ObjectNode on) {
                setText(on.name());
                setIcon(switch (on.category) {
                    case CAT_TABLES    -> DbIcons.TABLE;
                    case CAT_VIEWS     -> DbIcons.VIEW;
                    case CAT_MAT_VIEWS -> DbIcons.MAT_VIEW;
                    case CAT_FUNCTIONS -> DbIcons.FUNCTION;
                    case CAT_PROCEDURES -> DbIcons.PROCEDURE;
                    case CAT_INDEXES   -> DbIcons.INDEX;
                    case CAT_SEQUENCES -> DbIcons.SEQUENCE;
                    default            -> DbIcons.TABLE;
                });
            } else if (obj instanceof String s) {
                if (s.startsWith("Loading")) {
                    setIcon(DbIcons.LOADING);
                    setText(s);
                } else if (!s.startsWith("(") && !s.startsWith("Error")) {
                    setIcon(DbIcons.COLUMN);
                    setText(s);
                } else {
                    setIcon(null);
                    setText(s);
                }
            }
            return this;
        }
    }
}

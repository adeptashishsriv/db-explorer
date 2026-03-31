package com.dbexplorer.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.dbexplorer.model.DatabaseType;

/**
 * Generates CREATE TABLE DDL for every table in a schema.
 * Uses JDBC DatabaseMetaData so it works across PostgreSQL, MySQL,
 * Oracle, SQL Server, and SQLite without any native SHOW/DBMS_METADATA calls.
 *
 * For databases that expose a native DDL function (PostgreSQL pg_get_ddl,
 * MySQL SHOW CREATE TABLE, SQLite sqlite_master) we prefer that because it
 * preserves constraints, defaults, and comments exactly as stored.
 * For Oracle and SQL Server we fall back to a reconstructed DDL from metadata.
 */
public class DdlExportService {

    /**
     * Generates DDL for a single table.
     * Called on a background thread — never on the EDT.
     */
    public String exportTableDdl(Connection conn, DatabaseType dbType,
                                  String schema, String table) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("-- DDL Export\n");
        sb.append("-- Table   : ").append(table).append("\n");
        sb.append("-- Schema  : ").append(schema).append("\n");
        sb.append("-- Database: ").append(dbType.getDisplayName()).append("\n");
        sb.append("-- Generated: ").append(new java.util.Date()).append("\n");
        sb.append("-- ").append("=".repeat(70)).append("\n\n");
        sb.append(tableDDL(conn, dbType, schema, table));
        return sb.toString();
    }

    /**
     * Generates full schema DDL as a single SQL string.
     * Called on a background thread — never on the EDT.
     */
    public String exportSchema(Connection conn, DatabaseType dbType, String schema)
            throws SQLException {
        SchemaExplorerService svc = new SchemaExplorerService();
        List<String> tables = svc.getTables(conn, dbType, schema);
        List<String> views  = svc.getViews(conn, dbType, schema);

        StringBuilder sb = new StringBuilder();
        sb.append("-- DDL Export\n");
        sb.append("-- Schema  : ").append(schema).append("\n");
        sb.append("-- Database: ").append(dbType.getDisplayName()).append("\n");
        sb.append("-- Generated: ").append(new java.util.Date()).append("\n");
        sb.append("-- ").append("=".repeat(70)).append("\n\n");

        for (String table : tables) {
            sb.append(tableDDL(conn, dbType, schema, table));
            sb.append("\n\n");
        }

        for (String view : views) {
            sb.append(viewDDL(conn, dbType, schema, view));
            sb.append("\n\n");
        }

        return sb.toString().trim();
    }

    // ── Table DDL ─────────────────────────────────────────────────────────────

    private String tableDDL(Connection conn, DatabaseType dbType,
                             String schema, String table) throws SQLException {
        return switch (dbType) {
            case MYSQL     -> mysqlShowCreate(conn, schema, table, "TABLE");
            case SQLITE    -> sqliteCreate(conn, table);
            case POSTGRESQL -> pgShowCreate(conn, schema, table);
            default        -> reconstructedDDL(conn, dbType, schema, table);
        };
    }

    private String viewDDL(Connection conn, DatabaseType dbType,
                            String schema, String view) throws SQLException {
        return switch (dbType) {
            case MYSQL     -> mysqlShowCreate(conn, schema, view, "VIEW");
            case SQLITE    -> sqliteCreate(conn, view);
            case POSTGRESQL -> pgShowCreateView(conn, schema, view);
            default        -> reconstructedViewDDL(conn, dbType, schema, view);
        };
    }

    // ── PostgreSQL ────────────────────────────────────────────────────────────

    private String pgShowCreate(Connection conn, String schema, String table) {
        // pg_get_ddl is not a standard function; reconstruct from metadata + constraints
        try {
            return reconstructedDDL(conn, DatabaseType.POSTGRESQL, schema, table)
                    + pgForeignKeys(conn, schema, table);
        } catch (SQLException e) {
            return "-- Error generating DDL for " + table + ": " + e.getMessage();
        }
    }

    private String pgShowCreateView(Connection conn, String schema, String view) {
        String sql = "SELECT pg_get_viewdef(to_regclass(? || '.' || ?), true)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, view);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString(1);
                    if (def != null && !def.isBlank())
                        return "CREATE OR REPLACE VIEW " + schema + "." + view + " AS\n" + def.trim() + ";";
                }
            }
        } catch (SQLException ignored) {}
        return reconstructedViewDDL(conn, DatabaseType.POSTGRESQL, schema, view);
    }

    private String pgForeignKeys(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT
                    tc.constraint_name,
                    kcu.column_name,
                    ccu.table_schema AS foreign_schema,
                    ccu.table_name   AS foreign_table,
                    ccu.column_name  AS foreign_column,
                    rc.update_rule,
                    rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                     ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema    = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                     ON ccu.constraint_name = tc.constraint_name
                    AND ccu.table_schema    = tc.table_schema
                JOIN information_schema.referential_constraints rc
                     ON rc.constraint_name  = tc.constraint_name
                    AND rc.constraint_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = ?
                  AND tc.table_name   = ?
                ORDER BY tc.constraint_name
                """;
        StringBuilder fks = new StringBuilder();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fks.append("\nALTER TABLE ").append(schema).append(".").append(table)
                       .append("\n    ADD CONSTRAINT ").append(rs.getString("constraint_name"))
                       .append(" FOREIGN KEY (").append(rs.getString("column_name")).append(")")
                       .append("\n    REFERENCES ").append(rs.getString("foreign_schema"))
                       .append(".").append(rs.getString("foreign_table"))
                       .append(" (").append(rs.getString("foreign_column")).append(")")
                       .append(" ON UPDATE ").append(rs.getString("update_rule"))
                       .append(" ON DELETE ").append(rs.getString("delete_rule"))
                       .append(";");
                }
            }
        } catch (SQLException ignored) {}
        return fks.toString();
    }

    // ── MySQL ─────────────────────────────────────────────────────────────────

    private String mysqlShowCreate(Connection conn, String schema, String name, String type) {
        String sql = "SHOW CREATE " + type + " `" + schema + "`.`" + name + "`";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getString(2) + ";";
        } catch (SQLException e) {
            return "-- Error generating DDL for " + name + ": " + e.getMessage();
        }
        return "-- No DDL found for " + name;
    }

    // ── SQLite ────────────────────────────────────────────────────────────────

    private String sqliteCreate(Connection conn, String name) {
        String sql = "SELECT sql FROM sqlite_master WHERE name = ? AND sql IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1) + ";";
            }
        } catch (SQLException e) {
            return "-- Error generating DDL for " + name + ": " + e.getMessage();
        }
        return "-- No DDL found for " + name;
    }

    // ── Generic reconstructed DDL (Oracle, SQL Server, PostgreSQL fallback) ───

    private String reconstructedDDL(Connection conn, DatabaseType dbType,
                                     String schema, String table) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog   = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaArg = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;

        // Columns
        List<String> colDefs = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schemaArg, table, null)) {
            while (rs.next()) {
                colDefs.add(buildColumnDef(rs, dbType));
            }
        }

        // Primary key
        List<String> pkCols = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schemaArg, table)) {
            // collect in key-seq order
            java.util.TreeMap<Short, String> ordered = new java.util.TreeMap<>();
            while (rs.next()) ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            pkCols.addAll(ordered.values());
        }

        // Unique constraints
        List<String> uniqueDefs = new ArrayList<>();
        try (ResultSet rs = meta.getIndexInfo(catalog, schemaArg, table, true, false)) {
            java.util.Map<String, List<String>> idxCols = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                if (idxName == null) continue;
                // Skip PK index (same columns)
                idxCols.computeIfAbsent(idxName, k -> new ArrayList<>())
                       .add(rs.getString("COLUMN_NAME"));
            }
            for (var entry : idxCols.entrySet()) {
                if (!entry.getValue().equals(pkCols)) {
                    uniqueDefs.add("    CONSTRAINT " + entry.getKey()
                            + " UNIQUE (" + String.join(", ", entry.getValue()) + ")");
                }
            }
        } catch (SQLException ignored) {}

        // Build CREATE TABLE
        String qualifiedName = (schemaArg != null)
                ? schema + "." + table
                : (catalog != null ? catalog + "." + table : table);

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(qualifiedName).append(" (\n");
        for (int i = 0; i < colDefs.size(); i++) {
            sb.append("    ").append(colDefs.get(i));
            boolean last = (i == colDefs.size() - 1) && pkCols.isEmpty() && uniqueDefs.isEmpty();
            sb.append(last ? "\n" : ",\n");
        }
        if (!pkCols.isEmpty()) {
            sb.append("    PRIMARY KEY (").append(String.join(", ", pkCols)).append(")");
            sb.append(uniqueDefs.isEmpty() ? "\n" : ",\n");
        }
        for (int i = 0; i < uniqueDefs.size(); i++) {
            sb.append(uniqueDefs.get(i));
            sb.append(i == uniqueDefs.size() - 1 ? "\n" : ",\n");
        }
        sb.append(");");
        return sb.toString();
    }

    private String buildColumnDef(ResultSet rs, DatabaseType dbType) throws SQLException {
        String name     = rs.getString("COLUMN_NAME");
        String typeName = rs.getString("TYPE_NAME");
        int    size     = rs.getInt("COLUMN_SIZE");
        int    decimals = rs.getInt("DECIMAL_DIGITS");
        int    nullable = rs.getInt("NULLABLE");
        String defVal   = rs.getString("COLUMN_DEF");
        String autoInc  = rs.getString("IS_AUTOINCREMENT");

        StringBuilder col = new StringBuilder(name).append(" ");

        // Type + precision
        String upperType = typeName.toUpperCase();
        if (upperType.contains("CHAR") || upperType.contains("BINARY") || upperType.equals("NCHAR")) {
            col.append(typeName).append("(").append(size).append(")");
        } else if (upperType.contains("NUMERIC") || upperType.contains("DECIMAL")
                || upperType.contains("NUMBER")) {
            if (decimals > 0) col.append(typeName).append("(").append(size).append(",").append(decimals).append(")");
            else if (size > 0) col.append(typeName).append("(").append(size).append(")");
            else col.append(typeName);
        } else {
            col.append(typeName);
        }

        // Auto-increment / identity
        if ("YES".equalsIgnoreCase(autoInc)) {
            col.append(switch (dbType) {
                case POSTGRESQL -> ""; // handled by type (SERIAL/BIGSERIAL) or GENERATED
                case MYSQL      -> " AUTO_INCREMENT";
                case SQLSERVER  -> " IDENTITY(1,1)";
                case ORACLE     -> " GENERATED ALWAYS AS IDENTITY";
                default         -> "";
            });
        }

        // Default
        if (defVal != null && !defVal.isBlank()) {
            col.append(" DEFAULT ").append(defVal.trim());
        }

        // Nullability
        if (nullable == DatabaseMetaData.columnNoNulls) col.append(" NOT NULL");

        return col.toString();
    }

    // ── Materialized View DDL ─────────────────────────────────────────────────

    /**
     * Generates a CREATE MATERIALIZED VIEW DDL string for the named object.
     * Falls back to a comment if DDL cannot be retrieved.
     */
    public String exportMatViewDdl(Connection conn, DatabaseType dbType,
                                    String schema, String name) throws SQLException {
        return switch (dbType) {
            case POSTGRESQL -> pgMatViewDdl(conn, schema, name);
            case ORACLE     -> oracleMatViewDdl(conn, schema, name);
            case SQLSERVER  -> sqlServerMatViewDdl(conn, schema, name);
            default         -> "-- Materialized view DDL not available for " + name;
        };
    }

    private String pgMatViewDdl(Connection conn, String schema, String name) throws SQLException {
        String sql = "SELECT definition FROM pg_matviews WHERE schemaname = ? AND matviewname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String definition = rs.getString(1);
                    if (definition != null && !definition.isBlank())
                        return "CREATE MATERIALIZED VIEW " + schema + "." + name + " AS\n" + definition.trim() + ";";
                }
            }
        }
        return "-- Materialized view DDL not available for " + name;
    }

    private String oracleMatViewDdl(Connection conn, String schema, String name) throws SQLException {
        String sql = "SELECT QUERY FROM ALL_MVIEWS WHERE OWNER = ? AND MVIEW_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String query = rs.getString(1);
                    if (query != null && !query.isBlank())
                        return "CREATE MATERIALIZED VIEW " + schema + "." + name + " AS\n" + query.trim() + ";";
                }
            }
        }
        return "-- Materialized view DDL not available for " + name;
    }

    private String sqlServerMatViewDdl(Connection conn, String schema, String name) throws SQLException {
        String sql = "SELECT OBJECT_DEFINITION(OBJECT_ID(? + '.' + ?))";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString(1);
                    if (def != null && !def.isBlank())
                        return def.trim() + ";";
                }
            }
        }
        return "-- Materialized view DDL not available for " + name;
    }

    private String reconstructedViewDDL(Connection conn, DatabaseType dbType,
                                         String schema, String view) {
        // SQL Server
        if (dbType == DatabaseType.SQLSERVER) {
            String sql = "SELECT OBJECT_DEFINITION(OBJECT_ID(? + '.' + ?))";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                ps.setString(2, view);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String def = rs.getString(1);
                        if (def != null) return def.trim() + ";";
                    }
                }
            } catch (SQLException ignored) {}
        }
        // Oracle
        if (dbType == DatabaseType.ORACLE) {
            String sql = "SELECT TEXT FROM ALL_VIEWS WHERE OWNER = ? AND VIEW_NAME = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema.toUpperCase());
                ps.setString(2, view.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String def = rs.getString(1);
                        if (def != null)
                            return "CREATE OR REPLACE VIEW " + schema + "." + view + " AS\n" + def.trim() + ";";
                    }
                }
            } catch (SQLException ignored) {}
        }
        return "-- View DDL not available for " + view;
    }
}

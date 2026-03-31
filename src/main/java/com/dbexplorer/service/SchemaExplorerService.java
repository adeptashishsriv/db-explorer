package com.dbexplorer.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.dbexplorer.model.DatabaseType;

/**
 * Fetches schema metadata (schemas, tables, views, sequences, etc.) from a JDBC connection.
 */
public class SchemaExplorerService {

    public List<String> getSchemas(Connection conn, DatabaseType dbType) throws SQLException {
        List<String> schemas = new ArrayList<>();

        if (dbType == DatabaseType.SQLITE) {
            schemas.add("main");
            return schemas;
        }

        DatabaseMetaData meta = conn.getMetaData();
        if (dbType == DatabaseType.MYSQL) {
            try (ResultSet rs = meta.getCatalogs()) {
                while (rs.next()) schemas.add(rs.getString("TABLE_CAT"));
            }
        } else {
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) schemas.add(rs.getString("TABLE_SCHEM"));
            }
        }
        Collections.sort(schemas);
        return schemas;
    }

    public List<String> getTables(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        return getObjects(conn, dbType, schema, new String[]{"TABLE"});
    }

    public List<String> getViews(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        return getObjects(conn, dbType, schema, new String[]{"VIEW"});
    }

    public List<String> getColumns(Connection conn, DatabaseType dbType, String schema, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaPattern = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;

        try (ResultSet rs = meta.getColumns(catalog, schemaPattern, tableName, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                columns.add(name + " (" + type + (size > 0 ? "(" + size + ")" : "") + ")");
            }
        }
        return columns;
    }

    private List<String> getObjects(Connection conn, DatabaseType dbType, String schema,
                                     String[] types) throws SQLException {
        List<String> names = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaPattern = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;

        try (ResultSet rs = meta.getTables(catalog, schemaPattern, "%", types)) {
            while (rs.next()) names.add(rs.getString("TABLE_NAME"));
        }
        Collections.sort(names);
        return names;
    }

    public List<String> getMatViews(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = switch (dbType) {
            case POSTGRESQL -> "SELECT matviewname FROM pg_matviews WHERE schemaname = ? ORDER BY matviewname";
            case ORACLE     -> "SELECT mview_name FROM ALL_MVIEWS WHERE owner = ? ORDER BY mview_name";
            case SQLSERVER  -> "SELECT name FROM sys.views WHERE schema_id = SCHEMA_ID(?) AND OBJECTPROPERTY(object_id, 'IsIndexed') = 1 ORDER BY name";
            case MYSQL, SQLITE, DYNAMODB -> null;
            case GENERIC    -> "";  // sentinel: use DatabaseMetaData fallback
        };

        if (sql == null) return Collections.emptyList();

        if (sql.isEmpty()) {
            // Generic JDBC: try the "MATERIALIZED VIEW" table type
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"MATERIALIZED VIEW"})) {
                while (rs.next()) names.add(rs.getString("TABLE_NAME"));
            } catch (SQLException ignored) {}
            Collections.sort(names);
            return names;
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return names;
    }

    public List<String> getSequences(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = switch (dbType) {
            case POSTGRESQL -> "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?";
            case ORACLE -> "SELECT sequence_name FROM all_sequences WHERE sequence_owner = ?";
            case SQLSERVER -> "SELECT name FROM sys.sequences WHERE schema_id = SCHEMA_ID(?)";
            case MYSQL, DYNAMODB, SQLITE, GENERIC -> null;
        };
        if (sql == null) return names;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return names;
    }

    public List<String> getFunctions(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaPattern = (dbType == DatabaseType.MYSQL) ? null : schema;

        try (ResultSet rs = meta.getFunctions(catalog, schemaPattern, "%")) {
            while (rs.next()) names.add(rs.getString("FUNCTION_NAME"));
        } catch (SQLException ignored) {}
        Collections.sort(names);
        return names;
    }

    public List<String> getProcedures(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaPattern = (dbType == DatabaseType.MYSQL) ? null : schema;

        try (ResultSet rs = meta.getProcedures(catalog, schemaPattern, "%")) {
            while (rs.next()) names.add(rs.getString("PROCEDURE_NAME"));
        } catch (SQLException ignored) {}
        Collections.sort(names);
        return names;
    }

    public List<String> getIndexes(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaPattern = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;

        List<String> tables = getTables(conn, dbType, schema);
        for (String table : tables) {
            try (ResultSet rs = meta.getIndexInfo(catalog, schemaPattern, table, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName != null && !names.contains(indexName)) {
                        names.add(indexName);
                    }
                }
            } catch (SQLException ignored) {}
        }
        Collections.sort(names);
        return names;
    }

    // ── Diagram metadata ──────────────────────────────────────────────────────

    /** Returns primary key column names for a table. */
    public List<String> getPrimaryKeys(Connection conn, DatabaseType dbType,
                                       String schema, String table) throws SQLException {
        List<String> pks = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaArg = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schemaArg, table)) {
            while (rs.next()) pks.add(rs.getString("COLUMN_NAME"));
        }
        return pks;
    }

    /**
     * Returns imported foreign key relationships for a table.
     * Each entry: [fkTable, fkColumn, pkTable, pkColumn]
     */
    public List<String[]> getImportedForeignKeys(Connection conn, DatabaseType dbType,
                                                  String schema, String table) throws SQLException {
        List<String[]> fks = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaArg = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;
        try (ResultSet rs = meta.getImportedKeys(catalog, schemaArg, table)) {
            while (rs.next()) {
                fks.add(new String[]{
                    table,
                    rs.getString("FKCOLUMN_NAME"),
                    rs.getString("PKTABLE_NAME"),
                    rs.getString("PKCOLUMN_NAME")
                });
            }
        } catch (SQLException ignored) {}
        return fks;
    }

    /** Returns raw column metadata [name, type] for diagram display. */
    public List<String[]> getColumnDetails(Connection conn, DatabaseType dbType,
                                            String schema, String table) throws SQLException {
        List<String[]> cols = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaArg = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;
        try (ResultSet rs = meta.getColumns(catalog, schemaArg, table, null)) {
            while (rs.next()) {
                cols.add(new String[]{
                    rs.getString("COLUMN_NAME"),
                    rs.getString("TYPE_NAME")
                });
            }
        }
        return cols;
    }
}

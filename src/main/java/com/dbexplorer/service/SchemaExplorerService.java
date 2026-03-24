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
            schemas.add("main"); // SQLite has no real schemas; "main" is the default
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

    public List<String> getSequences(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = switch (dbType) {
            case POSTGRESQL -> "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?";
            case ORACLE -> "SELECT sequence_name FROM all_sequences WHERE sequence_owner = ?";
            case SQLSERVER -> "SELECT name FROM sys.sequences WHERE schema_id = SCHEMA_ID(?)";
            case MYSQL, DYNAMODB, SQLITE -> null;
        };
        if (sql == null) return names;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        } catch (SQLException ignored) {
            // Some DBs/versions may not support sequences
        }
        Collections.sort(names);
        return names;
    }

    public List<String> getIndexes(Connection conn, DatabaseType dbType, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = switch (dbType) {
            case POSTGRESQL -> "SELECT indexname FROM pg_indexes WHERE schemaname = ? ORDER BY indexname";
            case ORACLE -> "SELECT index_name FROM all_indexes WHERE owner = ? ORDER BY index_name";
            case SQLSERVER -> "SELECT i.name FROM sys.indexes i JOIN sys.tables t ON i.object_id = t.object_id "
                    + "JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = ? AND i.name IS NOT NULL ORDER BY i.name";
            case MYSQL, DYNAMODB, SQLITE -> null;
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
}

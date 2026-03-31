package com.dbexplorer.service;

import com.dbexplorer.model.DatabaseType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates table-level exports:
 *  - DDL    (CREATE TABLE for a single table)
 *  - INSERT statements streamed directly to a File
 *  - UPDATE SQL streamed directly to a File
 *  - CSV    streamed directly to a File
 *
 * INSERT and CSV write to a caller-supplied File so that arbitrarily large
 * tables never accumulate in heap.  A preview of the first PREVIEW_ROWS lines
 * is returned separately for display in the UI text area.
 *
 * All methods run on a background thread — never on the EDT.
 */
public class TableDataExportService {

    public static final int FETCH_SIZE   = loadFetchSize();
    public static final int PREVIEW_ROWS = 200;   // lines shown in the text area

    /** Reads a property from the filtered app.properties resource. Falls back to the given default. */
    private static String loadAppProperty(String key, String fallback) {
        try (InputStream is = TableDataExportService.class.getResourceAsStream("/app.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String v = props.getProperty(key);
                if (v != null && !v.isBlank()) return v;
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static int loadFetchSize() {
        try {
            return Integer.parseInt(loadAppProperty("export.fetch.size", "500"));
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    // ── DDL ───────────────────────────────────────────────────────────────────

    public String exportTableDdl(Connection conn, DatabaseType dbType,
                                  String schema, String table) throws SQLException {
        return new DdlExportService().exportTableDdl(conn, dbType, schema, table);
    }

    // ── INSERT — stream to file ───────────────────────────────────────────────

    /**
     * Streams INSERT statements to {@code outFile}.
     * Returns a StreamResult containing a preview string and the total row count.
     */
    public StreamResult exportInserts(Connection conn, DatabaseType dbType,
                                       String schema, String table,
                                       File outFile,
                                       ProgressCallback progress) throws Exception {
        String qualifiedTable = qualifiedName(dbType, schema, table);
        List<String> columns  = getColumnNames(conn, dbType, schema, table);
        String colList        = String.join(", ", columns);
        String header         = "-- INSERT export: " + qualifiedTable + "\n"
                              + "-- Generated: " + new java.util.Date() + "\n\n";

        StringBuilder preview = new StringBuilder(header);
        int rowCount  = 0;
        int previewRows = 0;

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8),
                64 * 1024)) {

            bw.write(header);

            try (Statement st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY,
                                                      ResultSet.CONCUR_READ_ONLY)) {
                st.setFetchSize(FETCH_SIZE);
                try (ResultSet rs = st.executeQuery("SELECT * FROM " + qualifiedTable)) {
                    int colCount = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        StringBuilder line = new StringBuilder();
                        line.append("INSERT INTO ").append(qualifiedTable)
                            .append(" (").append(colList).append(") VALUES (");
                        for (int i = 1; i <= colCount; i++) {
                            if (i > 1) line.append(", ");
                            line.append(sqlLiteral(rs, i));
                        }
                        line.append(");\n");

                        bw.write(line.toString());
                        rowCount++;

                        if (previewRows < PREVIEW_ROWS) {
                            preview.append(line);
                            previewRows++;
                        }
                        if (progress != null && rowCount % FETCH_SIZE == 0)
                            progress.onProgress(rowCount);
                    }
                }
            }
            String footer = "\n-- " + rowCount + " row(s) exported";
            bw.write(footer);
            if (previewRows < PREVIEW_ROWS) preview.append(footer);
            else preview.append("\n-- ... (").append(rowCount - PREVIEW_ROWS)
                        .append(" more rows in file)");
        }

        if (progress != null) progress.onProgress(rowCount);
        return new StreamResult(preview.toString(), rowCount, outFile);
    }

    // ── UPDATE — stream to file ───────────────────────────────────────────────

    public StreamResult exportUpdates(Connection conn, DatabaseType dbType,
                                       String schema, String table,
                                       File outFile,
                                       ProgressCallback progress) throws Exception {
        String qualifiedTable = qualifiedName(dbType, schema, table);
        List<String> pkCols   = getPkColumns(conn, dbType, schema, table);
        List<String> allCols  = getColumnNames(conn, dbType, schema, table);

        List<String> whereCols = pkCols.isEmpty() ? allCols : pkCols;
        List<String> setCols   = new ArrayList<>(allCols);
        setCols.removeAll(whereCols);
        if (setCols.isEmpty()) setCols = allCols;

        String header = "-- UPDATE export: " + qualifiedTable + "\n"
                      + "-- Generated: " + new java.util.Date() + "\n\n";

        StringBuilder preview = new StringBuilder(header);
        int rowCount = 0, previewRows = 0;

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8),
                64 * 1024)) {

            bw.write(header);

            try (Statement st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY,
                                                      ResultSet.CONCUR_READ_ONLY)) {
                st.setFetchSize(FETCH_SIZE);
                try (ResultSet rs = st.executeQuery("SELECT * FROM " + qualifiedTable)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    java.util.Map<String, Integer> colIdx = new java.util.LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++)
                        colIdx.put(meta.getColumnName(i).toLowerCase(), i);

                    while (rs.next()) {
                        StringBuilder line = new StringBuilder();
                        line.append("UPDATE ").append(qualifiedTable).append(" SET ");
                        boolean first = true;
                        for (String col : setCols) {
                            if (!first) line.append(", ");
                            Integer idx = colIdx.get(col.toLowerCase());
                            line.append(col).append(" = ")
                                .append(idx != null ? sqlLiteral(rs, idx) : "NULL");
                            first = false;
                        }
                        line.append(" WHERE ");
                        first = true;
                        for (String col : whereCols) {
                            if (!first) line.append(" AND ");
                            Integer idx = colIdx.get(col.toLowerCase());
                            line.append(col).append(" = ")
                                .append(idx != null ? sqlLiteral(rs, idx) : "NULL");
                            first = false;
                        }
                        line.append(";\n");

                        bw.write(line.toString());
                        rowCount++;

                        if (previewRows < PREVIEW_ROWS) {
                            preview.append(line);
                            previewRows++;
                        }
                        if (progress != null && rowCount % FETCH_SIZE == 0)
                            progress.onProgress(rowCount);
                    }
                }
            }
            String footer = "\n-- " + rowCount + " row(s) exported";
            bw.write(footer);
            if (previewRows < PREVIEW_ROWS) preview.append(footer);
            else preview.append("\n-- ... (").append(rowCount - PREVIEW_ROWS)
                        .append(" more rows in file)");
        }

        if (progress != null) progress.onProgress(rowCount);
        return new StreamResult(preview.toString(), rowCount, outFile);
    }

    // ── CSV — stream to file ──────────────────────────────────────────────────

    public StreamResult exportCsv(Connection conn, DatabaseType dbType,
                                   String schema, String table,
                                   File outFile,
                                   ProgressCallback progress) throws Exception {
        String qualifiedTable = qualifiedName(dbType, schema, table);
        StringBuilder preview = new StringBuilder();
        int rowCount = 0, previewRows = 0;

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8),
                64 * 1024)) {

            try (Statement st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY,
                                                      ResultSet.CONCUR_READ_ONLY)) {
                st.setFetchSize(FETCH_SIZE);
                try (ResultSet rs = st.executeQuery("SELECT * FROM " + qualifiedTable)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    // Header
                    StringBuilder headerLine = new StringBuilder();
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) headerLine.append(",");
                        headerLine.append(csvQuote(meta.getColumnLabel(i)));
                    }
                    headerLine.append("\n");
                    bw.write(headerLine.toString());
                    preview.append(headerLine);

                    // Data
                    while (rs.next()) {
                        StringBuilder line = new StringBuilder();
                        for (int i = 1; i <= colCount; i++) {
                            if (i > 1) line.append(",");
                            String val = rs.getString(i);
                            line.append(val == null ? "" : csvQuote(val));
                        }
                        line.append("\n");

                        bw.write(line.toString());
                        rowCount++;

                        if (previewRows < PREVIEW_ROWS) {
                            preview.append(line);
                            previewRows++;
                        }
                        if (progress != null && rowCount % FETCH_SIZE == 0)
                            progress.onProgress(rowCount);
                    }
                }
            }
            if (rowCount > PREVIEW_ROWS)
                preview.append("\n# ... (").append(rowCount - PREVIEW_ROWS)
                       .append(" more rows in file)");
        }

        if (progress != null) progress.onProgress(rowCount);
        return new StreamResult(preview.toString(), rowCount, outFile);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String qualifiedName(DatabaseType dbType, String schema, String table) {
        if (dbType == DatabaseType.MYSQL)
            return "`" + schema + "`.`" + table + "`";
        if (dbType == DatabaseType.SQLITE || schema == null || schema.isBlank())
            return table;
        return schema + "." + table;
    }

    private List<String> getColumnNames(Connection conn, DatabaseType dbType,
                                         String schema, String table) throws SQLException {
        List<String> cols = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog   = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaArg = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;
        try (ResultSet rs = meta.getColumns(catalog, schemaArg, table, null)) {
            while (rs.next()) cols.add(rs.getString("COLUMN_NAME"));
        }
        return cols;
    }

    private List<String> getPkColumns(Connection conn, DatabaseType dbType,
                                       String schema, String table) throws SQLException {
        java.util.TreeMap<Short, String> ordered = new java.util.TreeMap<>();
        DatabaseMetaData meta = conn.getMetaData();
        String catalog   = (dbType == DatabaseType.MYSQL) ? schema : null;
        String schemaArg = (dbType == DatabaseType.MYSQL || dbType == DatabaseType.SQLITE) ? null : schema;
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schemaArg, table)) {
            while (rs.next()) ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
        }
        return new ArrayList<>(ordered.values());
    }

    private String sqlLiteral(ResultSet rs, int col) throws SQLException {
        String val = rs.getString(col);
        if (rs.wasNull() || val == null) return "NULL";
        int sqlType = rs.getMetaData().getColumnType(col);
        if (isNumericType(sqlType)) return val;
        return "'" + val.replace("'", "''") + "'";
    }

    private boolean isNumericType(int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                 Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL,
                 Types.BIT, Types.BOOLEAN -> true;
            default -> false;
        };
    }

    private String csvQuote(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // ── Result + callback types ───────────────────────────────────────────────

    /**
     * Returned by streaming export methods.
     * {@code preview} is the first PREVIEW_ROWS lines for display.
     * {@code file}    is the complete output file — use for Save.
     */
    public record StreamResult(String preview, int rowCount, File file) {}

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int rowsProcessed);
    }
}

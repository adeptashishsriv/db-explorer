package com.dbexplorer.model;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds an open ResultSet and fetches rows in pages.
 * Must be closed when no longer needed to release DB resources.
 *
 * Performance notes:
 * - Uses getString() instead of getObject() to avoid driver-side type materialisation
 *   (avoids BLOB/CLOB reads, BigDecimal allocations, etc.)
 * - Rows are stored as List<String[]> (primitive arrays) rather than List<List<Object>>
 *   to reduce GC pressure on large result sets.
 * - Column metadata (max observed char width per column) is tracked here so the UI
 *   can size columns without calling prepareRenderer on the EDT.
 */
public class LazyQueryResult implements AutoCloseable {

    public static final int DEFAULT_FETCH_SIZE = loadFetchSize();
    public static final int MAX_ROWS = loadMaxRows();

    /** Reads a property from the filtered app.properties resource. Falls back to the given default. */
    private static String loadAppProperty(String key, String fallback) {
        try (InputStream is = LazyQueryResult.class.getResourceAsStream("/app.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String v = props.getProperty(key);
                if (v != null && !v.isBlank()) return v;
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static int loadMaxRows() {
        try {
            return Integer.parseInt(loadAppProperty("query.max.rows", "10000"));
        } catch (NumberFormatException e) {
            return 10000;
        }
    }

    private static int loadFetchSize() {
        try {
            return Integer.parseInt(loadAppProperty("query.fetch.size", "500"));
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    private final List<String>   columns;
    private final int[]          columnTypes;   // java.sql.Types values
    private final Statement      statement;
    private final ResultSet      resultSet;
    private final int            columnCount;
    private final long           executionTimeMs;

    // Accumulated rows — NOT stored here after being handed to the UI.
    // We only track the count for status display purposes.
    private int fetchedRowCount = 0;

    // First page is stored temporarily until ResultPanel reads it, then cleared.
    private List<String[]> firstPage = null;

    // Max observed string length per column — used for fast column sizing
    private final int[]  maxColWidth;

    private boolean exhausted = false;
    private boolean truncated = false;

    public LazyQueryResult(Statement statement, ResultSet resultSet, long executionTimeMs)
            throws SQLException {
        this.statement       = statement;
        this.resultSet       = resultSet;
        this.executionTimeMs = executionTimeMs;

        ResultSetMetaData meta = resultSet.getMetaData();
        this.columnCount = meta.getColumnCount();

        List<String> cols = new ArrayList<>(columnCount);
        this.columnTypes  = new int[columnCount];
        this.maxColWidth  = new int[columnCount];

        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            cols.add(label);
            columnTypes[i - 1] = meta.getColumnType(i);
            maxColWidth[i - 1] = label.length(); // seed with header width
        }
        this.columns = Collections.unmodifiableList(cols);
    }

    public List<String>  getColumns()         { return columns; }
    public int[]         getColumnTypes()      { return columnTypes; }
    public long          getExecutionTimeMs()  { return executionTimeMs; }
    public boolean       isExhausted()         { return exhausted; }
    public boolean       isTruncated()         { return truncated; }
    public int           getFetchedRowCount()  { return fetchedRowCount; }
    public int[]         getMaxColWidths()     { return maxColWidth; }

    /**
     * Returns the first page of rows and clears the reference so it can be GC'd.
     * Returns empty list if already consumed.
     */
    public List<String[]> takeFirstPage() {
        List<String[]> page = firstPage;
        firstPage = null; // release reference immediately
        return page != null ? page : List.of();
    }

    /**
     * Fetch the next page of rows (up to fetchSize).
     * Returns only the newly fetched rows.
     * Called on a background thread — never on the EDT.
     * Stops if total fetched rows would exceed MAX_ROWS to prevent memory issues.
     */
    public List<String[]> fetchNextPage(int fetchSize) throws SQLException {
        if (exhausted || fetchedRowCount >= MAX_ROWS) return List.of();

        List<String[]> page = new ArrayList<>(Math.min(fetchSize, MAX_ROWS - fetchedRowCount));
        int count = 0;

        while (count < fetchSize && resultSet.next() && fetchedRowCount + count < MAX_ROWS) {
            String[] row = new String[columnCount];
            for (int i = 1; i <= columnCount; i++) {
                String val = resultSet.getString(i);
                if (val == null) val = "";
                row[i - 1] = val;
                // Track max width for column sizing (cap at 80 chars to avoid huge columns)
                int len = Math.min(val.length(), 80);
                if (len > maxColWidth[i - 1]) maxColWidth[i - 1] = len;
            }
            page.add(row);
            count++;
        }

        if (count < fetchSize || fetchedRowCount + page.size() >= MAX_ROWS) exhausted = true;
        fetchedRowCount += page.size();
        if (fetchedRowCount >= MAX_ROWS) truncated = true;
        if (firstPage == null) firstPage = page; // store only the first page
        return page;
    }

    /** Fetch next page using the default fetch size. */
    public List<String[]> fetchNextPage() throws SQLException {
        return fetchNextPage(DEFAULT_FETCH_SIZE);
    }

    @Override
    public void close() {
        try { resultSet.close(); } catch (Exception ignored) {}
        try { statement.close(); } catch (Exception ignored) {}
    }

    /**
     * Clears internal data structures to aid garbage collection.
     * Call this when done with the result and before dropping references.
     */
    public void clearData() {
        if (firstPage != null) {
            firstPage.clear();
            firstPage = null;
        }
    }
}

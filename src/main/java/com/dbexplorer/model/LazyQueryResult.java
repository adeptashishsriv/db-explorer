package com.dbexplorer.model;

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
 */
public class LazyQueryResult implements AutoCloseable {

    public static final int DEFAULT_FETCH_SIZE = 200;

    private final List<String> columns;
    private final Statement statement;
    private final ResultSet resultSet;
    private final int columnCount;
    private final long executionTimeMs;

    private final List<List<Object>> fetchedRows = new ArrayList<>();
    private boolean exhausted = false;

    public LazyQueryResult(Statement statement, ResultSet resultSet, long executionTimeMs)
            throws SQLException {
        this.statement = statement;
        this.resultSet = resultSet;
        this.executionTimeMs = executionTimeMs;

        ResultSetMetaData meta = resultSet.getMetaData();
        this.columnCount = meta.getColumnCount();

        List<String> cols = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            cols.add(meta.getColumnLabel(i));
        }
        this.columns = Collections.unmodifiableList(cols);
    }

    public List<String> getColumns() { return columns; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public boolean isExhausted() { return exhausted; }
    public int getFetchedRowCount() { return fetchedRows.size(); }

    /**
     * Fetch the next page of rows (up to fetchSize).
     * Returns the newly fetched rows (empty list if exhausted).
     */
    public List<List<Object>> fetchNextPage(int fetchSize) throws SQLException {
        if (exhausted) return List.of();

        List<List<Object>> page = new ArrayList<>(fetchSize);
        int count = 0;
        while (count < fetchSize && resultSet.next()) {
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(resultSet.getObject(i));
            }
            page.add(row);
            count++;
        }

        if (count < fetchSize) {
            exhausted = true;
        }

        fetchedRows.addAll(page);
        return page;
    }

    /**
     * Fetch the next page using the default fetch size.
     */
    public List<List<Object>> fetchNextPage() throws SQLException {
        return fetchNextPage(DEFAULT_FETCH_SIZE);
    }

    @Override
    public void close() {
        try { resultSet.close(); } catch (Exception ignored) {}
        try { statement.close(); } catch (Exception ignored) {}
    }
}

package com.dbexplorer.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.dbexplorer.model.DatabaseType;
import com.dbexplorer.model.LazyQueryResult;
import com.dbexplorer.model.QueryResult;

public class QueryExecutor {

    /** Query timeout in seconds. Throws SQLTimeoutException if the DB doesn't respond in time. */
    public static final int QUERY_TIMEOUT_SECONDS = 180;

    private final AtomicReference<Statement> activeStatement = new AtomicReference<>();

    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(50),
            r -> {
                Thread t = new Thread(r, "query-exec");
                t.setDaemon(true);
                return t;
            }
    );

    /**
     * Execute SQL asynchronously. For SELECT queries, returns a LazyQueryResult
     * via onLazyResult. For non-SELECT, returns a QueryResult via onSuccess.
     *
     * Performance: the first page of rows is fetched on the background thread
     * before handing off to the UI, so the table is populated immediately on
     * the first EDT paint rather than requiring a second round-trip.
     */
    public Future<?> executeAsync(Connection connection, String sql,
                                  Consumer<LazyQueryResult> onLazyResult,
                                  Consumer<QueryResult> onSuccess,
                                  Consumer<SQLException> onError) {
        return executor.submit(() -> {
            Statement stmt = null;
            try {
                long start = System.currentTimeMillis();
                // TYPE_FORWARD_ONLY + CONCUR_READ_ONLY lets the driver stream rows
                stmt = connection.createStatement(
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                stmt.setFetchSize(LazyQueryResult.DEFAULT_FETCH_SIZE);
                stmt.setFetchDirection(ResultSet.FETCH_FORWARD);
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                activeStatement.set(stmt);
                boolean hasResultSet = stmt.execute(sql);
                long elapsed = System.currentTimeMillis() - start;

                if (hasResultSet) {
                    ResultSet rs = stmt.getResultSet();
                    LazyQueryResult lazy = new LazyQueryResult(stmt, rs, elapsed);
                    // Pre-fetch first page on this background thread so the EDT
                    // can render rows immediately without a second async hop.
                    // The page is stored in lazy.takeFirstPage() for ResultPanel to consume.
                    lazy.fetchNextPage();
                    onLazyResult.accept(lazy);
                } else {
                    int affected = stmt.getUpdateCount();
                    stmt.close();
                    onSuccess.accept(new QueryResult(List.of(), List.of(), elapsed, affected, false));
                }
            } catch (SQLException e) {
                onError.accept(e);
            } finally {
                activeStatement.set(null);
            }
        });
    }

    /**
     * Fetch the next page from a LazyQueryResult asynchronously.
     */
    public Future<?> fetchNextPageAsync(LazyQueryResult lazyResult,
                                        Consumer<List<String[]>> onPage,
                                        Consumer<SQLException> onError) {
        return executor.submit(() -> {
            try {
                List<String[]> page = lazyResult.fetchNextPage();
                onPage.accept(page);
            } catch (SQLException e) {
                onError.accept(e);
            }
        });
    }

    /**
     * Execute EXPLAIN/execution plan asynchronously.
     * Generates the appropriate EXPLAIN syntax per database type.
     */
    public Future<?> explainAsync(Connection connection, String sql, DatabaseType dbType,
                                  Consumer<String> onSuccess, Consumer<SQLException> onError) {
        return executor.submit(() -> {
            try {
                String plan = executeExplain(connection, sql, dbType);
                onSuccess.accept(plan);
            } catch (SQLException e) {
                onError.accept(e);
            }
        });
    }

    private String executeExplain(Connection connection, String sql, DatabaseType dbType)
            throws SQLException {
        StringBuilder plan = new StringBuilder();

        switch (dbType) {
            case POSTGRESQL -> {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("EXPLAIN ANALYZE " + sql)) {
                    while (rs.next()) {
                        plan.append(rs.getString(1)).append("\n");
                    }
                }
            }
            case MYSQL -> {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    // Pipe-delimited format that ExplainPlanPanel can parse as a table
                    StringBuilder header = new StringBuilder("|");
                    StringBuilder sep    = new StringBuilder("+");
                    for (int i = 1; i <= cols; i++) {
                        String label = meta.getColumnLabel(i);
                        header.append(" ").append(label).append(" |");
                        sep.append("-".repeat(label.length() + 2)).append("+");
                    }
                    plan.append(sep).append("\n")
                        .append(header).append("\n")
                        .append(sep).append("\n");
                    while (rs.next()) {
                        plan.append("|");
                        for (int i = 1; i <= cols; i++) {
                            String val = rs.getString(i);
                            plan.append(" ").append(val == null ? "NULL" : val).append(" |");
                        }
                        plan.append("\n");
                    }
                    plan.append(sep).append("\n");
                }
            }
            case ORACLE -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("EXPLAIN PLAN FOR " + sql);
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT PLAN_TABLE_OUTPUT FROM TABLE(DBMS_XPLAN.DISPLAY())")) {
                        while (rs.next()) {
                            plan.append(rs.getString(1)).append("\n");
                        }
                    }
                }
            }
            case SQLSERVER -> {
                // SET SHOWPLAN_TEXT ON causes SQL Server to return the execution
                // plan as result sets instead of executing the query.
                // Each result set contains one column with plan text rows.
                // Must use a fresh statement and iterate all result sets.
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET SHOWPLAN_TEXT ON");
                }
                try (Statement stmt = connection.createStatement()) {
                    boolean hasResults = stmt.execute(sql);
                    while (hasResults) {
                        try (ResultSet rs = stmt.getResultSet()) {
                            while (rs.next()) {
                                plan.append(rs.getString(1)).append("\n");
                            }
                        }
                        hasResults = stmt.getMoreResults();
                    }
                } finally {
                    // Always turn SHOWPLAN_TEXT off, even if the above throws
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("SET SHOWPLAN_TEXT OFF");
                    }
                }
            }
            case DYNAMODB -> {
                plan.append("Explain Plan is not supported for DynamoDB.");
            }
        }

        return plan.toString();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Cancels the currently executing JDBC Statement, if any.
     * Safe to call from any thread. No-op if no statement is active.
     */
    public void cancelCurrent() {
        Statement stmt = activeStatement.get();
        if (stmt != null) {
            try { stmt.cancel(); } catch (SQLException ignored) {}
        }
    }
}

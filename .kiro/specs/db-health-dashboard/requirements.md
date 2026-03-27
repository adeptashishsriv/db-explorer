# Requirements Document

## Introduction

The Database Health Dashboard is an opt-in, read-only panel within DB Explorer that surfaces
vital statistics about the connected database server and the JVM hosting the application. It
runs entirely on a dedicated background thread using its own isolated JDBC connection
(the Health_Connection), so it never competes with user queries or schema exploration.
Statistics are polled on a configurable timer and displayed in a structured Swing panel.

All query, transaction, session, and data-volume statistics are pulled directly from the
database server's own system views and tables via the Health_Connection. This means the
dashboard reflects activity from **all** clients connected to the database — not just DB
Explorer — giving a true, authoritative picture of server health.

The dashboard covers eight categories: connection health, database metadata, server-side
activity statistics (active sessions, running queries, query throughput, transactions, cache
hit ratios, lock/deadlock stats, slow queries, table/index scans, database size), live
connections (all currently active sessions on the server), SQL warning tracking, and
JVM-level resource usage.

---

## Glossary

- **Dashboard**: The Database Health Dashboard panel described in this document.
- **Health_Collector**: The background service responsible for polling statistics on a
  scheduled timer using the Health_Connection.
- **Health_Connection**: The dedicated `java.sql.Connection` instance opened exclusively for
  the Health_Collector; it is separate from all connections managed by `ConnectionManager`.
- **Stats_Model**: The in-memory data structure that holds the latest snapshot of all
  collected statistics and is read by the UI on the EDT.
- **Dashboard_Panel**: The Swing `JPanel` that renders the Stats_Model and is embedded in the
  main application window.
- **System_Query**: A SQL statement executed against the database's own system views or
  tables (e.g. `pg_stat_activity`, `performance_schema`, `v$session`) to retrieve server-side
  metrics.
- **Poll_Interval**: The user-configurable period (in seconds, range 5–30) between successive
  Health_Collector polling cycles.
- **ConnectionInfo**: The existing `com.dbexplorer.model.ConnectionInfo` model class.
- **ConnectionManager**: The existing `com.dbexplorer.service.ConnectionManager` class.
- **DatabaseType**: The existing `com.dbexplorer.model.DatabaseType` enum.

---

## Requirements

### Requirement 1: Opt-In Activation

**User Story:** As a developer, I want to enable or disable the health dashboard independently
of my query workflow, so that the feature has zero impact when I do not need it.

#### Acceptance Criteria

1. THE Dashboard SHALL be disabled by default when the application starts.
2. WHEN the user enables the Dashboard for a connection, THE Dashboard_Panel SHALL become
   visible and the Health_Collector SHALL start polling.
3. WHEN the user disables the Dashboard, THE Health_Collector SHALL stop polling within one
   Poll_Interval and the Health_Connection SHALL be closed.
4. WHILE the Dashboard is disabled, THE Health_Collector SHALL not open any JDBC connections
   and SHALL not execute any SQL statements.
5. THE Dashboard SHALL persist its enabled/disabled state per connection across application
   restarts using the existing `~/.dbexplorer/` configuration directory.

---

### Requirement 2: Isolated Health Connection

**User Story:** As a developer, I want the dashboard to use its own dedicated connection, so
that health checks and system queries never interfere with my active queries.

#### Acceptance Criteria

1. WHEN the Dashboard is enabled for a connection, THE Health_Collector SHALL open a
   Health_Connection using the same `ConnectionInfo` credentials but as a separate
   `java.sql.Connection` instance not registered in `ConnectionManager`.
2. THE Health_Connection SHALL be used exclusively by the Health_Collector and SHALL NOT be
   shared with `QueryExecutor` or any other component.
3. WHEN the Health_Connection becomes invalid (as detected by `Connection.isValid(2)`), THE
   Health_Collector SHALL attempt to re-establish the Health_Connection up to 3 times before
   marking the connection status as `FAILED`.
4. WHEN the application shuts down, THE Health_Collector SHALL close the Health_Connection
   before the JVM exits.
5. IF the Health_Connection cannot be established after 3 attempts, THEN THE Dashboard_Panel
   SHALL display a `FAILED` status and the Health_Collector SHALL cease polling until the user
   manually re-enables the Dashboard.

---

### Requirement 3: Configurable Poll Interval

**User Story:** As a developer, I want to configure how often the dashboard refreshes, so that
I can balance freshness against background overhead.

#### Acceptance Criteria

1. THE Dashboard SHALL support a Poll_Interval configurable by the user in the range of 5 to
   30 seconds inclusive.
2. THE Dashboard SHALL use a default Poll_Interval of 10 seconds when no user preference has
   been saved.
3. WHEN the user changes the Poll_Interval, THE Health_Collector SHALL apply the new interval
   starting from the next scheduled poll without restarting the Health_Connection.
4. THE Health_Collector SHALL execute each polling cycle on a dedicated daemon thread that is
   separate from the `QueryExecutor` thread pool.
5. WHEN a polling cycle takes longer than the Poll_Interval, THE Health_Collector SHALL skip
   the overlapping cycle rather than queuing a backlog of pending polls.

---

### Requirement 4: Connection Health Statistics

**User Story:** As a developer, I want to see the live status of my database connection, so
that I can quickly detect connectivity problems.

#### Acceptance Criteria

1. WHEN a polling cycle runs, THE Health_Collector SHALL call `Connection.isValid(2)` on the
   Health_Connection and record the boolean result in the Stats_Model.
2. WHEN a polling cycle runs, THE Health_Collector SHALL record the timestamp of the last
   successful `isValid` check in the Stats_Model as an ISO-8601 string.
3. THE Health_Collector SHALL record the cumulative count of Health_Connection reconnect
   attempts in the Stats_Model.
4. THE Dashboard_Panel SHALL display the connection validity status as a colour-coded
   indicator: green for valid, red for invalid or FAILED.
5. THE Dashboard_Panel SHALL display the last-successful-check timestamp and the reconnect
   attempt count.

---

### Requirement 5: Database Metadata

**User Story:** As a developer, I want to see static database and driver information, so that
I can confirm which server version and driver I am connected to.

#### Acceptance Criteria

1. WHEN the Health_Connection is first established, THE Health_Collector SHALL retrieve the
   following fields from `DatabaseMetaData` and store them in the Stats_Model: database
   product name, database product version, driver name, driver version, and maximum number of
   connections reported by the server.
2. THE Health_Collector SHALL retrieve the set of supported JDBC features from
   `DatabaseMetaData` (transactions, savepoints, batch updates, stored procedures) and store
   them as boolean flags in the Stats_Model.
3. THE Dashboard_Panel SHALL display all metadata fields retrieved under Acceptance Criteria 1
   and 2 in a read-only table.
4. THE Health_Collector SHALL refresh database metadata once per session (on first connection)
   rather than on every poll cycle, because metadata is static.

---

### Requirement 6: Server-Side Activity Statistics

**User Story:** As a developer, I want to see live server-wide activity metrics pulled
directly from the database's own system views, so that I can understand the full load on the
server from all connected clients.

#### Acceptance Criteria

1. WHEN a polling cycle runs, THE Health_Collector SHALL detect the `DatabaseType` of the
   active connection and execute the appropriate System_Queries for that database engine.
2. THE Health_Collector SHALL collect the following metrics where supported by the database
   engine:
   - Active connections / sessions count (all clients)
   - Queries currently running with their duration
   - Total queries executed (cumulative from DB start or last reset)
   - Transaction commits and rollbacks (server-wide)
   - Cache hit ratio (buffer pool / shared buffers)
   - Lock waits and deadlock count
   - Table scan and index scan counts
   - Slow queries (from the DB's own slow-query stats or log views)
   - Database size on disk
3. FOR PostgreSQL, THE Health_Collector SHALL query: `pg_stat_activity`,
   `pg_stat_database`, `pg_stat_user_tables`, and `pg_stat_bgwriter`.
4. FOR MySQL/MariaDB, THE Health_Collector SHALL query: `information_schema`,
   `performance_schema` tables, `SHOW GLOBAL STATUS`, and `SHOW PROCESSLIST`.
5. FOR Oracle, THE Health_Collector SHALL query: `v$session`, `v$sql`, `v$sysstat`,
   and `v$waitstat`.
6. FOR SQL Server, THE Health_Collector SHALL query: `sys.dm_exec_sessions`,
   `sys.dm_exec_query_stats`, and `sys.dm_os_wait_stats`.
7. FOR SQLite, THE Health_Collector SHALL collect available metrics via PRAGMA statements
   (e.g. `PRAGMA page_count`, `PRAGMA page_size`, `PRAGMA cache_size`); metrics not
   supported by SQLite SHALL be omitted from the display.
8. FOR DynamoDB, THE Health_Collector SHALL surface SDK-level or CloudWatch metrics where
   accessible; metrics not available SHALL be omitted from the display.
9. WHEN a metric is not supported by the connected database engine, THE Dashboard_Panel SHALL
   omit that metric's row rather than displaying an error or a zero placeholder.
10. THE Dashboard_Panel SHALL display all collected server-side metrics in a dedicated
    "Server Activity" section with per-metric labels and values.
11. WHEN the connected `DatabaseType` is `GENERIC` or any value not in the explicitly
    supported set (PostgreSQL, MySQL, Oracle, SQL Server, SQLite, DynamoDB), THE
    Health_Collector SHALL use the **Generic JDBC Fallback** path: `collectServerStats()`
    SHALL return `ServerStats.empty()` (all server-side fields null) and SHALL still collect
    connection health, database metadata, SQL warnings, and JVM stats via standard JDBC APIs
    with no system views.
12. WHEN the Generic JDBC Fallback path is active, THE Dashboard_Panel SHALL display the
    following message in the "Server Activity" section instead of hiding the section entirely:
    *"Server Activity statistics are not available for this database type. Connection health,
    metadata, warnings, and JVM stats are still collected."*

---

### Requirement 7: SQLWarning Tracking

**User Story:** As a developer, I want to see SQL warnings raised by the server, so that I
can catch non-fatal issues that would otherwise be silent.

#### Acceptance Criteria

1. WHEN a polling cycle runs, THE Health_Collector SHALL call `Connection.getWarnings()` on
   the Health_Connection and append any new `SQLWarning` entries to a capped warning log
   (maximum 100 entries) in the Stats_Model.
2. THE Dashboard_Panel SHALL display the 10 most recent SQLWarning messages with their
   SQLState codes and timestamps.
3. WHEN the warning log reaches 100 entries, THE Health_Collector SHALL discard the oldest
   entry before adding a new one (circular buffer behaviour).

---

### Requirement 8: JVM Resource Statistics

**User Story:** As a developer, I want to see JVM memory and thread metrics, so that I can
detect memory pressure or thread leaks caused by database activity.

#### Acceptance Criteria

1. WHEN a polling cycle runs, THE Health_Collector SHALL read heap memory used and heap memory
   maximum from `java.lang.management.MemoryMXBean` and store both values in the Stats_Model.
2. WHEN a polling cycle runs, THE Health_Collector SHALL read the live thread count from
   `java.lang.management.ThreadMXBean` and store it in the Stats_Model.
3. WHEN a polling cycle runs, THE Health_Collector SHALL read the collection count and
   collection time from all available `java.lang.management.GarbageCollectorMXBean` instances
   and store the aggregate values in the Stats_Model.
4. THE Dashboard_Panel SHALL display heap used, heap max, heap used as a percentage of max,
   live thread count, total GC collection count, and total GC time in milliseconds.
5. THE Dashboard_Panel SHALL render heap usage as a progress bar that turns amber when heap
   used exceeds 70% of heap max and red when it exceeds 90%.

---

### Requirement 10: Live Connections

**User Story:** As a developer, I want to see all currently active connections to the database
server, so that I can understand who and what is connected alongside my own session.

#### Acceptance Criteria

1. WHEN a polling cycle runs, THE Health_Collector SHALL call `collectLiveConnections(conn,
   dbType)` and store the resulting list of `LiveConnection` records in `ServerStats`.
2. FOR PostgreSQL, THE Health_Collector SHALL query `pg_stat_activity` and map the following
   columns to `LiveConnection` fields: `pid` → connectionId, `usename` → username,
   `client_addr || ':' || client_port` → host, `state` → state,
   `left(query, 80)` → currentQuery, `extract(epoch from (now()-query_start))*1000` →
   durationMs.
3. FOR MySQL/MariaDB, THE Health_Collector SHALL query `performance_schema.processlist` (or
   fall back to `SHOW PROCESSLIST`) and map: `Id` → connectionId, `User` → username,
   `Host` → host, `Command` → state, `Info` (truncated to 80 chars) → currentQuery,
   `Time * 1000` → durationMs.
4. FOR Oracle, THE Health_Collector SHALL query `v$session` and map: `SID || ',' || SERIAL#`
   → connectionId, `USERNAME` → username, `MACHINE` → host, `STATUS` → state,
   `PROGRAM` → currentQuery, `(SYSDATE - LOGON_TIME) * 86400000` → durationMs.
5. FOR SQL Server, THE Health_Collector SHALL join `sys.dm_exec_sessions` with
   `sys.dm_exec_requests` and map: `session_id` → connectionId, `login_name` → username,
   `host_name` → host, `status` → state, `sql_text` (from `sys.dm_exec_sql_text`,
   truncated to 80 chars) → currentQuery, `elapsed_time_ms` → durationMs.
6. FOR SQLite, THE Health_Collector SHALL return a single `LiveConnection` row populated from
   `DatabaseMetaData` (URL and username) with state `"active"` and a note field set to
   `"SQLite is an embedded database; only one connection exists"`.
7. FOR DynamoDB, THE Health_Collector SHALL return a single `LiveConnection` row with
   connectionId `"N/A"`, username from `DatabaseMetaData.getUserName()`, host from
   `DatabaseMetaData.getURL()`, state `"serverless"`, currentQuery `null`, and a note field
   set to `"DynamoDB is serverless; connection listing is not applicable"`.
8. WHEN the connected `DatabaseType` is `GENERIC` or any value not in the explicitly
   supported set, THE Health_Collector SHALL return a single `LiveConnection` row populated
   from `DatabaseMetaData` (URL and username) with state `"active"`, currentQuery `null`,
   and a note field set to `"Full connection list not available for this database type"`.
9. THE Dashboard_Panel SHALL display the live connections list in a `JTable` within the
   "Live Connections" section with columns: Connection ID, Username, Host, State, Current
   Query, Duration.
10. WHEN a column value is not available for the connected engine, THE Dashboard_Panel SHALL
    display `"—"` in that cell.
11. THE Dashboard_Panel SHALL display a row count label showing `"X active connection(s)"`
    above or below the connections table.
12. THE live connections table SHALL refresh on every poll cycle.
13. THE Dashboard_Panel SHALL visually highlight (bold font or accent colour) the row
    corresponding to the Health_Connection's own session, so the user can identify which
    connection belongs to DB Explorer.

---

### Requirement 11: Dashboard UI Layout

**User Story:** As a developer, I want the dashboard to be clearly organised and respect the
application theme, so that it integrates naturally with the rest of DB Explorer.

#### Acceptance Criteria

1. THE Dashboard_Panel SHALL be accessible from the main application window via a dedicated
   toolbar button or menu item without requiring a separate dialog.
2. THE Dashboard_Panel SHALL organise statistics into labelled sections corresponding to the
   categories defined in Requirements 4–10.
3. THE Dashboard_Panel SHALL display a "Last refreshed" timestamp showing when the most recent
   polling cycle completed.
4. THE Dashboard_Panel SHALL apply the active FlatLaf theme colours (background, foreground,
   accent) so that it visually matches the rest of the application.
5. WHEN the active connection changes in the main window, THE Dashboard_Panel SHALL update to
   reflect statistics for the newly selected connection.
6. THE Dashboard_Panel SHALL display all numeric values with locale-appropriate formatting
   (thousands separators).

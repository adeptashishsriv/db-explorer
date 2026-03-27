# Implementation Plan: Database Health Dashboard

## Overview

Implement the Database Health Dashboard as a new `com.dbexplorer.health` package plus a
`DashboardPanel` UI component, integrated into `MainFrame`. All server-side metrics are
collected via a dedicated `HealthCollector` background service using its own isolated JDBC
connection. The implementation language is Java 17, consistent with the rest of the project.

## Tasks

- [x] 1. Add jqwik dependency and create the `com.dbexplorer.health` package skeleton
  - Add `net.jqwik:jqwik:1.8.4` and `org.junit.jupiter:junit-jupiter:5.10.2` to `pom.xml`
    under `<scope>test</scope>`; add `maven-surefire-plugin` configured for JUnit Platform
  - Create `src/main/java/com/dbexplorer/health/` directory (package placeholder)
  - Create `src/test/java/com/dbexplorer/health/` directory (test placeholder)
  - _Requirements: 1.1_

- [x] 2. Implement data model types in `com.dbexplorer.health`
  - [x] 2.1 Create `ConnectionStatus` enum with values `VALID`, `INVALID`, `FAILED`
    - File: `src/main/java/com/dbexplorer/health/ConnectionStatus.java`
    - _Requirements: 4.4, 2.3, 2.5_

  - [x] 2.2 Create `DbMetadata` record
    - Fields: `productName`, `productVersion`, `driverName`, `driverVersion`,
      `maxConnections`, `supportsTransactions`, `supportsSavepoints`,
      `supportsBatchUpdates`, `supportsStoredProcedures`
    - File: `src/main/java/com/dbexplorer/health/DbMetadata.java`
    - _Requirements: 5.1, 5.2_

  - [x] 2.3 Create `ActiveQuery` record
    - Fields: `pid`, `state`, `queryText`, `durationMs`
    - File: `src/main/java/com/dbexplorer/health/ActiveQuery.java`
    - _Requirements: 6.2_

  - [x] 2.4 Create `LiveConnection` record
    - Fields: `connectionId`, `username`, `host`, `state`, `currentQuery`, `durationMs`
      (nullable `Long`), `isHealthConn`, `note`
    - File: `src/main/java/com/dbexplorer/health/LiveConnection.java`
    - _Requirements: 10.1, 10.9, 10.10, 10.13_

  - [x] 2.5 Create `ServerStats` record
    - All numeric fields nullable (`Integer`, `Long`, `Double`) to allow omission of
      unsupported metrics; includes `List<ActiveQuery> activeQueries` and
      `List<LiveConnection> liveConnections`
    - Add `static ServerStats empty()` factory returning all-null instance with empty lists
    - File: `src/main/java/com/dbexplorer/health/ServerStats.java`
    - _Requirements: 6.2, 6.9, 6.11_

  - [x] 2.6 Create `SqlWarningEntry` record
    - Fields: `timestamp` (`Instant`), `message`, `sqlState`, `errorCode`
    - File: `src/main/java/com/dbexplorer/health/SqlWarningEntry.java`
    - _Requirements: 7.1, 7.2_

  - [x] 2.7 Create `JvmStats` record
    - Fields: `heapUsedBytes`, `heapMaxBytes`, `liveThreadCount`, `gcCollectionCount`,
      `gcCollectionTimeMs`
    - File: `src/main/java/com/dbexplorer/health/JvmStats.java`
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 2.8 Create `StatsModel` class
    - Plain mutable data holder (no synchronisation — single-writer pattern)
    - Fields: `connectionStatus`, `lastValidCheck` (String ISO-8601), `reconnectAttempts`,
      `dbMetadata`, `serverStats`, `warningLog` (`ArrayDeque<SqlWarningEntry>`, max 100),
      `jvmStats`, `lastRefreshed` (`Instant`)
    - File: `src/main/java/com/dbexplorer/health/StatsModel.java`
    - _Requirements: 4.1, 4.2, 4.3, 7.1, 7.3_

  - [ ]* 2.9 Write property test for `ServerStats.empty()` and warning log cap
    - **Property 11: Warning log never exceeds 100 entries and retains newest**
    - **Validates: Requirements 7.1, 7.3**
    - File: `src/test/java/com/dbexplorer/health/StatsModelPropertyTest.java`

- [x] 3. Implement `DashboardConfig` and persistence
  - [x] 3.1 Create `DashboardConfig` class
    - Fields: `pollIntervalSeconds` (int, default 10), `enabledPerConnection`
      (`Map<String, Boolean>`)
    - Validation: reject poll intervals outside [5, 30]
    - File: `src/main/java/com/dbexplorer/health/DashboardConfig.java`
    - _Requirements: 3.1, 3.2, 1.5_

  - [x] 3.2 Implement config load/save to `~/.dbexplorer/dashboard.json` using Gson
    - Add static `load()` and `save(DashboardConfig)` methods (or a `ConfigPersistence`
      helper); use defaults when file is absent or corrupt
    - _Requirements: 1.5, 3.1, 3.2_

  - [ ]* 3.3 Write property test for `DashboardConfig` JSON round-trip
    - **Property 3: Dashboard config round-trip**
    - **Validates: Requirements 1.5**
    - File: `src/test/java/com/dbexplorer/health/DashboardConfigPropertyTest.java`

  - [ ]* 3.4 Write property test for poll interval validation
    - **Property 5: Poll interval validation**
    - **Validates: Requirements 3.1**
    - File: `src/test/java/com/dbexplorer/health/DashboardConfigPropertyTest.java`

- [x] 4. Implement per-engine server stats collectors
  - [x] 4.1 Implement `collectPostgresStats(Connection)` in `HealthCollector`
    - Query `pg_stat_activity`, `pg_stat_database`, `pg_stat_user_tables`,
      `pg_stat_bgwriter`; map results to `ServerStats` fields; wrap each query in
      try/catch — set field to `null` on `SQLException`
    - _Requirements: 6.2, 6.3_

  - [x] 4.2 Implement `collectMysqlStats(Connection)` in `HealthCollector`
    - Query `information_schema`, `performance_schema.*`, `SHOW GLOBAL STATUS`,
      `SHOW PROCESSLIST`; map to `ServerStats`
    - _Requirements: 6.2, 6.4_

  - [x] 4.3 Implement `collectOracleStats(Connection)` in `HealthCollector`
    - Query `v$session`, `v$sql`, `v$sysstat`, `v$waitstat`; map to `ServerStats`
    - _Requirements: 6.2, 6.5_

  - [x] 4.4 Implement `collectSqlServerStats(Connection)` in `HealthCollector`
    - Query `sys.dm_exec_sessions`, `sys.dm_exec_query_stats`, `sys.dm_os_wait_stats`;
      map to `ServerStats`
    - _Requirements: 6.2, 6.6_

  - [x] 4.5 Implement `collectSqliteStats(Connection)` in `HealthCollector`
    - Execute `PRAGMA page_count`, `PRAGMA page_size`, `PRAGMA cache_size`; compute
      `databaseSizeBytes = page_count * page_size`; omit unsupported fields
    - _Requirements: 6.2, 6.7_

  - [x] 4.6 Implement `collectDynamoStats(Connection)` in `HealthCollector`
    - Return `ServerStats.empty()` (DynamoDB metrics not available via JDBC); omit
      unsupported fields
    - _Requirements: 6.2, 6.8_

  - [x] 4.7 Implement `collectServerStats(Connection, DatabaseType)` dispatch method
    - Switch on `DatabaseType`; route to the correct per-engine method; `default` branch
      returns `ServerStats.empty()` for GENERIC and unknown types
    - _Requirements: 6.1, 6.11_

  - [ ]* 4.8 Write property test for `DatabaseType` dispatch routing
    - **Property 8: DatabaseType dispatch routes to correct collector**
    - **Validates: Requirements 6.1**
    - File: `src/test/java/com/dbexplorer/health/HealthCollectorPropertyTest.java`

  - [ ]* 4.9 Write property test for `ServerStats` field mapping from mock result sets
    - **Property 9: ServerStats fields correctly mapped from system view results**
    - **Validates: Requirements 6.2**
    - File: `src/test/java/com/dbexplorer/health/ServerStatsCollectorPropertyTest.java`

  - [ ]* 4.10 Write property test for Generic JDBC fallback
    - **Property 14: Generic JDBC fallback returns empty server stats**
    - **Validates: Requirements 6.11, 6.12**
    - File: `src/test/java/com/dbexplorer/health/HealthCollectorPropertyTest.java`

- [x] 5. Implement per-engine live connections collectors
  - [x] 5.1 Implement `collectPostgresLiveConnections(Connection)` in `HealthCollector`
    - Query `pg_stat_activity`; map `pid` → `connectionId`, `usename` → `username`,
      `client_addr || ':' || client_port` → `host`, `state`, `left(query,80)` →
      `currentQuery`, `extract(epoch from (now()-query_start))*1000` → `durationMs`
    - Set `isHealthConn` by comparing `pid` to the Health_Connection's own backend PID
    - _Requirements: 10.2_

  - [x] 5.2 Implement `collectMysqlLiveConnections(Connection)` in `HealthCollector`
    - Query `performance_schema.processlist` (fall back to `SHOW PROCESSLIST`); map `Id`,
      `User`, `Host`, `Command`, `Info` (80 chars), `Time * 1000`
    - _Requirements: 10.3_

  - [x] 5.3 Implement `collectOracleLiveConnections(Connection)` in `HealthCollector`
    - Query `v$session`; map `SID || ',' || SERIAL#`, `USERNAME`, `MACHINE`, `STATUS`,
      `PROGRAM`, `(SYSDATE - LOGON_TIME) * 86400000`
    - _Requirements: 10.4_

  - [x] 5.4 Implement `collectSqlServerLiveConnections(Connection)` in `HealthCollector`
    - Join `sys.dm_exec_sessions` with `sys.dm_exec_requests` and `sys.dm_exec_sql_text`;
      map `session_id`, `login_name`, `host_name`, `status`, `sql_text` (80 chars),
      `elapsed_time_ms`
    - _Requirements: 10.5_

  - [x] 5.5 Implement fallback `collectLiveConnections` for SQLite, DynamoDB, and GENERIC
    - Return single-element list built from `DatabaseMetaData` (URL, username); set
      `isHealthConn = true`; set engine-specific `note` string
    - _Requirements: 10.6, 10.7, 10.8_

  - [x] 5.6 Implement `collectLiveConnections(Connection, DatabaseType)` dispatch method
    - Switch on `DatabaseType`; route to per-engine method; `default` branch uses GENERIC
      fallback; wrap in try/catch — return empty list on `SQLException`
    - _Requirements: 10.1_

  - [ ]* 5.7 Write property test for live connections column mapping per engine
    - **Property 16: Live connections column mapping is correct per engine**
    - **Validates: Requirements 10.2, 10.3, 10.4, 10.5**
    - File: `src/test/java/com/dbexplorer/health/LiveConnectionCollectorPropertyTest.java`

  - [ ]* 5.8 Write property test for Health_Connection own-session entry
    - **Property 15: Live connections list always contains the Health_Connection's own entry**
    - **Validates: Requirements 10.1, 10.6, 10.7, 10.8**
    - File: `src/test/java/com/dbexplorer/health/LiveConnectionCollectorPropertyTest.java`

- [x] 6. Implement `HealthCollector` core lifecycle and poll cycle
  - [x] 6.1 Create `HealthCollector` class skeleton with lifecycle methods
    - Fields: `volatile StatsModel currentSnapshot`, `volatile Connection healthConn`,
      `ScheduledExecutorService scheduler` (single daemon thread named
      `"health-collector"`), `DashboardConfig config`, `ConnectionInfo connInfo`,
      `Consumer<StatsModel> onSnapshot`
    - Methods: `start(ConnectionInfo, DashboardConfig, Consumer<StatsModel>)`, `stop()`,
      `applyConfig(DashboardConfig)`
    - `start()` opens the Health_Connection directly via `DriverManager` (not via
      `ConnectionManager`); schedules `runPollCycle` with `scheduleWithFixedDelay`
    - `stop()` cancels the scheduled task and closes the Health_Connection
    - File: `src/main/java/com/dbexplorer/health/HealthCollector.java`
    - _Requirements: 1.2, 1.3, 2.1, 2.2, 3.4, 3.5_

  - [x] 6.2 Implement `runPollCycle()` in `HealthCollector`
    - Step 1: call `Connection.isValid(2)`; on failure invoke `reconnect()`
    - Step 2: read `DatabaseMetaData` on first cycle only (guard with boolean flag)
    - Step 3: call `collectServerStats(conn, dbType)` and `collectLiveConnections(conn, dbType)`
    - Step 4: collect JVM stats via `MemoryMXBean`, `ThreadMXBean`,
      `GarbageCollectorMXBean`
    - Step 5: call `Connection.getWarnings()` and append new entries to warning log
      (circular buffer, max 100)
    - Step 6: build new `StatsModel` snapshot; call
      `SwingUtilities.invokeLater(() -> onSnapshot.accept(snapshot))`
    - Wrap entire cycle in try/catch `Exception` — log to `System.err`, skip cycle
    - _Requirements: 3.5, 4.1, 4.2, 5.4, 7.1, 8.1, 8.2, 8.3_

  - [x] 6.3 Implement `reconnect()` in `HealthCollector`
    - Up to 3 attempts with 1-second sleep between; on exhaustion set
      `ConnectionStatus.FAILED`, cancel scheduled task, push FAILED snapshot via
      `invokeLater`
    - _Requirements: 2.3, 2.5, 4.3_

  - [x] 6.4 Implement `applyConfig(DashboardConfig)` hot-reload
    - Update stored config; reschedule the task with the new interval (cancel old task,
      schedule new one) without closing the Health_Connection
    - _Requirements: 3.3_

  - [ ]* 6.5 Write property test for reconnect exhaustion → FAILED status
    - **Property 4: Reconnect exhaustion leads to FAILED status with accurate count**
    - **Validates: Requirements 2.3, 2.5, 4.3**
    - File: `src/test/java/com/dbexplorer/health/HealthCollectorPropertyTest.java`

  - [ ]* 6.6 Write property test for `isValid` result faithfully recorded
    - **Property 6: isValid result faithfully recorded with timestamp**
    - **Validates: Requirements 4.1, 4.2**
    - File: `src/test/java/com/dbexplorer/health/HealthCollectorPropertyTest.java`

  - [ ]* 6.7 Write property test for metadata fetched exactly once
    - **Property 7: Database metadata fetched exactly once per session**
    - **Validates: Requirements 5.4**
    - File: `src/test/java/com/dbexplorer/health/HealthCollectorPropertyTest.java`

  - [ ]* 6.8 Write property test for enable-then-disable returns to stopped state
    - **Property 1: Enable-then-disable returns to stopped state**
    - **Validates: Requirements 1.3**
    - File: `src/test/java/com/dbexplorer/health/HealthCollectorPropertyTest.java`

  - [ ]* 6.9 Write property test for disabled collector opens no connections
    - **Property 2: Disabled collector opens no connections**
    - **Validates: Requirements 1.4**
    - File: `src/test/java/com/dbexplorer/health/HealthCollectorPropertyTest.java`

- [ ] 7. Checkpoint — Ensure all health package unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement `DashboardPanel` UI component
  - [x] 8.1 Create `DashboardPanel` class skeleton
    - Extends `JPanel`; constructor takes `HealthCollector` and `DashboardConfig`
    - Public methods: `updateSnapshot(StatsModel)` (must be called on EDT),
      `onConnectionChanged(ConnectionInfo)`, `applyTheme()`
    - Internal layout: `JScrollPane` wrapping a vertical `BoxLayout` panel containing
      collapsible section panels
    - File: `src/main/java/com/dbexplorer/ui/DashboardPanel.java`
    - _Requirements: 11.1, 11.2, 11.4_

  - [x] 8.2 Implement Connection Health section
    - Colour-coded status indicator (green = VALID, red = INVALID/FAILED), last-check
      timestamp label, reconnect count label
    - _Requirements: 4.4, 4.5_

  - [x] 8.3 Implement Database Metadata section
    - Read-only label grid showing all `DbMetadata` fields (product name/version, driver
      name/version, max connections, supported features)
    - _Requirements: 5.3_

  - [x] 8.4 Implement Server Activity section
    - Label grid for all non-null `ServerStats` scalar fields; hide rows where value is
      `null`; embedded `JTable` for `activeQueries` list
    - When `serverStats == ServerStats.empty()` (all null), display the unavailability
      message: *"Server Activity statistics are not available for this database type.
      Connection health, metadata, warnings, and JVM stats are still collected."*
    - Format all numeric values with `NumberFormat.getNumberInstance(Locale.getDefault())`
    - _Requirements: 6.9, 6.10, 6.12, 11.6_

  - [x] 8.5 Implement Live Connections section
    - `JTable` with columns: Connection ID, Username, Host, State, Current Query, Duration
    - Custom `TableCellRenderer`: render `null` cells as `"—"`; render rows where
      `isHealthConn == true` with bold font and theme accent background
    - Row count label: `"N active connection(s)"`
    - When `LiveConnection.note` is non-null, show italic `JLabel` beneath the table
    - _Requirements: 10.9, 10.10, 10.11, 10.12, 10.13_

  - [x] 8.6 Implement SQL Warnings section
    - Display the 10 most recent `SqlWarningEntry` items with timestamp, SQLState, and
      message
    - _Requirements: 7.2_

  - [x] 8.7 Implement JVM Resources section
    - `JProgressBar` for heap usage; foreground colour: default ≤ 70%, amber (`#F59E0B`)
      70–90%, red (`#EF4444`) > 90%
    - Labels for heap used/max (formatted), heap %, live thread count, GC count, GC time
    - _Requirements: 8.4, 8.5_

  - [x] 8.8 Implement "Last refreshed" timestamp label
    - Updated on every `updateSnapshot()` call; formatted as locale date-time string
    - _Requirements: 11.3_

  - [ ]* 8.9 Write property test for null metrics omitted from display
    - **Property 10: Null metrics are omitted from the display**
    - **Validates: Requirements 6.9**
    - File: `src/test/java/com/dbexplorer/ui/DashboardPanelPropertyTest.java`

  - [ ]* 8.10 Write property test for heap colour threshold logic
    - **Property 12: Heap colour threshold logic**
    - **Validates: Requirements 8.5**
    - File: `src/test/java/com/dbexplorer/ui/DashboardPanelPropertyTest.java`

  - [ ]* 8.11 Write property test for numeric formatting with thousands separator
    - **Property 13: Numeric formatting uses locale thousands separator**
    - **Validates: Requirements 11.6**
    - File: `src/test/java/com/dbexplorer/ui/DashboardPanelPropertyTest.java`

  - [ ]* 8.12 Write property test for null `LiveConnection` fields render as "—"
    - **Property 17: Null LiveConnection fields render as "—"**
    - **Validates: Requirements 10.10**
    - File: `src/test/java/com/dbexplorer/ui/DashboardPanelPropertyTest.java`

  - [ ]* 8.13 Write property test for live connections row count label
    - **Property 18: Live connections row count label matches list size**
    - **Validates: Requirements 10.11**
    - File: `src/test/java/com/dbexplorer/ui/DashboardPanelPropertyTest.java`

  - [ ]* 8.14 Write property test for Health_Connection row visual highlight
    - **Property 19: Health_Connection row is visually highlighted**
    - **Validates: Requirements 10.13**
    - File: `src/test/java/com/dbexplorer/ui/DashboardPanelPropertyTest.java`

- [x] 9. Integrate `DashboardPanel` and `HealthCollector` into `MainFrame`
  - [x] 9.1 Add `HealthCollector` and `DashboardPanel` fields to `MainFrame`; construct
    them in the constructor after existing components are built
    - Load `DashboardConfig` from `~/.dbexplorer/dashboard.json` at startup
    - _Requirements: 11.1_

  - [x] 9.2 Add dashboard toolbar button using `chart-bar.svg` icon
    - Button toggles `DashboardPanel` visibility; calls `HealthCollector.start()` /
      `stop()` accordingly; saves updated `DashboardConfig` on toggle
    - Add the button to the toolbar between the existing separator and the theme combo
    - _Requirements: 1.2, 1.3, 11.1_

  - [x] 9.3 Integrate `DashboardPanel` into the main layout
    - Add `DashboardPanel` as a right-side panel inside a new outer `JSplitPane` wrapping
      the existing `mainSplit`; collapse divider to zero when hidden
    - _Requirements: 11.1_

  - [x] 9.4 Wire connection-change events to `DashboardPanel.onConnectionChanged()`
    - Call `onConnectionChanged` when the active tab changes (`sqlEditorPanel.addChangeListener`)
      and when a connection is toggled (`connectionListPanel.setOnConnect` callback)
    - _Requirements: 11.5_

  - [x] 9.5 Add `healthCollector.stop()` to the window-closing shutdown hook before
    `queryExecutor.shutdown()`
    - _Requirements: 2.4_

  - [x] 9.6 Pass `DashboardPanel.updateSnapshot` as the `onSnapshot` consumer when calling
    `HealthCollector.start()`; ensure the dashboard is disabled by default on first launch
    - _Requirements: 1.1, 1.2_

- [x] 10. Add `DbIcons` entry for the dashboard toolbar button
  - Add a `TB_DASHBOARD` constant to `DbIcons` loading `chart-bar.svg` at the same size
    as existing toolbar icons
  - File: `src/main/java/com/dbexplorer/ui/DbIcons.java`
  - _Requirements: 11.1_

- [x] 11. Checkpoint — Ensure all tests pass and dashboard is visible in the running app
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Property tests use jqwik with a minimum of 100 tries per property
- `HealthCollector` uses `scheduleWithFixedDelay` so overlapping poll cycles are impossible
- All Swing mutations in `HealthCollector` go through `SwingUtilities.invokeLater`
- `ConnectionManager` and `QueryExecutor` are not modified

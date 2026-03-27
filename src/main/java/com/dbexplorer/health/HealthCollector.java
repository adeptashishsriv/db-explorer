package com.dbexplorer.health;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.model.DatabaseType;

/**
 * Background service that owns a dedicated JDBC connection (Health_Connection)
 * and polls database + JVM statistics on a configurable schedule.
 *
 * Threading: all polling runs on a single daemon thread named "health-collector".
 * All Swing mutations are dispatched via SwingUtilities.invokeLater.
 * QueryExecutor and ConnectionManager are never touched.
 */
public class HealthCollector {

    private static final int RECONNECT_MAX_ATTEMPTS = 3;
    private static final int RECONNECT_SLEEP_MS     = 1000;
    private static final int ISVALID_TIMEOUT_SEC    = 2;

    private volatile Connection              healthConn;
    private volatile StatsModel              currentSnapshot;
    private volatile boolean                 metadataFetched = false;
    private volatile DbMetadata              cachedMetadata;
    private volatile int                     reconnectAttempts = 0;
    private volatile boolean                 running = false;

    private ScheduledExecutorService         scheduler;
    private ScheduledFuture<?>               pollTask;
    private DashboardConfig                  config;
    private ConnectionInfo                   connInfo;
    private Consumer<StatsModel>             onSnapshot;

    // Warning log shared across cycles (single-writer: health-collector thread only)
    private final Deque<SqlWarningEntry>     warningLog = new ArrayDeque<>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public synchronized void start(ConnectionInfo info, DashboardConfig cfg,
                                   Consumer<StatsModel> snapshotConsumer) {
        if (running) stop();
        this.connInfo    = info;
        this.config      = cfg;
        this.onSnapshot  = snapshotConsumer;
        this.metadataFetched  = false;
        this.cachedMetadata   = null;
        this.reconnectAttempts = 0;
        this.warningLog.clear();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-collector");
            t.setDaemon(true);
            return t;
        });

        running = true;
        long intervalSec = config.getPollIntervalSeconds();
        pollTask = scheduler.scheduleWithFixedDelay(
            this::runPollCycle, 0, intervalSec, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (pollTask != null) { pollTask.cancel(false); pollTask = null; }
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        closeHealthConn();
    }

    public synchronized void applyConfig(DashboardConfig newConfig) {
        this.config = newConfig;
        if (!running) return;
        // Reschedule with new interval without closing the connection
        if (pollTask != null) pollTask.cancel(false);
        long intervalSec = newConfig.getPollIntervalSeconds();
        pollTask = scheduler.scheduleWithFixedDelay(
            this::runPollCycle, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    public boolean isRunning() { return running; }

    // -------------------------------------------------------------------------
    // Poll cycle
    // -------------------------------------------------------------------------

    void runPollCycle() {
        try {
            StatsModel snap = new StatsModel();
            snap.reconnectAttempts = reconnectAttempts;

            // 1. Validate connection
            boolean valid = false;
            try {
                valid = healthConn != null && healthConn.isValid(ISVALID_TIMEOUT_SEC);
            } catch (SQLException ignored) {}

            if (!valid) {
                valid = reconnect();
            }

            if (!valid) {
                snap.connectionStatus = ConnectionStatus.FAILED;
                snap.lastRefreshed    = Instant.now();
                publish(snap);
                return;
            }

            snap.connectionStatus = ConnectionStatus.VALID;
            snap.lastValidCheck   = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

            // 2. DB metadata — once per session
            if (!metadataFetched) {
                try {
                    cachedMetadata = fetchMetadata(healthConn);
                    metadataFetched = true;
                } catch (SQLException e) {
                    System.err.println("[HealthCollector] metadata fetch failed: " + e.getMessage());
                }
            }
            snap.dbMetadata = cachedMetadata;

            // 3. Server stats + live connections
            DatabaseType dbType = connInfo.getDbType();
            ServerStats serverStats = collectServerStats(healthConn, dbType);
            List<LiveConnection> liveConns = collectLiveConnections(healthConn, dbType);

            // Merge liveConnections into serverStats
            snap.serverStats = new ServerStats(
                serverStats.activeSessionCount(),
                serverStats.totalConnectionCount(),
                serverStats.activeQueries(),
                liveConns,
                serverStats.totalQueriesExecuted(),
                serverStats.totalCommits(),
                serverStats.totalRollbacks(),
                serverStats.cacheHitRatio(),
                serverStats.lockWaitCount(),
                serverStats.deadlockCount(),
                serverStats.seqScanCount(),
                serverStats.idxScanCount(),
                serverStats.slowQueryCount(),
                serverStats.databaseSizeBytes()
            );

            // 4. JVM stats
            snap.jvmStats = collectJvmStats();

            // 5. SQL warnings
            try {
                SQLWarning w = healthConn.getWarnings();
                while (w != null) {
                    SqlWarningEntry entry = new SqlWarningEntry(
                        Instant.now(), w.getMessage(), w.getSQLState(), w.getErrorCode());
                    warningLog.addLast(entry);
                    if (warningLog.size() > StatsModel.MAX_WARNING_LOG) warningLog.removeFirst();
                    w = w.getNextWarning();
                }
                healthConn.clearWarnings();
            } catch (SQLException e) {
                System.err.println("[HealthCollector] warning fetch failed: " + e.getMessage());
            }
            snap.warningLog   = new ArrayDeque<>(warningLog);
            snap.lastRefreshed = Instant.now();

            publish(snap);

        } catch (Exception e) {
            System.err.println("[HealthCollector] poll cycle error: " + e.getMessage());
        }
    }

    private void publish(StatsModel snap) {
        currentSnapshot = snap;
        Consumer<StatsModel> cb = onSnapshot;
        if (cb != null) SwingUtilities.invokeLater(() -> cb.accept(snap));
    }

    // -------------------------------------------------------------------------
    // Reconnect
    // -------------------------------------------------------------------------

    boolean reconnect() {
        for (int attempt = 1; attempt <= RECONNECT_MAX_ATTEMPTS; attempt++) {
            reconnectAttempts++;
            try {
                closeHealthConn();
                healthConn = openConnection(connInfo);
                return true;
            } catch (Exception e) {
                System.err.println("[HealthCollector] reconnect attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < RECONNECT_MAX_ATTEMPTS) {
                    try { Thread.sleep(RECONNECT_SLEEP_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        // Exhausted — stop polling
        running = false;
        if (pollTask != null) { pollTask.cancel(false); pollTask = null; }
        return false;
    }

    private void closeHealthConn() {
        try { if (healthConn != null && !healthConn.isClosed()) healthConn.close(); }
        catch (SQLException ignored) {}
        healthConn = null;
    }

    private static Connection openConnection(ConnectionInfo info) throws SQLException {
        return DriverManager.getConnection(info.getJdbcUrl(), info.getUsername(), info.getPassword());
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    private static DbMetadata fetchMetadata(Connection conn) throws SQLException {
        DatabaseMetaData m = conn.getMetaData();
        return new DbMetadata(
            m.getDatabaseProductName(),
            m.getDatabaseProductVersion(),
            m.getDriverName(),
            m.getDriverVersion(),
            m.getMaxConnections(),
            m.supportsTransactions(),
            m.supportsSavepoints(),
            m.supportsBatchUpdates(),
            m.supportsStoredProcedures()
        );
    }

    // -------------------------------------------------------------------------
    // JVM stats
    // -------------------------------------------------------------------------

    private static JvmStats collectJvmStats() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        long used = mem.getHeapMemoryUsage().getUsed();
        long max  = mem.getHeapMemoryUsage().getMax();

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int liveThreads = threads.getThreadCount();

        long gcCount = 0, gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount(); if (c > 0) gcCount += c;
            long t = gc.getCollectionTime();  if (t > 0) gcTime  += t;
        }
        return new JvmStats(used, max, liveThreads, gcCount, gcTime);
    }

    // -------------------------------------------------------------------------
    // Server stats dispatch
    // -------------------------------------------------------------------------

    ServerStats collectServerStats(Connection conn, DatabaseType dbType) {
        return switch (dbType) {
            case POSTGRESQL -> collectPostgresStats(conn);
            case MYSQL      -> collectMysqlStats(conn);
            case ORACLE     -> collectOracleStats(conn);
            case SQLSERVER  -> collectSqlServerStats(conn);
            case SQLITE     -> collectSqliteStats(conn);
            case DYNAMODB   -> ServerStats.empty();
            default         -> ServerStats.empty();
        };
    }

    // --- PostgreSQL ---
    private ServerStats collectPostgresStats(Connection conn) {
        Integer activeSessions = null; Integer totalConns = null;
        Long queries = null, commits = null, rollbacks = null;
        Double cacheHit = null; Long lockWaits = null, deadlocks = null;
        Long seqScans = null, idxScans = null, slowQueries = null, dbSize = null;
        List<ActiveQuery> activeQueries = new ArrayList<>();

        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FILTER (WHERE state='active') AS active, count(*) AS total " +
                    "FROM pg_stat_activity")) {
                if (rs.next()) { activeSessions = rs.getInt(1); totalConns = rs.getInt(2); }
            } catch (SQLException e) { logMetricError("pg_stat_activity counts", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT xact_commit, xact_rollback, blks_hit, blks_read " +
                    "FROM pg_stat_database WHERE datname = current_database()")) {
                if (rs.next()) {
                    commits    = rs.getLong(1);
                    rollbacks  = rs.getLong(2);
                    long hit   = rs.getLong(3), read = rs.getLong(4);
                    cacheHit   = (hit + read) > 0 ? (double) hit / (hit + read) : null;
                }
            } catch (SQLException e) { logMetricError("pg_stat_database", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT sum(seq_scan), sum(idx_scan) FROM pg_stat_user_tables")) {
                if (rs.next()) { seqScans = rs.getLong(1); idxScans = rs.getLong(2); }
            } catch (SQLException e) { logMetricError("pg_stat_user_tables", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT deadlocks FROM pg_stat_database WHERE datname = current_database()")) {
                if (rs.next()) deadlocks = rs.getLong(1);
            } catch (SQLException e) { logMetricError("pg deadlocks", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT pg_database_size(current_database())")) {
                if (rs.next()) dbSize = rs.getLong(1);
            } catch (SQLException e) { logMetricError("pg_database_size", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT pid::text, state, left(query,80), " +
                    "EXTRACT(EPOCH FROM (now()-query_start))*1000 " +
                    "FROM pg_stat_activity WHERE state='active' AND query NOT LIKE '%pg_stat_activity%'")) {
                while (rs.next()) {
                    activeQueries.add(new ActiveQuery(
                        rs.getString(1), rs.getString(2),
                        rs.getString(3), (long) rs.getDouble(4)));
                }
            } catch (SQLException e) { logMetricError("pg active queries", e); }

        } catch (SQLException e) {
            System.err.println("[HealthCollector] PostgreSQL stats error: " + e.getMessage());
        }
        return new ServerStats(activeSessions, totalConns, activeQueries, List.of(),
            queries, commits, rollbacks, cacheHit, lockWaits, deadlocks,
            seqScans, idxScans, slowQueries, dbSize);
    }

    // --- MySQL / MariaDB ---
    private ServerStats collectMysqlStats(Connection conn) {
        Integer activeSessions = null; Integer totalConns = null;
        Long commits = null, rollbacks = null; Double cacheHit = null;
        Long lockWaits = null, deadlocks = null, dbSize = null;
        List<ActiveQuery> activeQueries = new ArrayList<>();

        try (Statement st = conn.createStatement()) {
            Map<String, String> status = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS")) {
                while (rs.next()) status.put(rs.getString(1), rs.getString(2));
            } catch (SQLException e) { logMetricError("SHOW GLOBAL STATUS", e); }

            activeSessions = parseIntStat(status, "Threads_running");
            totalConns     = parseIntStat(status, "Threads_connected");
            commits        = parseLongStat(status, "Com_commit");
            rollbacks      = parseLongStat(status, "Com_rollback");
            lockWaits      = parseLongStat(status, "Innodb_row_lock_waits");
            deadlocks      = parseLongStat(status, "Innodb_deadlocks");

            Long hit  = parseLongStat(status, "Innodb_buffer_pool_read_requests");
            Long miss = parseLongStat(status, "Innodb_buffer_pool_reads");
            if (hit != null && miss != null && (hit + miss) > 0)
                cacheHit = (double) hit / (hit + miss);

            try (ResultSet rs = st.executeQuery(
                    "SELECT ID, USER, HOST, COMMAND, LEFT(INFO,80), TIME*1000 " +
                    "FROM information_schema.PROCESSLIST WHERE COMMAND != 'Sleep'")) {
                while (rs.next()) {
                    activeQueries.add(new ActiveQuery(
                        rs.getString(1), rs.getString(4),
                        rs.getString(5), rs.getLong(6)));
                }
            } catch (SQLException e) { logMetricError("MySQL processlist", e); }

        } catch (SQLException e) {
            System.err.println("[HealthCollector] MySQL stats error: " + e.getMessage());
        }
        return new ServerStats(activeSessions, totalConns, activeQueries, List.of(),
            null, commits, rollbacks, cacheHit, lockWaits, deadlocks,
            null, null, null, dbSize);
    }

    // --- Oracle ---
    private ServerStats collectOracleStats(Connection conn) {
        Integer activeSessions = null; Integer totalConns = null;
        Long commits = null, rollbacks = null; Long lockWaits = null;
        List<ActiveQuery> activeQueries = new ArrayList<>();

        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM v$session WHERE status='ACTIVE' AND type='USER'")) {
                if (rs.next()) activeSessions = rs.getInt(1);
            } catch (SQLException e) { logMetricError("v$session active", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM v$session WHERE type='USER'")) {
                if (rs.next()) totalConns = rs.getInt(1);
            } catch (SQLException e) { logMetricError("v$session total", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT VALUE FROM v$sysstat WHERE NAME='user commits'")) {
                if (rs.next()) commits = rs.getLong(1);
            } catch (SQLException e) { logMetricError("v$sysstat commits", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT VALUE FROM v$sysstat WHERE NAME='user rollbacks'")) {
                if (rs.next()) rollbacks = rs.getLong(1);
            } catch (SQLException e) { logMetricError("v$sysstat rollbacks", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT s.SID||','||s.SERIAL#, s.USERNAME, s.MACHINE, s.STATUS, " +
                    "SUBSTR(q.SQL_TEXT,1,80), " +
                    "ROUND((SYSDATE - s.LAST_CALL_ET/86400)*86400000) " +
                    "FROM v$session s LEFT JOIN v$sql q ON s.SQL_ID=q.SQL_ID " +
                    "WHERE s.STATUS='ACTIVE' AND s.TYPE='USER'")) {
                while (rs.next()) {
                    activeQueries.add(new ActiveQuery(
                        rs.getString(1), rs.getString(4),
                        rs.getString(5), rs.getLong(6)));
                }
            } catch (SQLException e) { logMetricError("v$session active queries", e); }

        } catch (SQLException e) {
            System.err.println("[HealthCollector] Oracle stats error: " + e.getMessage());
        }
        return new ServerStats(activeSessions, totalConns, activeQueries, List.of(),
            null, commits, rollbacks, null, lockWaits, null,
            null, null, null, null);
    }

    // --- SQL Server ---
    private ServerStats collectSqlServerStats(Connection conn) {
        Integer activeSessions = null; Integer totalConns = null;
        Long lockWaits = null, deadlocks = null;
        List<ActiveQuery> activeQueries = new ArrayList<>();

        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE is_user_process=1 AND status='running'")) {
                if (rs.next()) activeSessions = rs.getInt(1);
            } catch (SQLException e) { logMetricError("dm_exec_sessions active", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE is_user_process=1")) {
                if (rs.next()) totalConns = rs.getInt(1);
            } catch (SQLException e) { logMetricError("dm_exec_sessions total", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT wait_type, waiting_tasks_count FROM sys.dm_os_wait_stats " +
                    "WHERE wait_type='LCK_M_X' OR wait_type='DEADLOCK'")) {
                while (rs.next()) {
                    String wt = rs.getString(1); long cnt = rs.getLong(2);
                    if ("LCK_M_X".equals(wt))  lockWaits  = cnt;
                    if ("DEADLOCK".equals(wt))  deadlocks  = cnt;
                }
            } catch (SQLException e) { logMetricError("dm_os_wait_stats", e); }

            try (ResultSet rs = st.executeQuery(
                    "SELECT s.session_id, s.login_name, s.host_name, r.status, " +
                    "SUBSTRING(t.text,1,80), r.total_elapsed_time " +
                    "FROM sys.dm_exec_sessions s " +
                    "JOIN sys.dm_exec_requests r ON s.session_id=r.session_id " +
                    "CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t " +
                    "WHERE s.is_user_process=1")) {
                while (rs.next()) {
                    activeQueries.add(new ActiveQuery(
                        String.valueOf(rs.getInt(1)), rs.getString(4),
                        rs.getString(5), rs.getLong(6)));
                }
            } catch (SQLException e) { logMetricError("dm_exec_requests", e); }

        } catch (SQLException e) {
            System.err.println("[HealthCollector] SQL Server stats error: " + e.getMessage());
        }
        return new ServerStats(activeSessions, totalConns, activeQueries, List.of(),
            null, null, null, null, lockWaits, deadlocks,
            null, null, null, null);
    }

    // --- SQLite ---
    private ServerStats collectSqliteStats(Connection conn) {
        Long dbSize = null;
        try (Statement st = conn.createStatement()) {
            long pageCount = 0, pageSize = 0;
            try (ResultSet rs = st.executeQuery("PRAGMA page_count")) {
                if (rs.next()) pageCount = rs.getLong(1);
            } catch (SQLException e) { logMetricError("PRAGMA page_count", e); }
            try (ResultSet rs = st.executeQuery("PRAGMA page_size")) {
                if (rs.next()) pageSize = rs.getLong(1);
            } catch (SQLException e) { logMetricError("PRAGMA page_size", e); }
            if (pageCount > 0 && pageSize > 0) dbSize = pageCount * pageSize;
        } catch (SQLException e) {
            System.err.println("[HealthCollector] SQLite stats error: " + e.getMessage());
        }
        return new ServerStats(1, 1, List.of(), List.of(),
            null, null, null, null, null, null, null, null, null, dbSize);
    }

    // -------------------------------------------------------------------------
    // Live connections dispatch
    // -------------------------------------------------------------------------

    List<LiveConnection> collectLiveConnections(Connection conn, DatabaseType dbType) {
        try {
            return switch (dbType) {
                case POSTGRESQL -> collectPostgresLiveConnections(conn);
                case MYSQL      -> collectMysqlLiveConnections(conn);
                case ORACLE     -> collectOracleLiveConnections(conn);
                case SQLSERVER  -> collectSqlServerLiveConnections(conn);
                case SQLITE     -> fallbackLiveConnections(conn,
                    "SQLite is an embedded database; only one connection exists");
                case DYNAMODB   -> fallbackLiveConnections(conn,
                    "DynamoDB is serverless; connection listing is not applicable");
                default         -> fallbackLiveConnections(conn,
                    "Full connection list not available for this database type");
            };
        } catch (Exception e) {
            System.err.println("[HealthCollector] collectLiveConnections error: " + e.getMessage());
            return List.of();
        }
    }

    private List<LiveConnection> collectPostgresLiveConnections(Connection conn) throws SQLException {
        List<LiveConnection> list = new ArrayList<>();
        String ownPid = getPostgresOwnPid(conn);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT pid::text, usename, " +
                "COALESCE(client_addr::text,'local') || ':' || COALESCE(client_port::text,'') AS host, " +
                "state, left(query,80), " +
                "EXTRACT(EPOCH FROM (now()-query_start))*1000 " +
                "FROM pg_stat_activity WHERE backend_type='client backend'")) {
            while (rs.next()) {
                String pid = rs.getString(1);
                list.add(new LiveConnection(
                    pid, rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), nullableLong(rs, 6),
                    pid.equals(ownPid), null));
            }
        }
        return list;
    }

    private String getPostgresOwnPid(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT pg_backend_pid()")) {
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return null;
    }

    private List<LiveConnection> collectMysqlLiveConnections(Connection conn) throws SQLException {
        List<LiveConnection> list = new ArrayList<>();
        String ownId = getMysqlOwnId(conn);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT ID, USER, HOST, COMMAND, LEFT(INFO,80), TIME*1000 " +
                "FROM information_schema.PROCESSLIST")) {
            while (rs.next()) {
                String id = rs.getString(1);
                list.add(new LiveConnection(
                    id, rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getLong(6),
                    id.equals(ownId), null));
            }
        }
        return list;
    }

    private String getMysqlOwnId(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT CONNECTION_ID()")) {
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return null;
    }

    private List<LiveConnection> collectOracleLiveConnections(Connection conn) throws SQLException {
        List<LiveConnection> list = new ArrayList<>();
        String ownSid = getOracleOwnSid(conn);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT s.SID||','||s.SERIAL#, s.USERNAME, s.MACHINE, s.STATUS, " +
                "s.PROGRAM, ROUND((SYSDATE - s.LOGON_TIME)*86400000) " +
                "FROM v$session s WHERE s.TYPE='USER'")) {
            while (rs.next()) {
                String sid = rs.getString(1);
                list.add(new LiveConnection(
                    sid, rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getLong(6),
                    sid.equals(ownSid), null));
            }
        }
        return list;
    }

    private String getOracleOwnSid(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT SID||','||SERIAL# FROM v$session WHERE AUDSID=USERENV('SESSIONID')")) {
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return null;
    }

    private List<LiveConnection> collectSqlServerLiveConnections(Connection conn) throws SQLException {
        List<LiveConnection> list = new ArrayList<>();
        String ownId = getSqlServerOwnId(conn);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT s.session_id, s.login_name, s.host_name, s.status, " +
                "SUBSTRING(ISNULL(t.text,''),1,80), ISNULL(r.total_elapsed_time,0) " +
                "FROM sys.dm_exec_sessions s " +
                "LEFT JOIN sys.dm_exec_requests r ON s.session_id=r.session_id " +
                "OUTER APPLY sys.dm_exec_sql_text(r.sql_handle) t " +
                "WHERE s.is_user_process=1")) {
            while (rs.next()) {
                String sid = String.valueOf(rs.getInt(1));
                list.add(new LiveConnection(
                    sid, rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getLong(6),
                    sid.equals(ownId), null));
            }
        }
        return list;
    }

    private String getSqlServerOwnId(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT @@SPID")) {
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return null;
    }

    private List<LiveConnection> fallbackLiveConnections(Connection conn, String note) {
        String url = "N/A", user = "N/A";
        try {
            DatabaseMetaData m = conn.getMetaData();
            url  = m.getURL();
            user = m.getUserName();
        } catch (SQLException ignored) {}
        return List.of(new LiveConnection("N/A", user, url, "active", null, null, true, note));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void logMetricError(String metric, SQLException e) {
        System.err.println("[HealthCollector] metric '" + metric + "' failed: " + e.getMessage());
    }

    private static Integer parseIntStat(Map<String, String> map, String key) {
        String v = map.get(key);
        try { return v != null ? Integer.parseInt(v) : null; } catch (NumberFormatException e) { return null; }
    }

    private static Long parseLongStat(Map<String, String> map, String key) {
        String v = map.get(key);
        try { return v != null ? Long.parseLong(v) : null; } catch (NumberFormatException e) { return null; }
    }

    private static Long nullableLong(ResultSet rs, int col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}

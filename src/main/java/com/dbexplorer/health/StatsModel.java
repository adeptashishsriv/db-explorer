package com.dbexplorer.health;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Plain data holder for a single polling snapshot.
 * No synchronisation — single-writer pattern: HealthCollector constructs a new
 * instance each cycle and publishes it via a volatile reference swap.
 * The EDT reads only the published snapshot.
 */
public class StatsModel {

    public static final int MAX_WARNING_LOG = 100;

    // Connection health
    public ConnectionStatus connectionStatus = ConnectionStatus.INVALID;
    public String           lastValidCheck;       // ISO-8601; null until first valid check
    public int              reconnectAttempts;

    // DB metadata — populated once on first connection
    public DbMetadata dbMetadata;

    // Server-side activity stats
    public ServerStats serverStats = ServerStats.empty();

    // SQL warning circular buffer (max 100)
    public Deque<SqlWarningEntry> warningLog = new ArrayDeque<>();

    // JVM stats
    public JvmStats jvmStats;

    // Timestamp of the last completed poll cycle
    public Instant lastRefreshed;
}

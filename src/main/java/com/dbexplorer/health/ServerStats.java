package com.dbexplorer.health;

import java.util.List;

/**
 * Server-side statistics collected from the database's own system views.
 * All numeric fields are nullable — null means the metric is not supported
 * by the connected engine and will be omitted from the UI.
 */
public record ServerStats(
    Integer              activeSessionCount,
    Integer              totalConnectionCount,
    List<ActiveQuery>    activeQueries,
    List<LiveConnection> liveConnections,
    Long                 totalQueriesExecuted,
    Long                 totalCommits,
    Long                 totalRollbacks,
    Double               cacheHitRatio,
    Long                 lockWaitCount,
    Long                 deadlockCount,
    Long                 seqScanCount,
    Long                 idxScanCount,
    Long                 slowQueryCount,
    Long                 databaseSizeBytes
) {
    public static ServerStats empty() {
        return new ServerStats(
            null, null, List.of(), List.of(),
            null, null, null, null,
            null, null, null, null, null, null
        );
    }

    public boolean isEmpty() {
        return activeSessionCount == null
            && totalConnectionCount == null
            && activeQueries.isEmpty()
            && totalQueriesExecuted == null
            && totalCommits == null
            && totalRollbacks == null
            && cacheHitRatio == null
            && lockWaitCount == null
            && deadlockCount == null
            && seqScanCount == null
            && idxScanCount == null
            && slowQueryCount == null
            && databaseSizeBytes == null;
    }
}

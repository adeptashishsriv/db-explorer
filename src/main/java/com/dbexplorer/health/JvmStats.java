package com.dbexplorer.health;

public record JvmStats(
    long heapUsedBytes,
    long heapMaxBytes,
    int  liveThreadCount,
    long gcCollectionCount,
    long gcCollectionTimeMs
) {}

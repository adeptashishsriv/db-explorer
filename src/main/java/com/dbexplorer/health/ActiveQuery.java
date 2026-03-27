package com.dbexplorer.health;

public record ActiveQuery(
    String pid,
    String state,
    String queryText,
    long   durationMs
) {}

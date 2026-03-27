package com.dbexplorer.health;

import java.time.Instant;

public record SqlWarningEntry(
    Instant timestamp,
    String  message,
    String  sqlState,
    int     errorCode
) {}

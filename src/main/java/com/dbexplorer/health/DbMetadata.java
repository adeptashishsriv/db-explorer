package com.dbexplorer.health;

public record DbMetadata(
    String  productName,
    String  productVersion,
    String  driverName,
    String  driverVersion,
    int     maxConnections,
    boolean supportsTransactions,
    boolean supportsSavepoints,
    boolean supportsBatchUpdates,
    boolean supportsStoredProcedures
) {}

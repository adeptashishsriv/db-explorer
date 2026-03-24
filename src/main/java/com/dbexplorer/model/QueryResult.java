package com.dbexplorer.model;

import java.util.List;

public record QueryResult(
        List<String> columns,
        List<List<Object>> rows,
        long executionTimeMs,
        int affectedRows,
        boolean isResultSet
) {}

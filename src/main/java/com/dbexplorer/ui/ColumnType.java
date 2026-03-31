package com.dbexplorer.ui;

import java.sql.Types;

public enum ColumnType {
    NUMERIC,
    DATETIME,
    TEXT;

    public static ColumnType fromSqlType(int sqlType) {
        switch (sqlType) {
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
            case Types.NUMERIC:
            case Types.DECIMAL:
                return NUMERIC;

            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
            case Types.TIME_WITH_TIMEZONE:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return DATETIME;

            default:
                return TEXT;
        }
    }
}

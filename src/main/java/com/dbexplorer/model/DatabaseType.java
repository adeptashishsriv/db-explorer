package com.dbexplorer.model;

public enum DatabaseType {
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s", 5432, false),
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s", 3306, false),
    ORACLE("Oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@%s:%d:%s", 1521, false),
    SQLSERVER("SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false", 1433, false),
    SQLITE("SQLite", "org.sqlite.JDBC", "jdbc:sqlite:%s", 0, false),
    DYNAMODB("DynamoDB", null, null, 0, true);

    private final String displayName;
    private final String driverClass;
    private final String urlTemplate;
    private final int defaultPort;
    private final boolean noSql;

    DatabaseType(String displayName, String driverClass, String urlTemplate,
                 int defaultPort, boolean noSql) {
        this.displayName = displayName;
        this.driverClass = driverClass;
        this.urlTemplate = urlTemplate;
        this.defaultPort = defaultPort;
        this.noSql = noSql;
    }

    public String getDisplayName() { return displayName; }
    public String getDriverClass() { return driverClass; }
    public int getDefaultPort() { return defaultPort; }
    public boolean isNoSql() { return noSql; }

    public String buildUrl(String host, int port, String database) {
        if (urlTemplate == null) return displayName; // NoSQL types
        if (this == SQLITE) return String.format(urlTemplate, database); // file path only
        return String.format(urlTemplate, host, port, database);
    }

    @Override
    public String toString() { return displayName; }
}

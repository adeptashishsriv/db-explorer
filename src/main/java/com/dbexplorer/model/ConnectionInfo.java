package com.dbexplorer.model;

import java.util.UUID;

public class ConnectionInfo {
    private String id;
    private String name;
    private DatabaseType dbType;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String driverPath;

    // AWS DynamoDB fields
    private String awsRegion;
    private String awsAccessKey;
    private String awsSecretKey;
    private String awsEndpoint; // optional, for local DynamoDB

    public ConnectionInfo() {
        this.id = UUID.randomUUID().toString();
    }

    public ConnectionInfo(String name, DatabaseType dbType, String host, int port,
                          String database, String username, String password) {
        this();
        this.name = name;
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DatabaseType getDbType() { return dbType; }
    public void setDbType(DatabaseType dbType) { this.dbType = dbType; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDriverPath() { return driverPath; }
    public void setDriverPath(String driverPath) { this.driverPath = driverPath; }

    public String getAwsRegion() { return awsRegion; }
    public void setAwsRegion(String awsRegion) { this.awsRegion = awsRegion; }
    public String getAwsAccessKey() { return awsAccessKey; }
    public void setAwsAccessKey(String awsAccessKey) { this.awsAccessKey = awsAccessKey; }
    public String getAwsSecretKey() { return awsSecretKey; }
    public void setAwsSecretKey(String awsSecretKey) { this.awsSecretKey = awsSecretKey; }
    public String getAwsEndpoint() { return awsEndpoint; }
    public void setAwsEndpoint(String awsEndpoint) { this.awsEndpoint = awsEndpoint; }

    public String getJdbcUrl() {
        if (dbType != null && dbType.isNoSql()) {
            return dbType.getDisplayName() + " [" + (awsRegion != null ? awsRegion : "local") + "]";
        }
        return dbType.buildUrl(host, port, database);
    }

    @Override
    public String toString() {
        return name + " [" + dbType.getDisplayName() + "]";
    }
}

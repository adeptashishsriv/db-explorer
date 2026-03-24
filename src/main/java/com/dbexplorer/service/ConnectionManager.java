package com.dbexplorer.service;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.model.DatabaseType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {

    private static final Path CONFIG_FILE = Path.of(System.getProperty("user.home"),
            ".dbexplorer", "connections.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<ConnectionInfo> savedConnections = new ArrayList<>();
    private final Map<String, Connection> activeConnections = new ConcurrentHashMap<>();
    private volatile DynamoDbExecutor dynamoDbExecutor;

    public ConnectionManager() {
        loadConnections();
    }

    // --- Persistence ---

    public void loadConnections() {
        savedConnections.clear();
        if (Files.exists(CONFIG_FILE)) {
            try {
                String json = Files.readString(CONFIG_FILE);
                List<ConnectionInfo> loaded = GSON.fromJson(json,
                        new TypeToken<List<ConnectionInfo>>() {}.getType());
                if (loaded != null) {
                    // Decrypt sensitive fields after loading
                    for (ConnectionInfo info : loaded) {
                        info.setPassword(CryptoUtils.decrypt(info.getPassword()));
                        info.setAwsAccessKey(CryptoUtils.decrypt(info.getAwsAccessKey()));
                        info.setAwsSecretKey(CryptoUtils.decrypt(info.getAwsSecretKey()));
                    }
                    savedConnections.addAll(loaded);
                }
            } catch (IOException e) {
                System.err.println("Failed to load connections: " + e.getMessage());
            }
        }
    }

    public void saveConnections() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            // Create copies with encrypted sensitive fields for persistence
            List<ConnectionInfo> toSave = new ArrayList<>();
            for (ConnectionInfo info : savedConnections) {
                ConnectionInfo copy = cloneConnectionInfo(info);
                copy.setPassword(CryptoUtils.encrypt(copy.getPassword()));
                copy.setAwsAccessKey(CryptoUtils.encrypt(copy.getAwsAccessKey()));
                copy.setAwsSecretKey(CryptoUtils.encrypt(copy.getAwsSecretKey()));
                toSave.add(copy);
            }
            Files.writeString(CONFIG_FILE, GSON.toJson(toSave));
        } catch (IOException e) {
            System.err.println("Failed to save connections: " + e.getMessage());
        }
    }

    /** Create a shallow copy of ConnectionInfo for safe encryption without mutating in-memory objects. */
    private ConnectionInfo cloneConnectionInfo(ConnectionInfo src) {
        ConnectionInfo copy = new ConnectionInfo();
        copy.setId(src.getId());
        copy.setName(src.getName());
        copy.setDbType(src.getDbType());
        copy.setHost(src.getHost());
        copy.setPort(src.getPort());
        copy.setDatabase(src.getDatabase());
        copy.setUsername(src.getUsername());
        copy.setPassword(src.getPassword());
        copy.setDriverPath(src.getDriverPath());
        copy.setAwsRegion(src.getAwsRegion());
        copy.setAwsAccessKey(src.getAwsAccessKey());
        copy.setAwsSecretKey(src.getAwsSecretKey());
        copy.setAwsEndpoint(src.getAwsEndpoint());
        return copy;
    }

    // --- CRUD ---

    public List<ConnectionInfo> getConnections() {
        return Collections.unmodifiableList(savedConnections);
    }

    public void addConnection(ConnectionInfo info) {
        savedConnections.add(info);
        saveConnections();
    }

    public void updateConnection(ConnectionInfo info) {
        savedConnections.removeIf(c -> c.getId().equals(info.getId()));
        savedConnections.add(info);
        saveConnections();
    }

    public void deleteConnection(String id) {
        disconnect(id);
        savedConnections.removeIf(c -> c.getId().equals(id));
        saveConnections();
    }

    // --- Connect / Disconnect ---

    public Connection connect(ConnectionInfo info) throws SQLException {
        if (info.getDbType() == DatabaseType.DYNAMODB) {
            getDynamoDbExecutor().connect(info);
            return null; // No JDBC connection for DynamoDB
        }
        try {
            Class.forName(info.getDbType().getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + info.getDbType().getDriverClass()
                    + ". Ensure the driver JAR is on the classpath.", e);
        }
        Connection conn = DriverManager.getConnection(
                info.getJdbcUrl(), info.getUsername(), info.getPassword());
        activeConnections.put(info.getId(), conn);
        return conn;
    }

    public void disconnect(String id) {
        Connection conn = activeConnections.remove(id);
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        if (dynamoDbExecutor != null) {
            dynamoDbExecutor.disconnect(id);
        }
    }

    public Connection getActiveConnection(String id) {
        return activeConnections.get(id);
    }

    public boolean isConnected(String id) {
        // Check DynamoDB first
        if (dynamoDbExecutor != null && dynamoDbExecutor.isConnected(id)) return true;
        // Then JDBC
        Connection conn = activeConnections.get(id);
        if (conn == null) return false;
        try {
            return !conn.isClosed();
        } catch (SQLException e) {
            activeConnections.remove(id);
            return false;
        }
    }

    public Connection testConnection(ConnectionInfo info) throws SQLException {
        try {
            Class.forName(info.getDbType().getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + info.getDbType().getDriverClass(), e);
        }
        Connection conn = DriverManager.getConnection(
                info.getJdbcUrl(), info.getUsername(), info.getPassword());
        conn.close();
        return null;
    }

    public void testDynamoConnection(ConnectionInfo info) {
        getDynamoDbExecutor().testConnection(info);
    }

    public DynamoDbExecutor getDynamoDbExecutor() {
        if (dynamoDbExecutor == null) {
            synchronized (this) {
                if (dynamoDbExecutor == null) {
                    dynamoDbExecutor = new DynamoDbExecutor();
                }
            }
        }
        return dynamoDbExecutor;
    }

    public void disconnectAll() {
        activeConnections.forEach((id, conn) -> {
            try { conn.close(); } catch (SQLException ignored) {}
        });
        activeConnections.clear();
        if (dynamoDbExecutor != null) {
            dynamoDbExecutor.shutdown();
            dynamoDbExecutor = null;
        }
    }
}

package com.dbexplorer.service;

import com.dbexplorer.model.ConnectionInfo;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Executes PartiQL statements against DynamoDB and returns tabular results.
 */
public class DynamoDbExecutor {

    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(20),
            r -> { Thread t = new Thread(r, "dynamo-exec"); t.setDaemon(true); return t; }
    );

    private final Map<String, DynamoDbClient> clients = new ConcurrentHashMap<>();

    public DynamoDbClient connect(ConnectionInfo info) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();

        if (info.getAwsRegion() != null && !info.getAwsRegion().isBlank()) {
            builder.region(Region.of(info.getAwsRegion()));
        }
        if (info.getAwsAccessKey() != null && !info.getAwsAccessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(info.getAwsAccessKey(), info.getAwsSecretKey())));
        }
        if (info.getAwsEndpoint() != null && !info.getAwsEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(info.getAwsEndpoint()));
        }

        DynamoDbClient client = builder.build();
        clients.put(info.getId(), client);
        return client;
    }

    public void disconnect(String connectionId) {
        DynamoDbClient client = clients.remove(connectionId);
        if (client != null) client.close();
    }

    public boolean isConnected(String connectionId) {
        return clients.containsKey(connectionId);
    }

    public DynamoDbClient getClient(String connectionId) {
        return clients.get(connectionId);
    }

    public void testConnection(ConnectionInfo info) {
        DynamoDbClient client = null;
        try {
            client = connect(info);
            client.listTables(ListTablesRequest.builder().limit(1).build());
        } finally {
            if (client != null) {
                clients.remove(info.getId());
                client.close();
            }
        }
    }

    /** List all DynamoDB table names (used for schema explorer). */
    public List<String> listTables(String connectionId) {
        DynamoDbClient client = clients.get(connectionId);
        if (client == null) return List.of();
        List<String> tables = new ArrayList<>();
        String lastKey = null;
        do {
            ListTablesRequest.Builder req = ListTablesRequest.builder().limit(100);
            if (lastKey != null) req.exclusiveStartTableName(lastKey);
            ListTablesResponse resp = client.listTables(req.build());
            tables.addAll(resp.tableNames());
            lastKey = resp.lastEvaluatedTableName();
        } while (lastKey != null);
        Collections.sort(tables);
        return tables;
    }

    /**
     * Execute a PartiQL statement asynchronously.
     * Returns columns + rows for display in ResultPanel.
     */
    public Future<?> executePartiQLAsync(String connectionId, String partiQL,
                                          Consumer<PartiQLResult> onSuccess,
                                          Consumer<Exception> onError) {
        return executor.submit(() -> {
            try {
                long start = System.currentTimeMillis();
                DynamoDbClient client = clients.get(connectionId);
                if (client == null) throw new RuntimeException("Not connected");

                ExecuteStatementResponse resp = client.executeStatement(
                        ExecuteStatementRequest.builder().statement(partiQL).build());

                long elapsed = System.currentTimeMillis() - start;
                List<Map<String, AttributeValue>> items = resp.items();

                // Collect all unique column names across all items
                LinkedHashSet<String> colSet = new LinkedHashSet<>();
                for (Map<String, AttributeValue> item : items) {
                    colSet.addAll(item.keySet());
                }
                List<String> columns = new ArrayList<>(colSet);

                // Build rows
                List<List<Object>> rows = new ArrayList<>();
                for (Map<String, AttributeValue> item : items) {
                    List<Object> row = new ArrayList<>();
                    for (String col : columns) {
                        AttributeValue av = item.get(col);
                        row.add(av != null ? formatAttributeValue(av) : null);
                    }
                    rows.add(row);
                }

                onSuccess.accept(new PartiQLResult(columns, rows, elapsed, items.size()));
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    private String formatAttributeValue(AttributeValue av) {
        if (av.s() != null) return av.s();
        if (av.n() != null) return av.n();
        if (av.bool() != null) return av.bool().toString();
        if (av.hasSs()) return av.ss().toString();
        if (av.hasNs()) return av.ns().toString();
        if (av.hasL()) return av.l().toString();
        if (av.hasM()) return av.m().toString();
        if (av.nul() != null && av.nul()) return "NULL";
        if (av.b() != null) return "(binary)";
        return av.toString();
    }

    public void disconnectAll() {
        clients.forEach((id, c) -> c.close());
        clients.clear();
    }

    public void shutdown() {
        disconnectAll();
        executor.shutdownNow();
    }

    /** Result holder for PartiQL execution. */
    public record PartiQLResult(List<String> columns, List<List<Object>> rows,
                                 long executionTimeMs, int rowCount) {}
}

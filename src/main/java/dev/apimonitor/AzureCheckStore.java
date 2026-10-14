package dev.apimonitor;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.azure.identity.DefaultAzureCredentialBuilder;
import java.util.UUID;

public class AzureCheckStore {

    private final TableClient checkClient;
    private final TableClient stateClient;

    public AzureCheckStore() {
        String accountName = System.getenv("MONITOR_STORAGE_ACCOUNT");

        if (accountName == null || accountName.isBlank()) {
            throw new IllegalStateException(
                    "Set MONITOR_STORAGE_ACCOUNT before running"
            );
        }

        String endpoint =
                "https://" + accountName + ".table.core.windows.net";

        checkClient = buildClient(endpoint, "HealthChecks");
        stateClient = buildClient(endpoint, "MonitorState");
    }

    private TableClient buildClient(String endpoint, String tableName) {
        return new TableClientBuilder()
                .endpoint(endpoint)
                .tableName(tableName)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    public void save(CheckResult result) {
        TableEntity entity = new TableEntity(
                result.endpointName(),
                UUID.randomUUID().toString()
        );

        entity.addProperty("Url", result.url());
        entity.addProperty("CheckedAt", result.checkedAt().toString());
        entity.addProperty("LatencyMs", result.latencyMs());
        entity.addProperty("Health", result.health().name());

        if (result.statusCode() != null) {
            entity.addProperty("StatusCode", result.statusCode());
        }

        if (result.error() != null) {
            entity.addProperty("Error", result.error());
        }

        checkClient.createEntity(entity);

        TableEntity latest = new TableEntity(
                "latest", result.endpointName()
        );
                latest.addProperty("Url", result.url());
        latest.addProperty("CheckedAt", result.checkedAt().toString());
        latest.addProperty("LatencyMs", result.latencyMs());
        latest.addProperty("Health", result.health().name());

        if (result.statusCode() != null) {
            latest.addProperty("StatusCode", result.statusCode());
        }
        if (result.error() != null) {
            latest.addProperty("Error", result.error());
        }

        stateClient.upsertEntityWithResponse(
                latest,
                com.azure.data.tables.models.TableEntityUpdateMode.REPLACE,
                null,
                com.azure.core.util.Context.NONE
        );
    }

    public CheckResult loadLatest(String endpointName) {
        try {
            TableEntity entity =
                    stateClient.getEntity("latest", endpointName);

            Object status = entity.getProperty("StatusCode");

            return new CheckResult(
                    endpointName,
                    (String) entity.getProperty("Url"),
                    java.time.Instant.parse(
                            (String) entity.getProperty("CheckedAt")
                    ),
                    status == null ? null : ((Number) status).intValue(),
                    ((Number) entity.getProperty("LatencyMs")).longValue(),
                    HealthStatus.valueOf(
                            (String) entity.getProperty("Health")
                    ),
                    (String) entity.getProperty("Error")
            );

        } catch (TableServiceException e) {
            String code = e.getValue() == null
                    ? ""
                    : String.valueOf(e.getValue().getErrorCode());

            boolean missingRecord =
                    "ResourceNotFound".equals(code)
                    || "EntityNotFound".equals(code);

            if (e.getResponse().getStatusCode() == 404 && missingRecord) {
                return null;
            }

            throw e;
        }
    }
    public String loadDemoMode() {
        try {
            String mode = (String) stateClient
                    .getEntity("demo", "mode").getProperty("Mode");

            if (!"healthy".equals(mode) && !"failed".equals(mode)) {
                throw new IllegalStateException("Invalid stored demo mode");
            }
            return mode;
        } catch (TableServiceException e) {
            if (missingDemoRecord(e)) {
                return "healthy";
            }
            throw e;
        }
    }

    public void saveDemoMode(String mode) {
        if (!"healthy".equals(mode) && !"failed".equals(mode)) {
            throw new IllegalArgumentException("Invalid demo mode");
        }
        TableEntity entity = new TableEntity("demo", "mode");
        entity.addProperty("Mode", mode);
        stateClient.upsertEntity(entity);
    }

    public String acquireDemoLease() {
        String owner = UUID.randomUUID().toString();
        String expires = java.time.Instant.now()
                .plusSeconds(240).toString();

        TableEntity lease = new TableEntity("demo", "lease");
        lease.addProperty("Owner", owner);
        lease.addProperty("ExpiresAt", expires);

        try {
            stateClient.createEntity(lease);
            return owner;
        } catch (TableServiceException e) {
            if (e.getResponse().getStatusCode() != 409) {
                throw e;
            }
        }

        try {
            TableEntity existing = stateClient.getEntity("demo", "lease");
            java.time.Instant expiry = java.time.Instant.parse(
                    (String) existing.getProperty("ExpiresAt")
            );

            if (expiry.isAfter(java.time.Instant.now())) {
                return null;
            }

            existing.addProperty("Owner", owner);
            existing.addProperty("ExpiresAt", expires);

            stateClient.updateEntityWithResponse(
                    existing,
                    com.azure.data.tables.models.TableEntityUpdateMode.REPLACE,
                    true,
                    null,
                    com.azure.core.util.Context.NONE
            );
            return owner;

        } catch (TableServiceException e) {
            int status = e.getResponse().getStatusCode();
            if (status == 404 || status == 409 || status == 412) {
                return null;
            }
            throw e;
        }
    }

    public void releaseDemoLease(String owner) {
        try {
            TableEntity lease = stateClient.getEntity("demo", "lease");

            if (owner.equals(lease.getProperty("Owner"))) {
                stateClient.deleteEntityWithResponse(
                        lease, true, null,
                        com.azure.core.util.Context.NONE
                );
            }
        } catch (TableServiceException e) {
            int status = e.getResponse().getStatusCode();
            if (status != 404 && status != 412) {
                throw e;
            }
        }
    }

    private boolean missingDemoRecord(TableServiceException e) {
        String code = e.getValue() == null
                ? ""
                : String.valueOf(e.getValue().getErrorCode());

        return e.getResponse().getStatusCode() == 404
                && ("ResourceNotFound".equals(code)
                || "EntityNotFound".equals(code));
    }
    public java.util.List<java.util.Map<String, Object>> loadDashboardHistory() {
        String cutoff = java.time.Instant.now()
                .minusSeconds(3600).toString();

        java.util.List<java.util.Map<String, Object>> checks =
                new java.util.ArrayList<>();

        for (String name : java.util.List.of(
                "healthy-api", "error-api", "slow-api",
                "timeout-api", "demo-api"
        )) {
            var options = new com.azure.data.tables.models.ListEntitiesOptions()
                    .setFilter("PartitionKey eq '" + name
                            + "' and CheckedAt ge '" + cutoff + "'")
                    .setTop(100);

            for (TableEntity entity : checkClient.listEntities(
                    options, null, com.azure.core.util.Context.NONE
            )) {
                java.util.Map<String, Object> check =
                        new java.util.LinkedHashMap<>();

                check.put("name", entity.getPartitionKey());
                check.put("checkedAt", entity.getProperty("CheckedAt"));
                check.put("health", entity.getProperty("Health"));
                check.put("statusCode", entity.getProperty("StatusCode"));
                check.put("latencyMs", entity.getProperty("LatencyMs"));
                check.put("url", entity.getProperty("Url"));
                check.put("error", entity.getProperty("Error"));

                checks.add(check);
            }
        }

        checks.sort((a, b) -> java.time.Instant.parse(
                (String) b.get("checkedAt")
        ).compareTo(java.time.Instant.parse(
                (String) a.get("checkedAt")
        )));

        return new java.util.ArrayList<>(
                checks.subList(0, Math.min(100, checks.size()))
        );
    }

    public void saveDemoEvents(
            CheckResult result,
            java.util.List<String> notifications
    ) {
        java.util.List<String> messages = new java.util.ArrayList<>(
                notifications
        );

        messages.add(result.endpointName() + ": " + result.health()
                + " | HTTP " + (result.statusCode() == null
                        ? "-" : result.statusCode())
                + " | " + result.latencyMs() + " ms"
                + (result.error() == null
                        ? "" : " | " + result.error()));

        String groupKey = String.format(
                "%019d", Long.MAX_VALUE - result.checkedAt().toEpochMilli()
        ) + "-" + UUID.randomUUID();

        java.util.List<com.azure.data.tables.models.TableTransactionAction>
                actions = new java.util.ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            TableEntity event = new TableEntity(
                    "demo-events", groupKey + "-" + String.format("%03d", i)
            );

            event.addProperty("CheckedAt", result.checkedAt().toString());
            event.addProperty("Message", messages.get(i));
            event.addProperty("EndpointName", result.endpointName());

            actions.add(
                    new com.azure.data.tables.models.TableTransactionAction(
                            com.azure.data.tables.models.TableTransactionActionType.CREATE,
                            event
                    )
            );
        }

        stateClient.submitTransaction(actions);
    }

    public java.util.List<java.util.Map<String, Object>> loadDemoEvents() {
        var options = new com.azure.data.tables.models.ListEntitiesOptions()
                .setFilter("PartitionKey eq 'demo-events'")
                .setTop(30);

        java.util.List<java.util.Map<String, Object>> events =
                new java.util.ArrayList<>();

        for (TableEntity entity : stateClient.listEntities(
                options, null, com.azure.core.util.Context.NONE
        )) {
            java.util.Map<String, Object> event =
                    new java.util.LinkedHashMap<>();

            event.put("id", entity.getRowKey());
            event.put("at", entity.getProperty("CheckedAt"));
            event.put("text", entity.getProperty("Message"));

            events.add(event);
            if (events.size() == 30) {
                break;
            }
        }

        return events;
    }
    public MonitorState loadState(String endpointName) {
        try {
            TableEntity entity =
                    stateClient.getEntity("endpoint", endpointName);

            MonitorState state = new MonitorState();

            state.consecutiveFailures = ((Number)
                    entity.getProperty("ConsecutiveFailures")).intValue();

            state.consecutiveSlowChecks = ((Number)
                    entity.getProperty("ConsecutiveSlowChecks")).intValue();

            state.outageAlerted =
                    (Boolean) entity.getProperty("OutageAlerted");

            state.slowAlerted =
                    (Boolean) entity.getProperty("SlowAlerted");

            state.rateAlerted =
                    (Boolean) entity.getProperty("RateAlerted");

            state.recentFailures =
                    (String) entity.getProperty("RecentFailures");

            return state;

        } catch (TableServiceException e) {
            String code = e.getValue() == null
            ? ""
            : String.valueOf(e.getValue().getErrorCode());

            boolean missingRecord =
                    "ResourceNotFound".equals(code)
                    || "EntityNotFound".equals(code);

            if (e.getResponse().getStatusCode() == 404
                    && missingRecord) {
                return new MonitorState();
            }

            throw e;
        }
    }

    public void saveState(String endpointName, MonitorState state) {
        TableEntity entity = new TableEntity("endpoint", endpointName);

        entity.addProperty(
                "ConsecutiveFailures", state.consecutiveFailures
        );
        entity.addProperty(
                "ConsecutiveSlowChecks", state.consecutiveSlowChecks
        );
        entity.addProperty("OutageAlerted", state.outageAlerted);
        entity.addProperty("SlowAlerted", state.slowAlerted);
        entity.addProperty("RateAlerted", state.rateAlerted);
        entity.addProperty("RecentFailures", state.recentFailures);

        stateClient.upsertEntity(entity);
    }
}
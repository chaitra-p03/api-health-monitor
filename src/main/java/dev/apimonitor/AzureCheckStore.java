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
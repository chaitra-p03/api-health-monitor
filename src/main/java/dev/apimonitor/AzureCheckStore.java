package dev.apimonitor;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.identity.AzureCliCredentialBuilder;

import java.util.UUID;

public class AzureCheckStore {

    private final TableClient client;

    public AzureCheckStore() {
        String accountName = System.getenv("MONITOR_STORAGE_ACCOUNT");

        if (accountName == null || accountName.isBlank()) {
            throw new IllegalStateException(
                    "Set MONITOR_STORAGE_ACCOUNT before running"
            );
        }

        client = new TableClientBuilder()
                .endpoint(
                        "https://" + accountName + ".table.core.windows.net"
                )
                .tableName("HealthChecks")
                .credential(new AzureCliCredentialBuilder().build())
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

        client.createEntity(entity);
    }
}
package dev.apimonitor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;

public class ApiChecker {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CheckResult check(EndpointConfig endpoint)
            throws InterruptedException {

        Instant checkedAt = Instant.now();
        long start = System.nanoTime();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.url()))
                    .timeout(Duration.ofSeconds(endpoint.timeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            long latencyMs = elapsedMillis(start);
            int status = response.statusCode();

            HealthStatus health;
            if (status != endpoint.expectedStatus()) {
                health = HealthStatus.FAILED;
            } else if (latencyMs > endpoint.latencyThresholdMs()) {
                health = HealthStatus.SLOW;
            } else {
                health = HealthStatus.HEALTHY;
            }

            return new CheckResult(
                    endpoint.name(), endpoint.url(), checkedAt,
                    status, latencyMs, health, null
            );

        } catch (HttpTimeoutException e) {
            return failure(endpoint, checkedAt, start, "timeout");

        } catch (IOException e) {
            return failure(
                    endpoint, checkedAt, start,
                    e.getClass().getSimpleName()
            );

        } catch (IllegalArgumentException e) {
            return failure(
                    endpoint, checkedAt, start, "invalid configuration"
            );
        }
    }

    private CheckResult failure(
            EndpointConfig endpoint,
            Instant checkedAt,
            long start,
            String error
    ) {
        return new CheckResult(
                endpoint.name(), endpoint.url(), checkedAt,
                null, elapsedMillis(start), HealthStatus.FAILED, error
        );
    }

    private long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
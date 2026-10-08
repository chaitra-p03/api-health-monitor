package dev.apimonitor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class App {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void main(String[] args) {
        List<EndpointConfig> endpoints = List.of(
                new EndpointConfig(
                        "healthy-api",
                        "http://localhost:18080/healthy",
                        200, 5, 2000
                ),
                new EndpointConfig(
                        "error-api",
                        "http://localhost:18080/error",
                        200, 5, 2000
                ),
                new EndpointConfig(
                        "slow-api",
                        "http://localhost:18080/slow",
                        200, 5, 2000
                ),
                new EndpointConfig(
                        "timeout-api",
                        "http://localhost:18080/timeout",
                        200, 5, 2000
                )
        );

        FailureTracker failureTracker = new FailureTracker(3);
        CheckHistory history = new CheckHistory(10);
        FailureRateTracker rateTracker = new FailureRateTracker(30);
        SlowResponseTracker slowTracker = new SlowResponseTracker(3);

        int totalRounds = 13;

        try {
            for (int round = 1; round <= totalRounds; round++) {
                System.out.println(
                        "\n--- Monitoring round " + round + " ---"
                );

                for (EndpointConfig endpoint : endpoints) {
                    CheckResult result = checkEndpoint(endpoint);
                    System.out.println(result);

                    CheckHistory.Summary summary = history.add(result);

                    System.out.printf(
                            "name=%s | window=%d | failures=%d"
                                    + " | failureRate=%.1f%% | windowFull=%s%n",
                            result.endpointName(),
                            summary.checks(),
                            summary.failures(),
                            summary.failureRatePercent(),
                            summary.windowFull()
                    );

                    printNotification(failureTracker.evaluate(result));
                    printNotification(slowTracker.evaluate(result));

                    printNotification(rateTracker.evaluate(
                            result.endpointName(), summary
                    ));
                }

                if (round < totalRounds) {
                    Thread.sleep(2000);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Monitoring interrupted.");
        }
    }

    private static CheckResult checkEndpoint(EndpointConfig endpoint)
            throws InterruptedException {

        Instant checkedAt = Instant.now();
        long start = System.nanoTime();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.url()))
                    .timeout(Duration.ofSeconds(endpoint.timeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<Void> response = CLIENT.send(
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
                    endpoint.name(),
                    endpoint.url(),
                    checkedAt,
                    status,
                    latencyMs,
                    health,
                    null
            );

        } catch (HttpTimeoutException e) {
            return failureResult(endpoint, checkedAt, start, "timeout");

        } catch (IOException e) {
            return failureResult(
                    endpoint, checkedAt, start,
                    e.getClass().getSimpleName()
            );

        } catch (IllegalArgumentException e) {
            return failureResult(
                    endpoint, checkedAt, start,
                    "invalid configuration"
            );
        }
    }

    private static CheckResult failureResult(
            EndpointConfig endpoint,
            Instant checkedAt,
            long start,
            String error
    ) {
        return new CheckResult(
                endpoint.name(),
                endpoint.url(),
                checkedAt,
                null,
                elapsedMillis(start),
                HealthStatus.FAILED,
                error
        );
    }

    private static void printNotification(String notification) {
        if (notification != null) {
            System.out.println(notification);
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
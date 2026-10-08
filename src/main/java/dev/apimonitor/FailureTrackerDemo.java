package dev.apimonitor;

import java.time.Instant;

public class FailureTrackerDemo {

    public static void main(String[] args) {
        FailureTracker tracker = new FailureTracker(3);

        HealthStatus[] sequence = {
                HealthStatus.FAILED,
                HealthStatus.FAILED,
                HealthStatus.FAILED,
                HealthStatus.FAILED,
                HealthStatus.HEALTHY,
                HealthStatus.HEALTHY
        };

        for (HealthStatus health : sequence) {
            CheckResult result = new CheckResult(
                    "demo-api",
                    "http://localhost:18080/healthy",
                    Instant.now(),
                    health == HealthStatus.FAILED ? 500 : 200,
                    20,
                    health,
                    null
            );

            String notification = tracker.evaluate(result);

            System.out.printf(
                    "health=%s | consecutiveFailures=%d%n",
                    health,
                    tracker.consecutiveFailures(result.endpointName())
            );

            if (notification != null) {
                System.out.println(notification);
            }
        }
    }
}
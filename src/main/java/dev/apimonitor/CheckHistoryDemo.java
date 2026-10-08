package dev.apimonitor;

import java.time.Instant;

public class CheckHistoryDemo {

    public static void main(String[] args) {
        CheckHistory history = new CheckHistory(10);
        FailureRateTracker rateTracker = new FailureRateTracker(30);

        for (int check = 1; check <= 13; check++) {
            // Checks 1–3 and 11 fail.
            boolean failed = check <= 3 || check == 11;

            CheckResult result = new CheckResult(
                    "demo-api",
                    "http://localhost:18080/healthy",
                    Instant.now(),
                    failed ? 500 : 200,
                    20,
                    failed ? HealthStatus.FAILED : HealthStatus.HEALTHY,
                    null
            );

            CheckHistory.Summary summary = history.add(result);

            System.out.printf(
                    "check=%d | window=%d | failures=%d"
                            + " | failureRate=%.1f%% | windowFull=%s%n",
                    check,
                    summary.checks(),
                    summary.failures(),
                    summary.failureRatePercent(),
                    summary.windowFull()
            );

            String notification = rateTracker.evaluate(
                    result.endpointName(), summary
            );

            if (notification != null) {
                System.out.println(notification);
            }
        }
    }
}
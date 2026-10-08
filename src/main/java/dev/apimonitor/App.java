package dev.apimonitor;

import java.util.List;

public class App {

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

        AzureCheckStore store = new AzureCheckStore();
        ApiChecker checker = new ApiChecker();

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
                    CheckResult result = checker.check(endpoint);
                    System.out.println(result);

                    store.save(result);
                    System.out.println("Saved to Azure");

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

    private static void printNotification(String notification) {
        if (notification != null) {
            System.out.println(notification);
        }
    }
}
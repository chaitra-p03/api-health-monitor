package dev.apimonitor;

import java.util.List;

public class App {

    public static void main(String[] args) {
        List<EndpointConfig> endpoints = List.of(
        new EndpointConfig(
                "healthy-api",
                configuredUrl("HEALTHY_API_URL",
                        "http://localhost:18080/healthy"),
                200, 5, 2000
        ),
        new EndpointConfig(
                "error-api",
                configuredUrl("ERROR_API_URL",
                        "http://localhost:18080/error"),
                200, 5, 2000
        ),
        new EndpointConfig(
                "slow-api",
                configuredUrl("SLOW_API_URL",
                        "http://localhost:18080/slow"),
                200, 5, 2000
        ),
        new EndpointConfig(
                "timeout-api",
                configuredUrl("TIMEOUT_API_URL",
                        "http://localhost:18080/timeout"),
                200, 5, 2000
        )
    );

        AzureCheckStore store = new AzureCheckStore();
        ApiChecker checker = new ApiChecker();
        MonitorRules rules = new MonitorRules();

        try {
            for (EndpointConfig endpoint : endpoints) {
                MonitorState state = store.loadState(endpoint.name());

                CheckResult result = checker.check(endpoint);
                System.out.println(result);

                store.save(result);

                List<String> notifications = rules.evaluate(result, state);
                store.saveState(endpoint.name(), state);

                System.out.printf(
                        "Saved to Azure | consecutiveFailures=%d"
                                + " | consecutiveSlowChecks=%d"
                                + " | recentFailures=%s%n",
                        state.consecutiveFailures,
                        state.consecutiveSlowChecks,
                        state.recentFailures
                );

                for (String notification : notifications) {
                    System.out.println(notification);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Monitoring interrupted.");
        }
    }
    private static String configuredUrl(String setting, String fallback) {
        String value = System.getenv(setting);
        return value == null || value.isBlank() ? fallback : value;
    } 
}
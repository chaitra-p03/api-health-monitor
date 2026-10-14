package dev.apimonitor;

import com.azure.core.util.BinaryData;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DashboardDataFunction {

    private static AzureCheckStore store;

    static synchronized AzureCheckStore store() {
        if (store == null) {
            store = new AzureCheckStore();
        }
        return store;
    }

    @FunctionName("DashboardData")
    public HttpResponseMessage data(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "dashboard/data"
            ) HttpRequestMessage<Optional<String>> request,
            ExecutionContext context
    ) {
        try {
            List<Map<String, Object>> apis = new ArrayList<>();

            for (String name : List.of(
                    "healthy-api", "error-api", "slow-api",
                    "timeout-api", "demo-api"
            )) {
                apis.add(apiStatus(name));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("generatedAt", Instant.now().toString());
            response.put("apis", apis);
            response.put("history", store().loadDashboardHistory());
            response.put("events", store().loadDemoEvents());

            return json(request, HttpStatus.OK, response);

        } catch (RuntimeException e) {
            context.getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Could not load dashboard data", e
            );
            return json(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("error", "Could not load monitoring data."));
        }
    }

    static Map<String, Object> apiStatus(String name) {
        AzureCheckStore storage = store();
        CheckResult result = storage.loadLatest(name);
        MonitorState state = storage.loadState(name);

        Map<String, Object> api = new LinkedHashMap<>();
        api.put("name", name);
        api.put("demo", name.equals("demo-api"));
        api.put("health", result == null
                ? "NOT_CHECKED" : result.health().name());
        api.put("url", result == null ? null : result.url());
        api.put("checkedAt", result == null
                ? null : result.checkedAt().toString());
        api.put("statusCode", result == null
                ? null : result.statusCode());
        api.put("latencyMs", result == null
                ? null : result.latencyMs());
        api.put("error", result == null ? null : result.error());

        api.put("consecutiveFailures", state.consecutiveFailures);
        api.put("consecutiveSlowChecks", state.consecutiveSlowChecks);
        api.put("outageAlerted", state.outageAlerted);
        api.put("slowAlerted", state.slowAlerted);
        api.put("rateAlerted", state.rateAlerted);

        int windowSize = state.recentFailures.length();
        long failures = state.recentFailures.chars()
                .filter(value -> value == '1').count();

        api.put("windowSize", windowSize);
        api.put("windowFull", windowSize == 10);
        api.put("failureRatePercent", windowSize == 0 ? 0.0
                : failures * 100.0 / windowSize);

        return api;
    }

    static HttpResponseMessage json(
            HttpRequestMessage<?> request,
            HttpStatus status,
            Object body
    ) {
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Cache-Control", "no-store")
                .body(BinaryData.fromObject(body).toString())
                .build();
    }
}
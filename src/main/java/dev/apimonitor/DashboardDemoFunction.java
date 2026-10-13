package dev.apimonitor;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DashboardDemoFunction {

    @FunctionName("DashboardDemo")
    public HttpResponseMessage demo(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "dashboard/demo"
            ) HttpRequestMessage<Optional<String>> request,
            ExecutionContext context
    ) {
        String action = request.getQueryParameters().get("action");

        if (!List.of("fail", "restore", "check").contains(
                action == null ? "" : action
        )) {
            return DashboardDataFunction.json(
                    request, HttpStatus.BAD_REQUEST,
                    Map.of("error", "Choose fail, restore, or check.")
            );
        }

        AzureCheckStore storage = null;
        String lease = null;

        try {
            storage = DashboardDataFunction.store();
            lease = storage.acquireDemoLease();

            if (lease == null) {
                return DashboardDataFunction.json(
                        request, HttpStatus.CONFLICT,
                        Map.of("error",
                                "Another demo check is running. Try again shortly.")
                );
            }

            String mode = switch (action) {
                case "fail" -> "failed";
                case "restore" -> "healthy";
                default -> storage.loadDemoMode();
            };

            if (!action.equals("check")) {
                storage.saveDemoMode(mode);
            }

            String url = mode.equals("failed")
                    ? "https://httpbingo.org/status/500"
                    : "https://httpbingo.org/status/200";

            EndpointConfig endpoint = new EndpointConfig(
                    "demo-api", url, 200, 5, 2000
            );

            MonitorState state = storage.loadState("demo-api");
            CheckResult result = new ApiChecker().check(endpoint);
            List<String> notifications =
                    new MonitorRules().evaluate(result, state);

            storage.save(result);
            storage.saveState("demo-api", state);

            context.getLogger().info(result.toString());
            for (String notification : notifications) {
                context.getLogger().info(notification);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("mode", mode);
            response.put("api",
                    DashboardDataFunction.apiStatus("demo-api"));
            response.put("notifications", notifications);

            return DashboardDataFunction.json(
                    request, HttpStatus.OK, response
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DashboardDataFunction.json(
                    request, HttpStatus.SERVICE_UNAVAILABLE,
                    Map.of("error", "Demo check was interrupted.")
            );

        } catch (RuntimeException e) {
            context.getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Demo check failed", e
            );
            return DashboardDataFunction.json(
                    request, HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("error",
                            "Demo could not complete. Refresh before retrying.")
            );

        } finally {
            if (storage != null && lease != null) {
                try {
                    storage.releaseDemoLease(lease);
                } catch (RuntimeException e) {
                    context.getLogger().log(
                            java.util.logging.Level.WARNING,
                            "Demo lock cleanup failed", e
                    );
                }
            }
        }
    }
}
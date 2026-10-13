package dev.apimonitor;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class DashboardPageFunction {

    @FunctionName("DashboardPage")
    public HttpResponseMessage page(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "dashboard"
            ) HttpRequestMessage<Optional<String>> request,
            ExecutionContext context
    ) {
        try (InputStream page = getClass().getResourceAsStream(
                "/dashboard.html"
        )) {
            if (page == null) {
                throw new IllegalStateException(
                        "dashboard.html is missing from application resources"
                );
            }

            String html = new String(
                    page.readAllBytes(), StandardCharsets.UTF_8
            );

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "text/html; charset=utf-8")
                    .header("Cache-Control", "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy",
                            "default-src 'self'; "
                            + "script-src 'self' 'unsafe-inline'; "
                            + "style-src 'self' 'unsafe-inline'; "
                            + "connect-src 'self'; "
                            + "frame-ancestors 'none'; "
                            + "base-uri 'none'; form-action 'self'")
                    .body(html)
                    .build();

        } catch (Exception e) {
            context.getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Could not serve dashboard", e
            );

            return request.createResponseBuilder(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body("Dashboard could not be loaded.").build();
        }
    }
}
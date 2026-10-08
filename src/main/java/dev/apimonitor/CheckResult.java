package dev.apimonitor;

import java.time.Instant;

public record CheckResult(
        String endpointName,
        String url,
        Instant checkedAt,
        Integer statusCode,
        long latencyMs,
        HealthStatus health,
        String error
) {}
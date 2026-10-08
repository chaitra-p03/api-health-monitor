package dev.apimonitor;

public record EndpointConfig(
        String name,
        String url,
        int expectedStatus,
        int timeoutSeconds,
        long latencyThresholdMs
) {}
package dev.apimonitor;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ApiCheckerTest {

    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;
    private ApiChecker checker;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);

        route("/healthy", 200, 0);
        route("/error", 500, 0);
        route("/created", 201, 0);
        route("/slow", 200, 300);
        route("/timeout", 200, 2500);

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        checker = new ApiChecker();
    }

    private void route(String path, int status, long delayMs) {
        server.createContext(path, exchange -> {
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                exchange.sendResponseHeaders(status, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private EndpointConfig endpoint(
            String path, int expectedStatus, int timeout, long threshold
    ) {
        return new EndpointConfig(
                "test-api", baseUrl + path,
                expectedStatus, timeout, threshold
        );
    }

    @Test
    void successfulResponseIsHealthy() throws InterruptedException {
        EndpointConfig config = endpoint("/healthy", 200, 5, 60000);
        CheckResult result = checker.check(config);

        assertEquals(HealthStatus.HEALTHY, result.health());
        assertEquals(Integer.valueOf(200), result.statusCode());
        assertNull(result.error());
        assertEquals(config.name(), result.endpointName());
        assertEquals(config.url(), result.url());
        assertNotNull(result.checkedAt());
        assertTrue(result.latencyMs() >= 0);
    }

    @Test
    void unexpectedStatusIsFailed() throws InterruptedException {
        CheckResult result = checker.check(
                endpoint("/error", 200, 5, 60000)
        );

        assertEquals(HealthStatus.FAILED, result.health());
        assertEquals(Integer.valueOf(500), result.statusCode());
        assertNull(result.error());
    }

    @Test
    void configuredExpectedStatusIsAccepted() throws InterruptedException {
        CheckResult result = checker.check(
                endpoint("/created", 201, 5, 60000)
        );

        assertEquals(HealthStatus.HEALTHY, result.health());
        assertEquals(Integer.valueOf(201), result.statusCode());
    }

    @Test
    void responseAboveLatencyThresholdIsSlow() throws InterruptedException {
        CheckResult result = checker.check(
                endpoint("/slow", 200, 5, 100)
        );

        assertEquals(HealthStatus.SLOW, result.health());
        assertEquals(Integer.valueOf(200), result.statusCode());
        assertTrue(result.latencyMs() > 100);
        assertNull(result.error());
    }

    @Test
    void timeoutIsFailedWithTimeoutError() throws InterruptedException {
        CheckResult result = checker.check(
                endpoint("/timeout", 200, 1, 60000)
        );

        assertEquals(HealthStatus.FAILED, result.health());
        assertNull(result.statusCode());
        assertEquals("timeout", result.error());
    }

    @Test
    void invalidUrlReturnsFailedResult() throws InterruptedException {
        CheckResult result = checker.check(new EndpointConfig(
                "invalid-api", "not a valid URL", 200, 5, 2000
        ));

        assertEquals(HealthStatus.FAILED, result.health());
        assertNull(result.statusCode());
        assertEquals("invalid configuration", result.error());
    }
}
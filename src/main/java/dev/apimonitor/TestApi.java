package dev.apimonitor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TestApi {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 18080), 0
        );

        AtomicInteger errorRequests = new AtomicInteger();

        server.createContext("/healthy",
                exchange -> respond(exchange, 200, "OK", 0));

        server.createContext("/error", exchange -> {
            int requestNumber = errorRequests.incrementAndGet();

            if (requestNumber <= 4) {
                respond(exchange, 500, "Simulated failure", 0);
            } else {
                respond(exchange, 200, "Recovered", 0);
            }
        });

        server.createContext("/slow",
                exchange -> respond(exchange, 200, "Slow response", 3000));

        server.createContext("/timeout",
                exchange -> respond(exchange, 200, "Late response", 15000));

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("Test API running at http://localhost:18080");
        System.out.println("/error fails four requests, then recovers");
        System.out.println("Stop it with Ctrl+C");
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String message,
            long delayMs
    ) throws IOException {
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }

            byte[] body = message.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "text/plain; charset=utf-8"
            );

            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } finally {
            exchange.close();
        }
    }
}
package io.pulseengine.ops;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PrometheusMetricsServer implements AutoCloseable {
    private final HttpServer server;
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public PrometheusMetricsServer(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", this::handleMetrics);
            server.setExecutor(null);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start metrics server on port=" + port, e);
        }
    }

    public void start() {
        server.start();
    }

    public void set(String metric, long value) {
        gauges.computeIfAbsent(metric, k -> new AtomicLong()).set(value);
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder(1024);
        for (Map.Entry<String, AtomicLong> e : gauges.entrySet()) {
            sb.append(e.getKey()).append(' ').append(e.getValue().get()).append('\n');
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

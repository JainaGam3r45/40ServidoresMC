package com.cadiducho.cservidoresmc.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestHttpRequester {

    private HttpServer server;
    private ExecutorService executor;
    private HttpRequester requester;
    private TestLogger logger;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        requester = new HttpRequester();
        logger = new TestLogger();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void successfulResponseReturnsBody() throws IOException {
        server.createContext("/ok", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));
        server.start();

        String body = requester.get(url("/ok"), "test ok", new HttpConfig(500, 500, 2, 1), logger);

        assertEquals("{\"status\":\"ok\"}", body);
    }

    @Test
    void notFoundReadsErrorBodyWithoutRetry() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/missing", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 404, "not found");
        });
        server.start();

        HttpException exception = assertThrows(HttpException.class, () ->
                requester.get(url("/missing"), "test missing", new HttpConfig(500, 500, 2, 1), logger));

        assertEquals(404, exception.getStatusCode());
        assertEquals("not found", exception.getBody());
        assertEquals(1, attempts.get());
    }

    @Test
    void serverErrorRetriesAndReturnsSuccessfulRetry() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/flaky", exchange -> {
            if (attempts.incrementAndGet() < 3) {
                respond(exchange, 500, "temporary failure");
                return;
            }

            respond(exchange, 200, "recovered");
        });
        server.start();

        String body = requester.get(url("/flaky"), "test flaky", new HttpConfig(500, 500, 2, 1), logger);

        assertEquals("recovered", body);
        assertEquals(3, attempts.get());
        assertEquals(2, logger.retries());
    }

    @Test
    void timeoutRetriesThenFails() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/slow", exchange -> {
            attempts.incrementAndGet();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "slow");
        });
        server.start();

        assertThrows(SocketTimeoutException.class, () ->
                requester.get(url("/slow"), "test slow", new HttpConfig(50, 50, 2, 1), logger));

        assertEquals(3, attempts.get());
    }

    @Test
    void retryDelayUsesExponentialBackoffWithJitterCap() {
        long first = requester.retryDelayMillis(new HttpConfig(500, 500, 2, 250), 1);
        long third = requester.retryDelayMillis(new HttpConfig(500, 500, 2, 250), 3);
        long capped = requester.retryDelayMillis(new HttpConfig(500, 500, 2, 5000), 10);

        org.junit.jupiter.api.Assertions.assertTrue(first >= 250L && first <= 500L);
        org.junit.jupiter.api.Assertions.assertTrue(third >= 1000L && third <= 1250L);
        org.junit.jupiter.api.Assertions.assertTrue(capped <= 5000L);
    }

    private URL url(String path) throws IOException {
        return new URL("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private static class TestLogger implements HttpLogger {

        private final List<String> entries = new ArrayList<>();
        private final AtomicInteger retries = new AtomicInteger();

        @Override
        public void debug(String text) {
            entries.add(text);
        }

        @Override
        public void error(String text) {
            entries.add(text);
        }

        @Override
        public void retry(String text) {
            retries.incrementAndGet();
            entries.add(text);
        }

        private int retries() {
            return retries.get();
        }
    }
}

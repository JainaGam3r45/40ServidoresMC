package com.cadiducho.cservidoresmc.http;

import com.cadiducho.cservidoresmc.cache.Clock;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHttpRequester {

    private HttpServer server;
    private ExecutorService executor;
    private HttpRequester requester;
    private TestLogger logger;
    private ManualClock clock;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        clock = new ManualClock();
        requester = new HttpRequester(new CircuitBreaker(1, 1000L, 5000L, clock));
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

        assertTrue(first >= 250L && first <= 500L);
        assertTrue(third >= 1000L && third <= 1250L);
        assertTrue(capped <= 5000L);
    }

    @Test
    void sendsConfiguredUserAgent() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        final String[] seenAgent = new String[1];
        server.createContext("/agent", exchange -> {
            attempts.incrementAndGet();
            seenAgent[0] = exchange.getRequestHeaders().getFirst("User-Agent");
            respond(exchange, 200, "ok");
        });
        server.start();

        String agent = "40ServidoresMC/3.3.0/Bukkit-1.16.5/Java8-Test";
        String body = requester.get(url("/agent"), "test agent", new HttpConfig(500, 500, 0, 1, agent), logger);

        assertEquals("ok", body);
        assertEquals(agent, seenAgent[0]);
        assertEquals(1, attempts.get());
    }

    @Test
    void exhaustedRetriesOpenCircuitAndBlockNextCall() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/down", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 500, "down");
        });
        server.start();

        assertThrows(HttpException.class, () ->
                requester.get(url("/down"), "test down", new HttpConfig(500, 500, 1, 1), logger));
        assertEquals(2, attempts.get());
        assertTrue(requester.getCircuitBreaker().isOpen());

        assertThrows(CircuitBreaker.CircuitOpenException.class, () ->
                requester.get(url("/down"), "test blocked", new HttpConfig(500, 500, 1, 1), logger));
        assertEquals(2, attempts.get());
    }

    @Test
    void nonRetryableFailureDoesNotOpenCircuit() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/missing", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 404, "not found");
        });
        server.start();

        assertThrows(HttpException.class, () ->
                requester.get(url("/missing"), "test missing circuit", new HttpConfig(500, 500, 2, 1), logger));

        assertEquals(1, attempts.get());
        assertFalse(requester.getCircuitBreaker().isOpen());
    }

    @Test
    void successClosesPreviouslyOpenedCircuit() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/recover", exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                respond(exchange, 500, "down");
                return;
            }
            respond(exchange, 200, "up");
        });
        server.start();

        assertThrows(HttpException.class, () ->
                requester.get(url("/recover"), "test open", new HttpConfig(500, 500, 0, 1), logger));
        assertTrue(requester.getCircuitBreaker().isOpen());

        clock.advance(1000L);

        String body = requester.get(url("/recover"), "test recover", new HttpConfig(500, 500, 0, 1), logger);

        assertEquals("up", body);
        assertFalse(requester.getCircuitBreaker().isOpen());
    }

    private URL url(String path) throws IOException {
        return new URL("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static class ManualClock implements Clock {

        private long timeMillis;

        @Override
        public long currentTimeMillis() {
            return timeMillis;
        }

        private void advance(long millis) {
            timeMillis += millis;
        }
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

package com.cadiducho.cservidoresmc.http;

import com.cadiducho.cservidoresmc.cache.Clock;
import com.cadiducho.cservidoresmc.cache.SystemClock;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Soft circuit breaker: after retries are exhausted we pause new HTTP calls for a while
 * instead of hammering a failing endpoint.
 */
public class CircuitBreaker {

    private final int failureThreshold;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final Clock clock;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong nextRetryAtMs = new AtomicLong(0);

    public CircuitBreaker(int failureThreshold, long baseBackoffMs, long maxBackoffMs) {
        this(failureThreshold, baseBackoffMs, maxBackoffMs, new SystemClock());
    }

    public CircuitBreaker(int failureThreshold, long baseBackoffMs, long maxBackoffMs, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.clock = clock == null ? new SystemClock() : clock;
    }

    public static CircuitBreaker defaults() {
        return new CircuitBreaker(3, 5000L, 300000L);
    }

    public boolean canExecute() {
        long nextRetry = nextRetryAtMs.get();
        return nextRetry == 0L || clock.currentTimeMillis() >= nextRetry;
    }

    public long backoffRemainingMs() {
        long nextRetry = nextRetryAtMs.get();
        if (nextRetry == 0L) {
            return 0L;
        }
        return Math.max(0L, nextRetry - clock.currentTimeMillis());
    }

    public boolean isOpen() {
        return !canExecute();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures < failureThreshold) {
            return;
        }

        int over = failures - failureThreshold;
        long backoff = baseBackoffMs << Math.min(over, 30);
        backoff = Math.min(backoff, maxBackoffMs);
        nextRetryAtMs.set(clock.currentTimeMillis() + backoff);
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        nextRetryAtMs.set(0L);
    }

    public static class CircuitOpenException extends IOException {

        private final long retryAfterMs;

        public CircuitOpenException(long retryAfterMs) {
            super("Circuit breaker open: retries paused for " + retryAfterMs + " ms");
            this.retryAfterMs = retryAfterMs;
        }

        public long getRetryAfterMs() {
            return retryAfterMs;
        }
    }
}

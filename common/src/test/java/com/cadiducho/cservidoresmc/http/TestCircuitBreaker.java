package com.cadiducho.cservidoresmc.http;

import com.cadiducho.cservidoresmc.cache.Clock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCircuitBreaker {

    @Test
    void startsClosed() {
        CircuitBreaker breaker = CircuitBreaker.defaults();

        assertTrue(breaker.canExecute());
        assertFalse(breaker.isOpen());
        assertEquals(0, breaker.getConsecutiveFailures());
    }

    @Test
    void opensAfterThresholdFailures() {
        ManualClock clock = new ManualClock();
        CircuitBreaker breaker = new CircuitBreaker(3, 5000L, 60000L, clock);

        breaker.recordFailure();
        assertTrue(breaker.canExecute());
        breaker.recordFailure();
        assertTrue(breaker.canExecute());
        breaker.recordFailure();

        assertFalse(breaker.canExecute());
        assertTrue(breaker.isOpen());
        assertTrue(breaker.backoffRemainingMs() > 0L);
    }

    @Test
    void successResetsCounterAndCloses() {
        ManualClock clock = new ManualClock();
        CircuitBreaker breaker = new CircuitBreaker(2, 5000L, 60000L, clock);

        breaker.recordFailure();
        breaker.recordFailure();
        assertFalse(breaker.canExecute());

        breaker.recordSuccess();

        assertTrue(breaker.canExecute());
        assertEquals(0, breaker.getConsecutiveFailures());
    }

    @Test
    void backoffGrowsAndIsCapped() {
        ManualClock clock = new ManualClock();
        CircuitBreaker breaker = new CircuitBreaker(1, 1000L, 2000L, clock);

        breaker.recordFailure();
        assertEquals(1000L, breaker.backoffRemainingMs());

        clock.advance(1000L);
        assertTrue(breaker.canExecute());

        for (int i = 0; i < 10; i++) {
            breaker.recordFailure();
        }

        assertEquals(2000L, breaker.backoffRemainingMs());
    }

    @Test
    void canExecuteReturnsTrueOnceBackoffElapsed() {
        ManualClock clock = new ManualClock();
        CircuitBreaker breaker = new CircuitBreaker(1, 100L, 200L, clock);

        breaker.recordFailure();
        assertFalse(breaker.canExecute());

        clock.advance(150L);

        assertTrue(breaker.canExecute());
        assertEquals(0L, breaker.backoffRemainingMs());
    }

    @Test
    void circuitOpenExceptionCarriesRetryAfter() {
        CircuitBreaker.CircuitOpenException exception = new CircuitBreaker.CircuitOpenException(7000L);

        assertEquals(7000L, exception.getRetryAfterMs());
        assertTrue(exception.getMessage().contains("Circuit breaker open"));
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
}

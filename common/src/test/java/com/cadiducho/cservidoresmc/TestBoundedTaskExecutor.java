package com.cadiducho.cservidoresmc;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBoundedTaskExecutor {

    @Test
    void rejectsWhenQueueIsFullAndCountsRejection() throws Exception {
        PluginMetrics metrics = new PluginMetrics();
        AtomicInteger logs = new AtomicInteger();
        BoundedTaskExecutor executor = new BoundedTaskExecutor("test-http", 1, 1, metrics, message -> logs.incrementAndGet());
        CountDownLatch blocker = new CountDownLatch(1);

        executor.execute(() -> await(blocker));
        executor.execute(() -> { });

        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
        blocker.countDown();
        executor.shutdownNow();

        assertEquals(1L, metrics.getHttpRejections());
        assertEquals(1, logs.get());
    }

    @Test
    void shutdownRejectsNewTasks() {
        PluginMetrics metrics = new PluginMetrics();
        BoundedTaskExecutor executor = new BoundedTaskExecutor("test-http", 1, 1, metrics, null);

        executor.shutdownNow();

        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
        assertTrue(executor.isShutdown());
    }

    private void await(CountDownLatch blocker) {
        try {
            blocker.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

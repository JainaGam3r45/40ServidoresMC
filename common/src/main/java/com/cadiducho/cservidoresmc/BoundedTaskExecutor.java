package com.cadiducho.cservidoresmc;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BoundedTaskExecutor implements Executor {

    public static final int DEFAULT_THREADS = 4;
    public static final int DEFAULT_QUEUE_CAPACITY = 64;

    private final ThreadPoolExecutor executor;
    private final PluginMetrics metrics;
    private final CSLogger logger;
    private final AtomicLong lastRejectionLog = new AtomicLong();

    public BoundedTaskExecutor(String threadName, PluginMetrics metrics, CSLogger logger) {
        this(threadName, DEFAULT_THREADS, DEFAULT_QUEUE_CAPACITY, metrics, logger);
    }

    BoundedTaskExecutor(String threadName, int threads, int queueCapacity, PluginMetrics metrics, CSLogger logger) {
        this.metrics = metrics;
        this.logger = logger;
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new NamedThreadFactory(threadName),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public void execute(Runnable command) {
        try {
            executor.execute(command);
        } catch (RejectedExecutionException ex) {
            if (metrics != null) {
                metrics.incrementHttpRejections();
            }
            logRejection();
            throw ex;
        }
    }

    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    private void logRejection() {
        if (logger == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastRejectionLog.get();
        if (now - previous < TimeUnit.SECONDS.toMillis(30)) {
            return;
        }

        if (lastRejectionLog.compareAndSet(previous, now)) {
            logger.error("El executor HTTP está saturado; se rechazarán peticiones temporalmente.");
        }
    }

    public interface CSLogger {
        void error(String message);
    }

    private static class NamedThreadFactory implements ThreadFactory {

        private final String threadName;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedThreadFactory(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, threadName + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}

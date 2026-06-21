package com.cadiducho.cservidoresmc.scheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class CommonSchedulers {

    private static final CSScheduler DIRECT = new DirectScheduler();

    private CommonSchedulers() {
    }

    public static CSScheduler direct() {
        return DIRECT;
    }

    private static class DirectScheduler implements CSScheduler {

        private final Executor executor = Runnable::run;

        @Override
        public void runGlobal(Runnable task) {
            task.run();
        }

        @Override
        public CancellableTask runGlobalLater(Runnable task, long delay, TimeUnit unit) {
            task.run();
            return FinishedTask.INSTANCE;
        }

        @Override
        public void runPlayer(PlayerReference player, PlayerTask task) {
            throw new UnsupportedOperationException("Player-affine scheduling requires a platform scheduler.");
        }

        @Override
        public CancellableTask runPlayerLater(PlayerReference player, PlayerTask task, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("Player-affine scheduling requires a platform scheduler.");
        }

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }

        @Override
        public CancellableTask runAsyncLater(Runnable task, long delay, TimeUnit unit) {
            task.run();
            return FinishedTask.INSTANCE;
        }

        @Override
        public CancellableTask runAsyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
            task.run();
            return FinishedTask.INSTANCE;
        }

        @Override
        public Executor asyncExecutor() {
            return executor;
        }
    }

    private enum FinishedTask implements CancellableTask {
        INSTANCE;

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}

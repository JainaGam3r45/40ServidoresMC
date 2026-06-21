package com.cadiducho.cservidoresmc.scheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public interface CSScheduler {

    void runGlobal(Runnable task);

    CancellableTask runGlobalLater(Runnable task, long delay, TimeUnit unit);

    void runPlayer(PlayerReference player, PlayerTask task);

    CancellableTask runPlayerLater(PlayerReference player, PlayerTask task, long delay, TimeUnit unit);

    void runAsync(Runnable task);

    CancellableTask runAsyncLater(Runnable task, long delay, TimeUnit unit);

    CancellableTask runAsyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit);

    Executor asyncExecutor();

    default void shutdown() {
    }
}

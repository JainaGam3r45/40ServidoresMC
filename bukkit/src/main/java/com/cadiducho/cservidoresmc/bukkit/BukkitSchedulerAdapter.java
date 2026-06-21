package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.scheduler.CancellableTask;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;
import com.cadiducho.cservidoresmc.scheduler.PlayerTask;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BukkitSchedulerAdapter implements CSScheduler {

    private final BukkitPlugin plugin;
    private final Executor asyncExecutor;
    private final ScheduledExecutorService timers;
    private final AtomicBoolean shutdown = new AtomicBoolean();

    public BukkitSchedulerAdapter(BukkitPlugin plugin, Executor asyncExecutor) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.timers = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("40servidoresmc-scheduler"));
    }

    @Override
    public void runGlobal(Runnable task) {
        if (shouldSkip()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, guarded(task));
    }

    @Override
    public CancellableTask runGlobalLater(Runnable task, long delay, TimeUnit unit) {
        if (shouldSkip()) {
            return CancelledTask.INSTANCE;
        }
        BukkitTask bukkitTask = plugin.getServer().getScheduler().runTaskLater(plugin, guarded(task), toTicks(delay, unit));
        return new BukkitCancellableTask(bukkitTask);
    }

    @Override
    public void runPlayer(PlayerReference player, PlayerTask task) {
        runGlobal(() -> runResolvedPlayer(player, task));
    }

    @Override
    public CancellableTask runPlayerLater(PlayerReference player, PlayerTask task, long delay, TimeUnit unit) {
        return runGlobalLater(() -> runResolvedPlayer(player, task), delay, unit);
    }

    @Override
    public void runAsync(Runnable task) {
        if (shouldSkip()) {
            return;
        }
        timers.execute(guarded(task));
    }

    @Override
    public CancellableTask runAsyncLater(Runnable task, long delay, TimeUnit unit) {
        if (shouldSkip()) {
            return CancelledTask.INSTANCE;
        }
        ScheduledFuture<?> future = timers.schedule(guarded(task), safeDelay(delay), unit);
        return new FutureCancellableTask(future);
    }

    @Override
    public CancellableTask runAsyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (shouldSkip()) {
            return CancelledTask.INSTANCE;
        }
        Runnable guardedRepeating = nonOverlapping(guarded(task));
        ScheduledFuture<?> future = timers.scheduleAtFixedRate(guardedRepeating, safeDelay(initialDelay), Math.max(1L, period), unit);
        return new FutureCancellableTask(future);
    }

    @Override
    public Executor asyncExecutor() {
        return asyncExecutor;
    }

    @Override
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            timers.shutdownNow();
        }
    }

    private Runnable guarded(Runnable task) {
        return () -> {
            if (!shouldSkip()) {
                task.run();
            }
        };
    }

    private Runnable nonOverlapping(Runnable task) {
        AtomicBoolean running = new AtomicBoolean();
        return () -> {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                task.run();
            } finally {
                running.set(false);
            }
        };
    }

    private void runResolvedPlayer(PlayerReference reference, PlayerTask task) {
        if (reference == null || reference.isEmpty() || shouldSkip()) {
            return;
        }
        Player player = resolve(reference);
        if (player == null || !player.isOnline()) {
            task.unavailable(reference);
            return;
        }
        CSCommandSender sender = new BukkitCommandSender(player, plugin);
        task.run(sender);
    }

    private Player resolve(PlayerReference reference) {
        if (reference.hasUniqueId()) {
            try {
                Player player = plugin.getServer().getPlayer(UUID.fromString(reference.getUniqueId()));
                if (player != null) {
                    return player;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return reference.getName().isEmpty() ? null : plugin.getServer().getPlayerExact(reference.getName());
    }

    private boolean shouldSkip() {
        return shutdown.get() || !plugin.isActive();
    }

    private long toTicks(long delay, TimeUnit unit) {
        long millis = Math.max(0L, unit.toMillis(delay));
        if (millis == 0L) {
            return 0L;
        }
        return Math.max(1L, (millis + 49L) / 50L);
    }

    private long safeDelay(long delay) {
        return Math.max(0L, delay);
    }

    private enum CancelledTask implements CancellableTask {
        INSTANCE;

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    }

    private static class BukkitCancellableTask implements CancellableTask {

        private final BukkitTask task;

        private BukkitCancellableTask(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }

    private static class FutureCancellableTask implements CancellableTask {

        private final ScheduledFuture<?> future;

        private FutureCancellableTask(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public void cancel() {
            future.cancel(false);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {

        private final String name;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}

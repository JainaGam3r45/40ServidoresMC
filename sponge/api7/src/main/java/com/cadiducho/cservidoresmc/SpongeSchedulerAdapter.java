package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.scheduler.CancellableTask;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;
import com.cadiducho.cservidoresmc.scheduler.PlayerTask;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SpongeSchedulerAdapter implements CSScheduler {

    private final SpongePlugin plugin;
    private final Executor asyncExecutor;
    private final ScheduledExecutorService timers;
    private final AtomicBoolean shutdown = new AtomicBoolean();

    public SpongeSchedulerAdapter(SpongePlugin plugin, Executor asyncExecutor) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.timers = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("40servidoresmc-scheduler"));
    }

    @Override
    public void runGlobal(Runnable task) {
        if (shouldSkip()) {
            return;
        }
        Sponge.getScheduler().createTaskBuilder().execute(guarded(task)).submit(plugin);
    }

    @Override
    public CancellableTask runGlobalLater(Runnable task, long delay, TimeUnit unit) {
        if (shouldSkip()) {
            return CancelledTask.INSTANCE;
        }
        Task spongeTask = Sponge.getScheduler().createTaskBuilder()
                .delay(safeDelay(delay), unit)
                .execute(guarded(task))
                .submit(plugin);
        return new SpongeCancellableTask(spongeTask);
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
        ScheduledFuture<?> future = timers.scheduleAtFixedRate(
                nonOverlapping(guarded(task)),
                safeDelay(initialDelay),
                Math.max(1L, period),
                unit);
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
        CSCommandSender sender = new SpongeCommandSender(player, plugin);
        task.run(sender);
    }

    private Player resolve(PlayerReference reference) {
        if (reference.hasUniqueId()) {
            try {
                Player player = Sponge.getServer().getPlayer(UUID.fromString(reference.getUniqueId())).orElse(null);
                if (player != null) {
                    return player;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return reference.getName().isEmpty() ? null : Sponge.getServer().getPlayer(reference.getName()).orElse(null);
    }

    private boolean shouldSkip() {
        return shutdown.get() || !plugin.isActive();
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

    private static class SpongeCancellableTask implements CancellableTask {

        private final Task task;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private SpongeCancellableTask(Task task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
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

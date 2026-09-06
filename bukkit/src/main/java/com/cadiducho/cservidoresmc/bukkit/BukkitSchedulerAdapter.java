package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.scheduler.CancellableTask;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;
import com.cadiducho.cservidoresmc.scheduler.PlayerTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class BukkitSchedulerAdapter implements CSScheduler {

    private final BukkitPlugin plugin;
    private final Executor asyncExecutor;
    private final ScheduledExecutorService timers;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final boolean folia;

    private Object globalRegionScheduler;
    private Method globalRun;
    private Method globalRunDelayed;
    private Method entityGetScheduler;
    private Method entityRun;
    private Method entityRunDelayed;
    private Method scheduledTaskCancel;
    private Method scheduledTaskIsCancelled;

    public BukkitSchedulerAdapter(BukkitPlugin plugin, Executor asyncExecutor) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.timers = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("40servidoresmc-scheduler"));
        this.folia = FoliaDetector.isFoliaServer() && initFoliaReflection();
    }

    boolean isUsingFoliaSchedulers() {
        return folia;
    }

    @Override
    public void runGlobal(Runnable task) {
        if (shouldSkip()) {
            return;
        }
        if (folia) {
            invokeGlobal(guarded(task), 0L);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, guarded(task));
    }

    @Override
    public CancellableTask runGlobalLater(Runnable task, long delay, TimeUnit unit) {
        if (shouldSkip()) {
            return CancelledTask.INSTANCE;
        }
        if (folia) {
            return invokeGlobal(guarded(task), toTicks(delay, unit));
        }
        BukkitTask bukkitTask = plugin.getServer().getScheduler().runTaskLater(plugin, guarded(task), toTicks(delay, unit));
        return new BukkitCancellableTask(bukkitTask);
    }

    @Override
    public void runPlayer(PlayerReference player, PlayerTask task) {
        if (folia) {
            schedulePlayer(player, task, 0L);
            return;
        }
        runGlobal(() -> runResolvedPlayer(player, task));
    }

    @Override
    public CancellableTask runPlayerLater(PlayerReference player, PlayerTask task, long delay, TimeUnit unit) {
        if (folia) {
            return schedulePlayer(player, task, toTicks(delay, unit));
        }
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

    private boolean initFoliaReflection() {
        try {
            // GlobalRegionScheduler: server-wide work
            globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalRun = globalRegionScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            globalRunDelayed = globalRegionScheduler.getClass().getMethod(
                    "runDelayed", Plugin.class, Consumer.class, long.class);

            // EntityScheduler on Player: player-affine work
            entityGetScheduler = Player.class.getMethod("getScheduler");
            Class<?> entitySchedulerType = entityGetScheduler.getReturnType();
            entityRun = entitySchedulerType.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = entitySchedulerType.getMethod(
                    "runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);

            // ScheduledTask cancel / isCancelled
            Class<?> scheduledTaskType = globalRun.getReturnType();
            scheduledTaskCancel = scheduledTaskType.getMethod("cancel");
            scheduledTaskIsCancelled = scheduledTaskType.getMethod("isCancelled");
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.logError("Folia detectado pero el scheduler por reflection no pudo inicializarse: " + ex.getMessage());
            return false;
        }
    }

    private CancellableTask invokeGlobal(Runnable task, long delayTicks) {
        try {
            Consumer<Object> consumer = ignored -> task.run();
            Object scheduled = delayTicks <= 0L
                    ? globalRun.invoke(globalRegionScheduler, plugin, consumer)
                    : globalRunDelayed.invoke(globalRegionScheduler, plugin, consumer, delayTicks);
            return new FoliaCancellableTask(scheduled, scheduledTaskCancel, scheduledTaskIsCancelled);
        } catch (ReflectiveOperationException ex) {
            plugin.logError("Error al agendar tarea global Folia: " + ex.getMessage());
            return CancelledTask.INSTANCE;
        }
    }

    private CancellableTask schedulePlayer(PlayerReference reference, PlayerTask task, long delayTicks) {
        if (reference == null || reference.isEmpty() || shouldSkip()) {
            if (task != null && reference != null && !reference.isEmpty()) {
                task.unavailable(reference);
            }
            return CancelledTask.INSTANCE;
        }
        Player player = resolve(reference);
        if (player == null || !player.isOnline()) {
            task.unavailable(reference);
            return CancelledTask.INSTANCE;
        }

        Runnable run = guarded(() -> {
            if (!player.isOnline()) {
                task.unavailable(reference);
                return;
            }
            CSCommandSender sender = new BukkitCommandSender(player, plugin);
            task.run(sender);
        });
        Runnable retired = () -> {
            if (!shouldSkip()) {
                task.unavailable(reference);
            }
        };

        try {
            Object entityScheduler = entityGetScheduler.invoke(player);
            Consumer<Object> consumer = ignored -> run.run();
            Object scheduled = delayTicks <= 0L
                    ? entityRun.invoke(entityScheduler, plugin, consumer, retired)
                    : entityRunDelayed.invoke(entityScheduler, plugin, consumer, retired, delayTicks);
            if (scheduled == null) {
                task.unavailable(reference);
                return CancelledTask.INSTANCE;
            }
            return new FoliaCancellableTask(scheduled, scheduledTaskCancel, scheduledTaskIsCancelled);
        } catch (ReflectiveOperationException ex) {
            plugin.logError("Error al agendar tarea de jugador Folia: " + ex.getMessage());
            task.unavailable(reference);
            return CancelledTask.INSTANCE;
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

    private static class FoliaCancellableTask implements CancellableTask {

        private final Object scheduledTask;
        private final Method cancelMethod;
        private final Method isCancelledMethod;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private FoliaCancellableTask(Object scheduledTask, Method cancelMethod, Method isCancelledMethod) {
            this.scheduledTask = scheduledTask;
            this.cancelMethod = cancelMethod;
            this.isCancelledMethod = isCancelledMethod;
        }

        @Override
        public void cancel() {
            if (scheduledTask == null || !cancelled.compareAndSet(false, true)) {
                return;
            }
            try {
                cancelMethod.invoke(scheduledTask);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public boolean isCancelled() {
            if (cancelled.get()) {
                return true;
            }
            if (scheduledTask == null) {
                return true;
            }
            try {
                Object value = isCancelledMethod.invoke(scheduledTask);
                return value instanceof Boolean && (Boolean) value;
            } catch (ReflectiveOperationException ignored) {
                return cancelled.get();
            }
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

package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.scheduler.CancellableTask;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;
import com.cadiducho.cservidoresmc.scheduler.PlayerTask;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class TestScheduler implements CSScheduler {

    private final Map<String, CSCommandSender> playersByUuid = new HashMap<>();
    private final Map<String, CSCommandSender> playersByName = new HashMap<>();
    private final Queue<ManualTask> delayedTasks = new ArrayDeque<>();
    private final Queue<RepeatingTask> repeatingTasks = new ArrayDeque<>();
    private boolean shutdown;
    private long lastRepeatingPeriod;

    void connect(CSCommandSender sender) {
        if (sender.getUniqueId() != null && !sender.getUniqueId().trim().isEmpty()) {
            playersByUuid.put(sender.getUniqueId().toLowerCase(Locale.ROOT), sender);
        }
        playersByName.put(sender.getName().toLowerCase(Locale.ROOT), sender);
    }

    void disconnect(CSCommandSender sender) {
        playersByUuid.remove(sender.getUniqueId().toLowerCase(Locale.ROOT));
        playersByName.remove(sender.getName().toLowerCase(Locale.ROOT));
    }

    void runNextDelayed() {
        delayedTasks.remove().run();
    }

    void runRepeating() {
        repeatingTasks.peek().run();
    }

    long lastRepeatingPeriod() {
        return lastRepeatingPeriod;
    }

    boolean hasDelayedTasks() {
        return !delayedTasks.isEmpty();
    }

    @Override
    public void runGlobal(Runnable task) {
        if (!shutdown) {
            task.run();
        }
    }

    @Override
    public CancellableTask runGlobalLater(Runnable task, long delay, TimeUnit unit) {
        ManualTask manualTask = new ManualTask(task);
        delayedTasks.add(manualTask);
        return manualTask;
    }

    @Override
    public void runPlayer(PlayerReference player, PlayerTask task) {
        if (shutdown) {
            return;
        }
        CSCommandSender sender = resolve(player);
        if (sender != null) {
            task.run(sender);
            return;
        }
        task.unavailable(player);
    }

    @Override
    public CancellableTask runPlayerLater(PlayerReference player, PlayerTask task, long delay, TimeUnit unit) {
        ManualTask manualTask = new ManualTask(() -> runPlayer(player, task));
        delayedTasks.add(manualTask);
        return manualTask;
    }

    @Override
    public void runAsync(Runnable task) {
        if (!shutdown) {
            task.run();
        }
    }

    @Override
    public CancellableTask runAsyncLater(Runnable task, long delay, TimeUnit unit) {
        ManualTask manualTask = new ManualTask(task);
        delayedTasks.add(manualTask);
        return manualTask;
    }

    @Override
    public CancellableTask runAsyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        lastRepeatingPeriod = period;
        RepeatingTask repeatingTask = new RepeatingTask(task);
        repeatingTasks.add(repeatingTask);
        return repeatingTask;
    }

    @Override
    public Executor asyncExecutor() {
        return Runnable::run;
    }

    @Override
    public void shutdown() {
        shutdown = true;
        delayedTasks.clear();
        repeatingTasks.clear();
    }

    private CSCommandSender resolve(PlayerReference reference) {
        if (reference.hasUniqueId()) {
            CSCommandSender sender = playersByUuid.get(reference.getUniqueId().toLowerCase(Locale.ROOT));
            if (sender != null) {
                return sender;
            }
        }
        return playersByName.get(reference.getName().toLowerCase(Locale.ROOT));
    }

    private class ManualTask implements CancellableTask {

        private final Runnable task;
        private boolean cancelled;

        private ManualTask(Runnable task) {
            this.task = task;
        }

        protected void run() {
            if (!cancelled && !shutdown) {
                task.run();
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private class RepeatingTask extends ManualTask {

        private final AtomicBoolean running = new AtomicBoolean();

        private RepeatingTask(Runnable task) {
            super(task);
        }

        @Override
        protected void run() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                super.run();
            } finally {
                running.set(false);
            }
        }
    }
}

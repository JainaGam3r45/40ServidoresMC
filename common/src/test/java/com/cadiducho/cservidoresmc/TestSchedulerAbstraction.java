package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.scheduler.CancellableTask;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSchedulerAbstraction {

    @Test
    void disconnectedPlayerTaskIsSkipped() {
        TestScheduler scheduler = new TestScheduler();
        TestSender sender = new TestSender("Cadiducho");
        scheduler.connect(sender);
        scheduler.disconnect(sender);

        scheduler.runPlayer(PlayerReference.from(sender), resolved -> resolved.sendMessage("message"));

        assertEquals(0, sender.messages);
    }

    @Test
    void delayedTaskCanBeCancelled() {
        TestScheduler scheduler = new TestScheduler();
        AtomicInteger runs = new AtomicInteger();

        CancellableTask task = scheduler.runAsyncLater(runs::incrementAndGet, 10, TimeUnit.SECONDS);
        task.cancel();
        scheduler.runNextDelayed();

        assertTrue(task.isCancelled());
        assertEquals(0, runs.get());
    }

    @Test
    void repeatingTaskCanBeCancelled() {
        TestScheduler scheduler = new TestScheduler();
        AtomicInteger runs = new AtomicInteger();

        CancellableTask task = scheduler.runAsyncRepeating(runs::incrementAndGet, 1, 1, TimeUnit.SECONDS);
        task.cancel();
        scheduler.runRepeating();

        assertTrue(task.isCancelled());
        assertEquals(0, runs.get());
    }

    @Test
    void shutdownIsIdempotentAndStopsPendingTasks() {
        TestScheduler scheduler = new TestScheduler();
        AtomicInteger runs = new AtomicInteger();
        scheduler.runAsyncLater(runs::incrementAndGet, 1, TimeUnit.SECONDS);

        scheduler.shutdown();
        scheduler.shutdown();

        assertFalse(scheduler.hasDelayedTasks());
        assertEquals(0, runs.get());
    }

    @Test
    void repeatingTaskDoesNotOverlap() {
        TestScheduler scheduler = new TestScheduler();
        AtomicInteger runs = new AtomicInteger();

        scheduler.runAsyncRepeating(() -> {
            runs.incrementAndGet();
            scheduler.runRepeating();
        }, 1, 1, TimeUnit.SECONDS);
        scheduler.runRepeating();

        assertEquals(1, runs.get());
    }

    @Test
    void runSyncDelegatesToScheduler() {
        TestPlugin plugin = new TestPlugin();

        plugin.runSync(() -> plugin.runs.incrementAndGet());

        assertEquals(1, plugin.runs.get());
    }

    private static class TestPlugin implements CSPlugin {

        private final TestScheduler scheduler = new TestScheduler();
        private final AtomicInteger runs = new AtomicInteger();

        @Override
        public CSScheduler getScheduler() {
            return scheduler;
        }

        @Override
        public void log(String text) {
        }

        @Override
        public void logError(String text) {
        }

        @Override
        public void registerCommands() {
        }

        @Override
        public CSConfiguration getCSConfiguration() {
            return null;
        }

        @Override
        public ApiClient getApiClient() {
            return null;
        }

        @Override
        public RewardService getRewardService() {
            return null;
        }

        @Override
        public File getPluginDataFolder() {
            return new File(".");
        }

        @Override
        public Updater getUpdater() {
            return null;
        }

        @Override
        public PluginMetrics getPluginMetrics() {
            return null;
        }

        @Override
        public String getPluginVersion() {
            return "test";
        }

        @Override
        public void dispatchCommand(String command) {
        }

        @Override
        public void broadcastMessage(String message) {
        }
    }

    private static class TestSender implements CSCommandSender {

        private final String name;
        private int messages;

        private TestSender(String name) {
            this.name = name;
        }

        @Override
        public String TAG() {
            return "";
        }

        @Override
        public void sendMessage(String message) {
            messages++;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getUniqueId() {
            return "0f50d3c1-2d53-47d8-9f5a-10153b5f9770";
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }
    }
}

package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestVoteReminderService {

    @TempDir
    File tempDir;

    @Test
    void remindsAfterTwentyFourHours() {
        AtomicLong clock = new AtomicLong(1_000L);
        TestPlugin plugin = new TestPlugin(tempDir);
        TestScheduler scheduler = plugin.scheduler;
        VoteReminderService service = reminderService(plugin, scheduler, clock);
        TestSender sender = new TestSender("Cadiducho");
        scheduler.connect(sender);
        plugin.onlinePlayers.add(sender);

        service.recordVote(sender.getName(), clock.get());
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS);
        service.checkReminders();

        assertEquals(Collections.singletonList("&8[&b40ServidoresMC&8] &aYa puedes volver a votar."), sender.messages);
    }

    @Test
    void doesNotRemindBeforeTwentyFourHours() {
        AtomicLong clock = new AtomicLong(1_000L);
        TestPlugin plugin = new TestPlugin(tempDir);
        VoteReminderService service = reminderService(plugin, plugin.scheduler, clock);
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);
        plugin.onlinePlayers.add(sender);

        service.recordVote(sender.getName(), clock.get());
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS - 1L);
        service.checkReminders();

        assertEquals(1L, service.nextVoteInMillis(sender.getName()));
        assertFalse(service.canVote(sender.getName()));
        assertEquals(Collections.emptyList(), sender.messages);
    }

    @Test
    void exposesLocalVoteStatus() {
        AtomicLong clock = new AtomicLong(1_000L);
        TestPlugin plugin = new TestPlugin(tempDir);
        VoteReminderService service = reminderService(plugin, plugin.scheduler, clock);

        service.recordVote("Cadiducho", clock.get());
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS);

        assertEquals(1_000L, service.lastVoteAt("cadiducho"));
        assertEquals(0L, service.nextVoteInMillis("Cadiducho"));
        assertEquals(-1L, service.nextVoteInMillis("SinDatos"));
        assertEquals(true, service.canVote("Cadiducho"));
    }

    @Test
    void doesNotRepeatReminderForSameVoteCycle() {
        AtomicLong clock = new AtomicLong(1_000L);
        TestPlugin plugin = new TestPlugin(tempDir);
        VoteReminderService service = reminderService(plugin, plugin.scheduler, clock);
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);
        plugin.onlinePlayers.add(sender);

        service.recordVote(sender.getName(), clock.get());
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS);
        service.checkReminders();
        service.checkReminders();

        assertEquals(1, sender.messages.size());
    }

    @Test
    void remindsAgainAfterNewVote() {
        AtomicLong clock = new AtomicLong(1_000L);
        TestPlugin plugin = new TestPlugin(tempDir);
        VoteReminderService service = reminderService(plugin, plugin.scheduler, clock);
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);
        plugin.onlinePlayers.add(sender);

        service.recordVote(sender.getName(), clock.get());
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS);
        service.checkReminders();
        clock.addAndGet(10_000L);
        service.recordVote(sender.getName(), clock.get());
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS);
        service.checkReminders();

        assertEquals(2, sender.messages.size());
    }

    @Test
    void disabledReminderDoesNotStartOrSend() {
        AtomicLong clock = new AtomicLong(1_000L);
        TestPlugin plugin = new TestPlugin(tempDir);
        plugin.configuration.booleans.put("voteReminder.enabled", false);
        TestScheduler scheduler = plugin.scheduler;
        VoteReminderService service = reminderService(plugin, scheduler, clock);
        TestSender sender = new TestSender("Cadiducho");
        scheduler.connect(sender);
        plugin.onlinePlayers.add(sender);

        service.start();
        service.recordVote(sender.getName(), clock.get());
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS);
        service.checkReminders();

        assertFalse(scheduler.hasDelayedTasks());
        assertEquals(Collections.emptyList(), sender.messages);
    }

    @Test
    void startUsesMinimumCheckInterval() {
        AtomicLong clock = new AtomicLong(1_000L);
        TestPlugin plugin = new TestPlugin(tempDir);
        plugin.configuration.ints.put("voteReminder.checkIntervalSeconds", 1);
        TestScheduler scheduler = plugin.scheduler;
        VoteReminderService service = reminderService(plugin, scheduler, clock);

        service.start();

        assertEquals(60L, scheduler.lastRepeatingPeriod());
    }

    @Test
    void reminderFlowUsesKnownUuidWithoutResolvingByName() {
        AtomicLong clock = new AtomicLong(1_000L);
        TestPlugin plugin = new TestPlugin(tempDir);
        VoteReminderService service = reminderService(plugin, plugin.scheduler, clock);
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);
        plugin.onlinePlayers.add(sender);

        service.recordVote(sender.getName(), sender.getUniqueId(), clock.get());
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS);
        service.checkReminders();

        assertEquals(0, plugin.resolveCalls);
        assertEquals(Collections.singletonList("&8[&b40ServidoresMC&8] &aYa puedes volver a votar."), sender.messages);
    }

    private VoteReminderService reminderService(TestPlugin plugin, TestScheduler scheduler, AtomicLong clock) {
        return new VoteReminderService(plugin, new File(tempDir, "vote-reminders.properties"), scheduler, clock::get);
    }

    private static class TestPlugin implements CSPlugin {

        private final File dataFolder;
        private final TestConfiguration configuration = new TestConfiguration(this);
        private final TestScheduler scheduler = new TestScheduler();
        private final List<CSCommandSender> onlinePlayers = new ArrayList<>();
        private int resolveCalls;

        private TestPlugin(File dataFolder) {
            this.dataFolder = dataFolder;
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
            return configuration;
        }

        @Override
        public com.cadiducho.cservidoresmc.scheduler.CSScheduler getScheduler() {
            return scheduler;
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
            return dataFolder;
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
        public List<CSCommandSender> getOnlinePlayers() {
            return onlinePlayers;
        }

        @Override
        public String resolvePlayerUniqueId(String player) {
            resolveCalls++;
            return "cadiducho".equalsIgnoreCase(player) ? "0f50d3c1-2d53-47d8-9f5a-10153b5f9770" : "";
        }

        @Override
        public void runSync(Runnable task) {
            task.run();
        }

        @Override
        public void broadcastMessage(String message) {
        }
    }

    private static class TestConfiguration implements CSConfiguration {

        private final CSPlugin plugin;
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Integer> ints = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();

        private TestConfiguration(CSPlugin plugin) {
            this.plugin = plugin;
            strings.put("voteReminder.message", "&aYa puedes volver a votar.");
            booleans.put("voteReminder.enabled", true);
            ints.put("voteReminder.checkIntervalSeconds", 60);
        }

        @Override
        public void reload() {
        }

        @Override
        public String getString(String key, String defValue) {
            return strings.containsKey(key) ? strings.get(key) : defValue;
        }

        @Override
        public int getInt(String key, int defValue) {
            return ints.containsKey(key) ? ints.get(key) : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return booleans.containsKey(key) ? booleans.get(key) : defValue;
        }

        @Override
        public List<String> getStringList(String path, List<String> def) {
            return def;
        }

        @Override
        public Map<String, String> getStringMap(String path, Map<String, String> def) {
            return def;
        }

        @Override
        public CSPlugin getPlugin() {
            return plugin;
        }
    }

    private static class TestSender implements CSCommandSender {

        private final String name;
        private final String uuid;
        private final List<String> messages = new ArrayList<>();

        private TestSender(String name) {
            this.name = name;
            this.uuid = "0f50d3c1-2d53-47d8-9f5a-10153b5f9770";
        }

        @Override
        public String TAG() {
            return "&8[&b40ServidoresMC&8]";
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getUniqueId() {
            return uuid;
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }
    }
}

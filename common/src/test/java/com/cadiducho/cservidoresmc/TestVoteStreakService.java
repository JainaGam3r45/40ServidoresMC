package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestVoteStreakService {

    @TempDir
    File tempDir;

    @Test
    void consecutiveDaysIncreaseStreak() {
        AtomicReference<LocalDate> day = new AtomicReference<>(LocalDate.parse("2026-06-17"));
        TestPlugin plugin = new TestPlugin(tempDir);
        VoteStreakService service = service(plugin, day);
        TestSender sender = new TestSender("Cadiducho", "0f50d3c1-2d53-47d8-9f5a-10153b5f9770");

        service.recordVote(sender);
        day.set(LocalDate.parse("2026-06-18"));
        VoteStreakStore.Snapshot snapshot = service.recordVote(sender);

        assertEquals(2, snapshot.getStreak());
        assertEquals("2026-06-18", snapshot.getLastDay());
    }

    @Test
    void skippedDayResetsStreak() {
        AtomicReference<LocalDate> day = new AtomicReference<>(LocalDate.parse("2026-06-15"));
        TestPlugin plugin = new TestPlugin(tempDir);
        VoteStreakService service = service(plugin, day);
        TestSender sender = new TestSender("Cadiducho", "0f50d3c1-2d53-47d8-9f5a-10153b5f9770");

        service.recordVote(sender);
        day.set(LocalDate.parse("2026-06-17"));
        VoteStreakStore.Snapshot snapshot = service.recordVote(sender);

        assertEquals(1, snapshot.getStreak());
        assertEquals(1, snapshot.getBestStreak());
        assertEquals("2026-06-17", snapshot.getLastDay());
        assertTrue(snapshot.getRewardedMilestones().isEmpty());
    }

    @Test
    void tracksBestStreakAndNextReward() {
        AtomicReference<LocalDate> day = new AtomicReference<>(LocalDate.parse("2026-06-17"));
        TestPlugin plugin = new TestPlugin(tempDir);
        plugin.configuration.streakRewards.put("3", Collections.singletonList("give %player% diamond 1"));
        VoteStreakService service = service(plugin, day);
        TestSender sender = new TestSender("Cadiducho", "0f50d3c1-2d53-47d8-9f5a-10153b5f9770");

        service.recordVote(sender);
        day.set(LocalDate.parse("2026-06-18"));
        VoteStreakStore.Snapshot snapshot = service.recordVote(sender);

        assertEquals(2, snapshot.getBestStreak());
        assertEquals(3, service.nextRewardMilestone(snapshot.getStreak()));
        assertEquals(0, service.nextRewardMilestone(3));
    }

    @Test
    void milestoneRewardUsesPlaceholders() {
        AtomicReference<LocalDate> day = new AtomicReference<>(LocalDate.parse("2026-06-17"));
        TestPlugin plugin = new TestPlugin(tempDir);
        plugin.configuration.streakRewards.put("2", Collections.singletonList("give %player% %uuid% %streak%"));
        VoteStreakService service = service(plugin, day);
        TestSender sender = new TestSender("Cadiducho", "0f50d3c1-2d53-47d8-9f5a-10153b5f9770");

        service.recordVote(sender);
        day.set(LocalDate.parse("2026-06-18"));
        service.recordVote(sender);

        assertEquals(Collections.singletonList("give Cadiducho 0f50d3c1-2d53-47d8-9f5a-10153b5f9770 2"), plugin.commands);
    }

    @Test
    void milestoneRewardIsNotDuplicated() {
        AtomicReference<LocalDate> day = new AtomicReference<>(LocalDate.parse("2026-06-18"));
        TestPlugin plugin = new TestPlugin(tempDir);
        plugin.configuration.streakRewards.put("1", Collections.singletonList("give %player% diamond 1"));
        VoteStreakService service = service(plugin, day);
        TestSender sender = new TestSender("Cadiducho", "0f50d3c1-2d53-47d8-9f5a-10153b5f9770");

        service.recordVote(sender);
        service.recordVote(sender);

        assertEquals(Collections.singletonList("give Cadiducho diamond 1"), plugin.commands);
    }

    @Test
    void storePersistsAndResetsStreaks() {
        File streakFile = new File(tempDir, "vote-streaks.properties");
        TestPlugin plugin = new TestPlugin(tempDir);
        VoteStreakStore store = new VoteStreakStore(streakFile, plugin);

        VoteStreakStore.Snapshot snapshot = store.recordVote("Cadiducho", "0f50d3c1-2d53-47d8-9f5a-10153b5f9770", LocalDate.parse("2026-06-18"));
        store.markMilestoneRewarded(snapshot.getKey(), 1);

        VoteStreakStore reloadedStore = new VoteStreakStore(streakFile, plugin);
        VoteStreakStore.Snapshot reloaded = reloadedStore.find("Cadiducho");

        assertEquals(1, reloaded.getStreak());
        assertEquals(1, reloaded.getBestStreak());
        assertEquals("2026-06-18", reloaded.getLastDay());
        assertEquals(Collections.singleton(1), reloaded.getRewardedMilestones());

        assertTrue(reloadedStore.reset("Cadiducho"));
        assertEquals(0, reloadedStore.find("Cadiducho").getStreak());
    }

    private VoteStreakService service(TestPlugin plugin, AtomicReference<LocalDate> day) {
        return new VoteStreakService(plugin, new File(tempDir, "vote-streaks.properties"), day::get);
    }

    private static class TestPlugin implements CSPlugin {

        private final File dataFolder;
        private final TestConfiguration configuration = new TestConfiguration(this);
        private final List<String> commands = new ArrayList<>();

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
            commands.add(command);
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
        private final Map<String, List<String>> streakRewards = new HashMap<>();

        private TestConfiguration(CSPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void reload() {
        }

        @Override
        public String getString(String key, String defValue) {
            return defValue;
        }

        @Override
        public int getInt(String key, int defValue) {
            return defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return defValue;
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
        public Map<String, List<String>> getStringListMap(String path, Map<String, List<String>> def) {
            if ("streakRewards".equals(path)) {
                return streakRewards;
            }
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

        private TestSender(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        @Override
        public String TAG() {
            return "";
        }

        @Override
        public void sendMessage(String message) {
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

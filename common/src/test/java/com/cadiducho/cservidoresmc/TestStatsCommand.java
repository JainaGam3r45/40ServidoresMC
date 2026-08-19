package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommand;
import com.cadiducho.cservidoresmc.cmd.StatsCMD;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestStatsCommand {

    @Test
    void emptyRecentVotesDoesNotThrow() {
        TestPlugin plugin = new TestPlugin();
        TestSender sender = new TestSender();
        ServerStats stats = new ServerStats();
        stats.setServerName("Servidor");
        stats.setLastVotes(Collections.emptyList());
        plugin.apiClient = new TestApiClient(plugin, stats);

        CSCommand.CommandResult result = new StatsCMD().execute(plugin, sender, "stats40", Collections.emptyList());

        assertEquals(CSCommand.CommandResult.SUCCESS, result);
        assertEquals(5, sender.messages.size());
    }

    @Test
    void completedStatsAreIgnoredAfterShutdown() {
        TestPlugin plugin = new TestPlugin();
        plugin.active = false;
        TestSender sender = new TestSender();
        ServerStats stats = new ServerStats();
        stats.setServerName("Servidor");
        stats.setLastVotes(Collections.emptyList());
        plugin.apiClient = new TestApiClient(plugin, stats);

        new StatsCMD().execute(plugin, sender, "stats40", Collections.emptyList());

        assertEquals(0, sender.messages.size());
    }

    @Test
    void completedStatsFromCacheUsePlayerScheduler() {
        TestPlugin plugin = new TestPlugin();
        TestSender sender = new TestSender("0f50d3c1-2d53-47d8-9f5a-10153b5f9770");
        plugin.scheduler.connect(sender);
        ServerStats stats = new ServerStats();
        stats.setServerName("Servidor");
        stats.setLastVotes(Collections.emptyList());
        plugin.apiClient = new TestApiClient(plugin, stats);

        new StatsCMD().execute(plugin, sender, "stats40", Collections.emptyList());

        assertEquals(5, sender.messages.size());
    }

    @Test
    void completedStatsDoNotRunPlayerTaskAfterShutdown() {
        TestPlugin plugin = new TestPlugin();
        TestSender sender = new TestSender("0f50d3c1-2d53-47d8-9f5a-10153b5f9770");
        plugin.scheduler.connect(sender);
        plugin.active = false;
        ServerStats stats = new ServerStats();
        stats.setServerName("Servidor");
        stats.setLastVotes(Collections.emptyList());
        plugin.apiClient = new TestApiClient(plugin, stats);

        new StatsCMD().execute(plugin, sender, "stats40", Collections.emptyList());

        assertEquals(0, sender.messages.size());
    }

    private static class TestApiClient extends ApiClient {

        private final ServerStats stats;

        private TestApiClient(CSPlugin plugin, ServerStats stats) {
            super(plugin, new Gson());
            this.stats = stats;
        }

        @Override
        public CompletableFuture<ServerStats> fetchServerStats() {
            return CompletableFuture.completedFuture(stats);
        }
    }

    private static class TestSender implements CSCommandSender {

        private final List<String> messages = new ArrayList<>();
        private final String uuid;

        private TestSender() {
            this("");
        }

        private TestSender(String uuid) {
            this.uuid = uuid;
        }

        @Override
        public String TAG() {
            return "";
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public String getName() {
            return "Cadiducho";
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

    private static class TestPlugin implements CSPlugin {

        private final TestConfiguration configuration = new TestConfiguration(this);
        private ApiClient apiClient;
        private boolean active = true;
        private final PluginMetrics metrics = new PluginMetrics();
        private final TestScheduler scheduler = new TestScheduler();

        @Override
        public com.cadiducho.cservidoresmc.scheduler.CSScheduler getScheduler() {
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
            return configuration;
        }

        @Override
        public ApiClient getApiClient() {
            return apiClient;
        }

        @Override
        public boolean isActive() {
            return active;
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
            return metrics;
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

    private static class TestConfiguration implements CSConfiguration {

        private final CSPlugin plugin;
        private final Map<String, String> strings = new HashMap<>();

        private TestConfiguration(CSPlugin plugin) {
            this.plugin = plugin;
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
        public CSPlugin getPlugin() {
            return plugin;
        }
    }
}

package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRewardService {

    @TempDir
    File tempDir;

    @Test
    void voteDetectedAfterSeveralAttempts() {
        TestPlugin plugin = new TestPlugin(tempDir);
        ManualScheduler scheduler = new ManualScheduler();
        RewardService rewardService = rewardService(plugin, scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin, vote("0"), vote("0"), vote("1")));
        TestSender sender = new TestSender("Cadiducho");

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));
        scheduler.runNext();
        scheduler.runNext();
        scheduler.runNext();

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void rewardIsDeliveredOnce() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, new ManualScheduler());
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");

        rewardService.deliverReward(sender.getName(), sender, true);
        rewardService.deliverReward(sender.getName(), sender, true);

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void apiErrorDoesNotDuplicateReward() {
        TestPlugin plugin = new TestPlugin(tempDir);
        ManualScheduler scheduler = new ManualScheduler();
        RewardService rewardService = rewardService(plugin, scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin, new IOException("API down"), vote("1"), vote("1")));
        TestSender sender = new TestSender("Cadiducho");

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));
        scheduler.runNext();
        scheduler.runNext();
        rewardService.handleVoteResponse(sender.getName(), sender, vote("1"));

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void manualSuccessUsesRewardService() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, new ManualScheduler());
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");

        rewardService.handleVoteResponse(sender.getName(), sender, vote("1"));

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(Collections.singletonList("&aGracias a Cadiducho por votarnos!"), plugin.broadcasts);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    private RewardService rewardService(TestPlugin plugin, ManualScheduler scheduler) {
        return new RewardService(plugin, new File(tempDir, "rewarded-votes.properties"), scheduler, () -> "2026-06-18");
    }

    private VoteResponse vote(String status) {
        return new Gson().fromJson("{\"web\":\"https://40servidoresmc.es\",\"status\":\"" + status + "\"}", VoteResponse.class);
    }

    private static class ManualScheduler implements RewardService.RewardScheduler {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void schedule(Runnable task, long delaySeconds) {
            tasks.add(task);
        }

        @Override
        public void shutdown() {
            tasks.clear();
        }

        private void runNext() {
            tasks.remove().run();
        }
    }

    private static class FakeApiClient extends ApiClient {

        private final Queue<Object> responses = new ArrayDeque<>();

        private FakeApiClient(CSPlugin plugin, Object... responses) {
            super(plugin, new Gson(), null, "http://localhost/api?clave=");
            this.responses.addAll(Arrays.asList(responses));
        }

        @Override
        public CompletableFuture<VoteResponse> validateVote(String player) {
            Object next = responses.remove();
            if (next instanceof Throwable) {
                CompletableFuture<VoteResponse> future = new CompletableFuture<>();
                future.completeExceptionally((Throwable) next);
                return future;
            }
            return CompletableFuture.completedFuture((VoteResponse) next);
        }
    }

    private static class TestPlugin implements CSPlugin {

        private final File dataFolder;
        private final PluginMetrics pluginMetrics = new PluginMetrics();
        private final TestConfiguration configuration = new TestConfiguration(this);
        private final List<String> commands = new ArrayList<>();
        private final List<String> broadcasts = new ArrayList<>();
        private ApiClient apiClient;
        private RewardService rewardService;

        private TestPlugin(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        private void setApiClient(ApiClient apiClient) {
            this.apiClient = apiClient;
        }

        private void setRewardService(RewardService rewardService) {
            this.rewardService = rewardService;
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
        public RewardService getRewardService() {
            return rewardService;
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
            return pluginMetrics;
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
        public void broadcastMessage(String message) {
            broadcasts.add(message);
        }
    }

    private static class TestConfiguration implements CSConfiguration {

        private final CSPlugin plugin;
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Integer> ints = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();

        private TestConfiguration(CSPlugin plugin) {
            this.plugin = plugin;
            strings.put("mensaje", "&6Gracias por votarnos! Aqui tienes tu premio: ");
            strings.put("broadcast.mensajeBroadcast", "&aGracias a {0} por votarnos!");
            booleans.put("broadcast.activado", true);
            booleans.put("autoReward.enabled", true);
            booleans.put("autoReward.debug", false);
            ints.put("autoReward.maxAttempts", 3);
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
            if ("comandosCustom".equals(path)) {
                return Collections.singletonList("money add {0} 10");
            }
            if ("autoReward.recheckDelaysSeconds".equals(path)) {
                return Arrays.asList("0", "0", "0");
            }
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

        private TestSender(String name) {
            this.name = name;
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
        public boolean hasPermission(String permission) {
            return true;
        }
    }
}

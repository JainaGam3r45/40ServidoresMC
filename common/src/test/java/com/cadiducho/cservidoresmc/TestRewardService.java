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
        TestScheduler scheduler = plugin.scheduler;
        RewardService rewardService = rewardService(plugin, scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin, vote("0"), vote("0"), vote("1")));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));
        scheduler.runNextDelayed();
        scheduler.runNextDelayed();
        scheduler.runNextDelayed();

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void rewardIsDeliveredOnce() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.deliverReward(sender.getName(), sender, true);
        rewardService.deliverReward(sender.getName(), sender, true);

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void duplicateRewardDoesNotDuplicateStreakMilestone() {
        TestPlugin plugin = new TestPlugin(tempDir);
        plugin.configuration.streakRewards.put("1", Collections.singletonList("give %player% diamond %streak%"));
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        plugin.setVoteStreakService(new VoteStreakService(plugin, new File(tempDir, "vote-streaks.properties"), () -> java.time.LocalDate.parse("2026-06-18")));
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.deliverReward(sender.getName(), sender, true);
        rewardService.deliverReward(sender.getName(), sender, true);

        assertEquals(Arrays.asList("give Cadiducho diamond 1", "money add Cadiducho 10"), plugin.commands);
    }

    @Test
    void apiErrorDoesNotDuplicateReward() {
        TestPlugin plugin = new TestPlugin(tempDir);
        TestScheduler scheduler = plugin.scheduler;
        RewardService rewardService = rewardService(plugin, scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin, new IOException("API down"), vote("1"), vote("1")));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));
        scheduler.runNextDelayed();
        scheduler.runNextDelayed();
        rewardService.handleVoteResponse(sender.getName(), sender, vote("1"));

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void exposesPendingAutoRewardState() {
        TestPlugin plugin = new TestPlugin(tempDir);
        TestScheduler scheduler = plugin.scheduler;
        RewardService rewardService = rewardService(plugin, scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin, vote("1")));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));

        assertEquals(true, rewardService.hasPendingReward("cadiducho"));
        scheduler.runNextDelayed();
        assertEquals(false, rewardService.hasPendingReward("Cadiducho"));
    }

    @Test
    void manualSuccessUsesRewardService() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("1"));

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(Collections.singletonList("&aGracias a Cadiducho por votarnos!"), plugin.broadcasts);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void localRewardedStateOverridesNotVotedMessage() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = new RewardService(plugin, tempDir, plugin.scheduler, () -> "2026-06-18", () -> 1_000L);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.deliverReward(sender, true);
        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));

        assertEquals(false, sender.messages.contains("&6No has votado hoy! Puedes hacerlo en &a https://40servidoresmc.es"));
        assertEquals(true, sender.messages.get(sender.messages.size() - 1).contains("Ya has votado y recibido tu recompensa"));
    }

    private RewardService rewardService(TestPlugin plugin, TestScheduler scheduler) {
        return new RewardService(plugin, new File(tempDir, "rewarded-votes.properties"), scheduler, () -> "2026-06-18");
    }

    private VoteResponse vote(String status) {
        return new Gson().fromJson("{\"web\":\"https://40servidoresmc.es\",\"status\":\"" + status + "\"}", VoteResponse.class);
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
        private final TestScheduler scheduler = new TestScheduler();
        private final List<String> commands = new ArrayList<>();
        private final List<String> broadcasts = new ArrayList<>();
        private ApiClient apiClient;
        private RewardService rewardService;
        private VoteStreakService voteStreakService;

        private TestPlugin(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        private void setApiClient(ApiClient apiClient) {
            this.apiClient = apiClient;
        }

        private void setRewardService(RewardService rewardService) {
            this.rewardService = rewardService;
        }

        private void setVoteStreakService(VoteStreakService voteStreakService) {
            this.voteStreakService = voteStreakService;
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
            return apiClient;
        }

        @Override
        public RewardService getRewardService() {
            return rewardService;
        }

        @Override
        public VoteStreakService getVoteStreakService() {
            return voteStreakService;
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
        private final Map<String, List<String>> streakRewards = new HashMap<>();

        private TestConfiguration(CSPlugin plugin) {
            this.plugin = plugin;
            strings.put("mensaje", "&6Gracias por votarnos! Aqui tienes tu premio: ");
            strings.put("broadcast.mensajeBroadcast", "&aGracias a %player% por votarnos!");
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
                return Collections.singletonList("money add %player% 10");
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
        private final List<String> messages = new ArrayList<>();

        private TestSender(String name) {
            this.name = name;
            this.uuid = "0f50d3c1-2d53-47d8-9f5a-10153b5f9770";
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

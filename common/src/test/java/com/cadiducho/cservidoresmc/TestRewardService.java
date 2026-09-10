package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.model.ServerStats;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRewardService {

    private static final String PLAYER_UUID = "0f50d3c1-2d53-47d8-9f5a-10153b5f9770";

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

    @Test
    void nextDayNotVotedShowsLinkAfterStaleSuccess() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<String> date = new AtomicReference<>("2026-06-18");
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, date::get, clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.deliverReward(sender, true);
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS + 1L);
        date.set("2026-06-19");

        plugin.setApiClient(new FakeApiClient(plugin, vote("0")));
        sender.messages.clear();
        rewardService.handleVoteResponse(sender.getName(), sender, vote("1"));

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("https://40servidoresmc.es")));
        assertFalse(sender.messages.stream().anyMatch(message -> message.contains("Aqui tienes tu premio")));
        assertEquals(1, plugin.commands.size());
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void nextDaySuccessStillDeliversWhenConfirmed() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<String> date = new AtomicReference<>("2026-06-18");
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, date::get, clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.deliverReward(sender, true);
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS + 1L);
        date.set("2026-06-19");

        plugin.setApiClient(new FakeApiClient(plugin, vote("1")));
        rewardService.handleVoteResponse(sender.getName(), sender, vote("1"));

        assertEquals(2, plugin.commands.size());
        assertEquals(2, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void alreadyVotedDoesNotPoisonRewardDate() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<String> date = new AtomicReference<>("2026-06-18");
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, date::get, clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.deliverReward(sender, true);
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS + 1L);
        date.set("2026-06-19");

        rewardService.handleVoteResponse(sender.getName(), sender, vote("2"));
        assertEquals("2026-06-18", store.lastRewardDate("Cadiducho", PLAYER_UUID));

        sender.messages.clear();
        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));
        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("https://40servidoresmc.es")));
        assertEquals("2026-06-18", store.lastRewardDate("Cadiducho", PLAYER_UUID));
    }

    @Test
    void alreadyVotedWithoutLocalCooldownShowsVoteLink() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender,
                vote("2", "FALLO. Votos ya recompensados.", "Varios votos"));

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("https://40servidoresmc.es")));
        assertFalse(sender.messages.stream().anyMatch(message ->
                message.contains("Ya has votado y recibido tu recompensa")));
        assertEquals(0L, store.lastVoteAt("Cadiducho", PLAYER_UUID));
    }

    @Test
    void alreadyVotedDoesNotExtendActiveCooldown() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        assertTrue(store.recordVote(sender.getName(), PLAYER_UUID, clock.get()));
        long originalLastVoteAt = store.lastVoteAt("Cadiducho", PLAYER_UUID);
        clock.addAndGet(TimeUnit.HOURS.toMillis(2));

        rewardService.handleVoteResponse(sender.getName(), sender, vote("2"));

        assertEquals(originalLastVoteAt, store.lastVoteAt("Cadiducho", PLAYER_UUID));
        assertTrue(sender.messages.stream().anyMatch(message ->
                message.contains("Ya has votado y recibido tu recompensa")
                        && message.contains(VoteTimeFormatter.formatDuration(
                        VoteCooldownRules.nextVoteInMillis(originalLastVoteAt, clock.get())))));
        assertFalse(sender.messages.stream().anyMatch(message -> message.contains("https://40servidoresmc.es")));
    }

    @Test
    void recheckAlreadyVotedDoesNotExtendCooldownWithoutReward() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        TestScheduler scheduler = plugin.scheduler;
        RewardService rewardService = new RewardService(plugin, store, scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin, vote("2")));
        TestSender sender = new TestSender("Cadiducho");
        scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));
        scheduler.runNextDelayed();

        assertEquals(0, plugin.commands.size());
        assertEquals(0L, store.lastVoteAt("Cadiducho", PLAYER_UUID));

        clock.addAndGet(TimeUnit.HOURS.toMillis(3));
        sender.messages.clear();
        rewardService.handleVoteResponse(sender.getName(), sender, vote("2"));

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("https://40servidoresmc.es")));
        assertFalse(sender.messages.stream().anyMatch(message ->
                message.contains("Ya has votado y recibido tu recompensa")));
        assertEquals(0L, store.lastVoteAt("Cadiducho", PLAYER_UUID));
    }

    @Test
    void recheckAlreadyVotedDoesNotRefreshExistingLastVoteAt() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        TestScheduler scheduler = plugin.scheduler;
        RewardService rewardService = new RewardService(plugin, store, scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin, vote("2")));
        TestSender sender = new TestSender("Cadiducho");
        scheduler.connect(sender);

        assertTrue(store.recordVote(sender.getName(), PLAYER_UUID, clock.get()));
        long originalLastVoteAt = store.lastVoteAt("Cadiducho", PLAYER_UUID);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));
        clock.addAndGet(TimeUnit.HOURS.toMillis(2));
        scheduler.runNextDelayed();

        assertEquals(originalLastVoteAt, store.lastVoteAt("Cadiducho", PLAYER_UUID));
        assertEquals(0, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void alreadyVotedWithoutLocalRewardDoesNotEarlyExit() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("2"));
        sender.messages.clear();

        assertFalse(rewardService.sendAlreadyRewardedIfActive(sender));
        assertTrue(sender.messages.isEmpty());
        assertEquals("", store.lastRewardDate("Cadiducho", PLAYER_UUID));
        assertEquals(0L, store.lastVoteAt("Cadiducho", PLAYER_UUID));
    }

    @Test
    void sendAlreadyRewardedIfActiveRequiresLocalRewardToday() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.deliverReward(sender, true);
        sender.messages.clear();

        assertTrue(rewardService.sendAlreadyRewardedIfActive(sender));
        assertTrue(sender.messages.stream().anyMatch(message ->
                message.contains("Ya has votado y recibido tu recompensa")));
    }

    @Test
    void statusZeroAlreadyRewardedWithoutLocalCooldownShowsLink() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0", "Voto Diario ya recompensado.", "Voto único"));

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("https://40servidoresmc.es")));
        assertFalse(sender.messages.stream().anyMatch(message ->
                message.contains("Ya has votado y recibido tu recompensa")));
    }

    @Test
    void statusZeroWithoutPendingVoteShowsLink() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender,
                vote("0", "FALLO. No hay voto que recompensar.", "Voto único"));

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("https://40servidoresmc.es")));
    }

    @Test
    void notVotedButListedUnrewardedShowsListedMessage() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        FakeApiClient api = new FakeApiClient(plugin, vote("1"));
        api.setServerStats(statsWithVote("JainaGamer45", 0));
        plugin.setApiClient(api);
        TestSender sender = new TestSender("JainaGamer45");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));

        assertTrue(sender.messages.stream().anyMatch(message ->
                message.toLowerCase().contains("registrado") || message.toLowerCase().contains("canjear")));
        assertFalse(sender.messages.stream().anyMatch(message ->
                message.toLowerCase().contains("no has votado")
                        || message.toLowerCase().contains("have not voted")));
        assertTrue(rewardService.hasPendingReward(sender.getName()));
    }

    @Test
    void notVotedButListedRewardedShowsAlreadyRewarded() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        store.recordVote("Cadiducho", PLAYER_UUID, clock.get());
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        FakeApiClient api = new FakeApiClient(plugin);
        api.setServerStats(statsWithVote("Cadiducho", 1));
        plugin.setApiClient(api);
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handleVoteResponse(sender.getName(), sender, vote("0"));

        assertTrue(sender.messages.stream().anyMatch(message ->
                message.contains("Ya has votado y recibido tu recompensa")));
        assertFalse(sender.messages.stream().anyMatch(message ->
                message.toLowerCase().contains("no has votado")));
    }

    private RewardService rewardService(TestPlugin plugin, TestScheduler scheduler) {
        return new RewardService(plugin, new File(tempDir, "rewarded-votes.properties"), scheduler, () -> "2026-06-18");
    }

    private VoteResponse vote(String status) {
        return vote(status, null, null);
    }

    private VoteResponse vote(String status, String mensaje, String tipovoto) {
        StringBuilder json = new StringBuilder("{\"web\":\"https://40servidoresmc.es\",\"status\":\"").append(status).append("\"");
        if (mensaje != null) {
            json.append(",\"mensaje\":\"").append(mensaje).append("\"");
        }
        if (tipovoto != null) {
            json.append(",\"tipovoto\":\"").append(tipovoto).append("\"");
        }
        json.append("}");
        return new Gson().fromJson(json.toString(), VoteResponse.class);
    }

    private static class FakeApiClient extends ApiClient {

        private final Queue<Object> responses = new ArrayDeque<>();
        private ServerStats serverStats = emptyStats();

        private FakeApiClient(CSPlugin plugin, Object... responses) {
            super(plugin, new Gson(), null, "http://localhost/api?clave=");
            this.responses.addAll(Arrays.asList(responses));
        }

        private void setServerStats(ServerStats serverStats) {
            this.serverStats = serverStats == null ? emptyStats() : serverStats;
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

        @Override
        public CompletableFuture<ServerStats> fetchServerStats() {
            return CompletableFuture.completedFuture(serverStats);
        }

        @Override
        public void invalidateServerStatsCache() {
        }

        @Override
        public void invalidateVoteCache(String player) {
        }
    }

    private static ServerStats emptyStats() {
        return new Gson().fromJson(
                "{\"nombre\":\"Test\",\"puesto\":1,\"votoshoy\":0,\"votoshoypremiados\":0,"
                        + "\"votossemanales\":0,\"votossemanalespremiados\":0,\"ultimos20votos\":[]}",
                ServerStats.class);
    }

    private static ServerStats statsWithVote(String usuario, int recompensado) {
        return new Gson().fromJson(
                "{\"nombre\":\"Test\",\"puesto\":1,\"votoshoy\":1,\"votoshoypremiados\":0,"
                        + "\"votossemanales\":1,\"votossemanalespremiados\":0,"
                        + "\"ultimos20votos\":[{\"usuario\":\"" + usuario + "\",\"recompensado\":"
                        + recompensado + "}]}",
                ServerStats.class);
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
            this.uuid = PLAYER_UUID;
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

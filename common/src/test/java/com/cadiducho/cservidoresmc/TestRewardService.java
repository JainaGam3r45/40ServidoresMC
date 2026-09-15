package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.model.AckResponse;
import com.cadiducho.cservidoresmc.model.PendingVote;
import com.cadiducho.cservidoresmc.model.PendingVotesResponse;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
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
    void pendingVotesDeliverRewardOnceAndAck() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        FakeApiClient api = new FakeApiClient(plugin);
        plugin.setApiClient(api);
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.handlePendingVotes(sender.getName(), sender, pendingWithVote(101L), VoteTrace.noop());

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
        assertEquals(1, api.ackCalls);
        assertTrue(api.lastAckDelivered);
        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("Aqui tienes tu premio")));
    }

    @Test
    void emptyPendingAndPuedeVotarShowsServerVoteLink() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.handlePendingVotes(sender.getName(), sender, emptyPending(true, null, "mi-servidor"), VoteTrace.noop());

        assertTrue(sender.messages.stream().anyMatch(message ->
                message.contains("https://www.40servidoresmc.es/mi-servidor/votar")));
        assertEquals(0, plugin.commands.size());
        assertTrue(rewardService.hasPendingReward("Cadiducho"));
    }

    @Test
    void emptyPendingWithoutSlugFallsBackToSiteHome() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.handlePendingVotes(sender.getName(), sender, emptyPending(true, null, null), VoteTrace.noop());

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains(RewardService.VOTE_URL)));
    }

    @Test
    void emptyPendingAndCannotVoteShowsAlreadyRewarded() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        RewardService rewardService = new RewardService(plugin, tempDir, plugin.scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        String next = "2026-06-18T12:00:00Z";
        rewardService.handlePendingVotes(sender.getName(), sender, emptyPending(false, next), VoteTrace.noop());

        assertTrue(sender.messages.stream().anyMatch(message ->
                message.contains("Ya has votado y recibido tu recompensa")));
        assertFalse(sender.messages.stream().anyMatch(message -> message.contains(RewardService.VOTE_URL)));
        assertEquals(0, plugin.commands.size());
    }

    @Test
    void autoRecheckDeliversWhenPendingAppears() {
        TestPlugin plugin = new TestPlugin(tempDir);
        TestScheduler scheduler = plugin.scheduler;
        RewardService rewardService = rewardService(plugin, scheduler);
        plugin.setRewardService(rewardService);
        FakeApiClient api = new FakeApiClient(plugin, pendingWithVote(55L));
        plugin.setApiClient(api);
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.handlePendingVotes(sender.getName(), sender, emptyPending(true, null), VoteTrace.noop());
        assertEquals(0, plugin.commands.size());
        assertTrue(rewardService.hasPendingReward("cadiducho"));

        scheduler.runNextDelayed();

        assertEquals(Collections.singletonList("money add Cadiducho 10"), plugin.commands);
        assertEquals(1, plugin.getPluginMetrics().getRewardsDelivered());
        assertFalse(rewardService.hasPendingReward("Cadiducho"));
        assertEquals(1, api.ackCalls);
    }

    @Test
    void autoRecheckStopsWhenWebSaysCannotVote() {
        TestPlugin plugin = new TestPlugin(tempDir);
        TestScheduler scheduler = plugin.scheduler;
        RewardService rewardService = rewardService(plugin, scheduler);
        plugin.setRewardService(rewardService);
        FakeApiClient api = new FakeApiClient(plugin, emptyPending(false, "2026-06-19T00:00:00Z"));
        plugin.setApiClient(api);
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.handlePendingVotes(sender.getName(), sender, emptyPending(true, null), VoteTrace.noop());
        scheduler.runNextDelayed();

        assertEquals(0, plugin.commands.size());
        assertEquals(0, api.ackCalls);
        assertFalse(rewardService.hasPendingReward("Cadiducho"));
    }

    @Test
    void apiErrorDuringRecheckDoesNotDuplicateReward() {
        TestPlugin plugin = new TestPlugin(tempDir);
        TestScheduler scheduler = plugin.scheduler;
        RewardService rewardService = rewardService(plugin, scheduler);
        plugin.setRewardService(rewardService);
        FakeApiClient api = new FakeApiClient(plugin, new IOException("API down"), pendingWithVote(9L));
        plugin.setApiClient(api);
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.handlePendingVotes(sender.getName(), sender, emptyPending(true, null), VoteTrace.noop());
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
        plugin.connect(sender);

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
        plugin.setVoteStreakService(new VoteStreakService(plugin, new File(tempDir, "vote-streaks.properties"),
                () -> java.time.LocalDate.parse("2026-06-18")));
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.deliverReward(sender.getName(), sender, true);
        rewardService.deliverReward(sender.getName(), sender, true);

        assertEquals(Arrays.asList("give Cadiducho diamond 1", "money add Cadiducho 10"), plugin.commands);
    }

    @Test
    void localActiveRewardBlocksEarlyExitOnlyWithoutPendingAcks() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, () -> "2026-06-18", clock::get);
        plugin.setRewardService(rewardService);
        FakeApiClient api = new FakeApiClient(plugin);
        plugin.setApiClient(api);
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.deliverReward(sender, true);
        sender.messages.clear();

        assertTrue(rewardService.sendAlreadyRewardedIfActive(sender));
        assertTrue(sender.messages.stream().anyMatch(message ->
                message.contains("Ya has votado y recibido tu recompensa")));

        api.addPendingAck(sender.getName(), Collections.singletonList(42L));
        sender.messages.clear();
        assertFalse(rewardService.sendAlreadyRewardedIfActive(sender));
        assertTrue(sender.messages.isEmpty());
    }

    @Test
    void nextDayPendingStillDeliversWhenConfirmed() {
        TestPlugin plugin = new TestPlugin(tempDir);
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicReference<String> date = new AtomicReference<>("2026-06-18");
        PlayerVoteStore store = new PlayerVoteStore(tempDir, plugin);
        RewardService rewardService = new RewardService(plugin, store, plugin.scheduler, date::get, clock::get);
        plugin.setRewardService(rewardService);
        plugin.setApiClient(new FakeApiClient(plugin));
        TestSender sender = new TestSender("Cadiducho");
        plugin.connect(sender);

        rewardService.deliverReward(sender, true);
        clock.addAndGet(VoteReminderService.VOTE_COOLDOWN_MILLIS + 1L);
        date.set("2026-06-19");

        rewardService.handlePendingVotes(sender.getName(), sender, pendingWithVote(77L), VoteTrace.noop());

        assertEquals(2, plugin.commands.size());
        assertEquals(2, plugin.getPluginMetrics().getRewardsDelivered());
    }

    @Test
    void offlinePlayerAcksAsNotDelivered() {
        TestPlugin plugin = new TestPlugin(tempDir);
        RewardService rewardService = rewardService(plugin, plugin.scheduler);
        plugin.setRewardService(rewardService);
        FakeApiClient api = new FakeApiClient(plugin);
        plugin.setApiClient(api);
        TestSender sender = new TestSender("Cadiducho");
        plugin.scheduler.connect(sender);

        rewardService.handlePendingVotes(sender.getName(), sender, pendingWithVote(3L), VoteTrace.noop());

        assertEquals(0, plugin.commands.size());
        assertEquals(1, api.ackCalls);
        assertFalse(api.lastAckDelivered);
        assertTrue(sender.messages.stream().anyMatch(message ->
                message.contains("No se pudo entregar el premio")));
    }

    private RewardService rewardService(TestPlugin plugin, TestScheduler scheduler) {
        return new RewardService(plugin, new File(tempDir, "rewarded-votes.properties"), scheduler, () -> "2026-06-18");
    }

    private static PendingVotesResponse pendingWithVote(long id) {
        PendingVote vote = new PendingVote();
        vote.setId(id);
        vote.setOrigen("web");
        PendingVotesResponse response = new PendingVotesResponse();
        response.setApiVersion(3);
        response.setJugador("Cadiducho");
        response.setVotosPendientes(Collections.singletonList(vote));
        response.setPuedeVotarYa(false);
        response.setReservaSegundos(300);
        return response;
    }

    private static PendingVotesResponse emptyPending(boolean puedeVotarYa, String siguienteVoto) {
        return emptyPending(puedeVotarYa, siguienteVoto, "mi-servidor");
    }

    private static PendingVotesResponse emptyPending(boolean puedeVotarYa, String siguienteVoto, String slug) {
        PendingVotesResponse response = new PendingVotesResponse();
        response.setApiVersion(3);
        response.setJugador("Cadiducho");
        response.setVotosPendientes(Collections.<PendingVote>emptyList());
        response.setPuedeVotarYa(puedeVotarYa);
        response.setSiguienteVoto(siguienteVoto);
        if (slug != null) {
            PendingVotesResponse.ServerInfo server = new PendingVotesResponse.ServerInfo();
            server.setSlug(slug);
            response.setServidor(server);
        }
        return response;
    }

    private static class FakeApiClient extends ApiClient {

        private final Queue<Object> pendingResponses = new ArrayDeque<>();
        private int ackCalls;
        private boolean lastAckDelivered = true;

        private FakeApiClient(CSPlugin plugin, Object... pendingResponses) {
            super(plugin, new Gson(), null, "http://localhost");
            this.pendingResponses.addAll(Arrays.asList(pendingResponses));
        }

        @Override
        public CompletableFuture<PendingVotesResponse> fetchPendingVotes(String nick, VoteTrace trace) {
            Object next = pendingResponses.isEmpty() ? emptyPending(true, null) : pendingResponses.remove();
            if (next instanceof Throwable) {
                CompletableFuture<PendingVotesResponse> future = new CompletableFuture<>();
                future.completeExceptionally((Throwable) next);
                return future;
            }
            return CompletableFuture.completedFuture((PendingVotesResponse) next);
        }

        @Override
        public CompletableFuture<AckResponse> sendAck(List<Long> voteIds, String nick, boolean delivered,
                                                      String userIp, VoteTrace trace) {
            ackCalls++;
            lastAckDelivered = delivered;
            AckResponse response = new AckResponse();
            response.setApiVersion(3);
            response.setEntregado(delivered);
            response.setConfirmados(voteIds);
            return CompletableFuture.completedFuture(response);
        }
    }

    private static class TestPlugin implements CSPlugin {

        private final File dataFolder;
        private final PluginMetrics pluginMetrics = new PluginMetrics();
        private final TestConfiguration configuration = new TestConfiguration(this);
        private final TestScheduler scheduler = new TestScheduler();
        private final List<CSCommandSender> online = new ArrayList<>();
        private final List<String> commands = new ArrayList<>();
        private final List<String> broadcasts = new ArrayList<>();
        private ApiClient apiClient;
        private RewardService rewardService;
        private VoteStreakService voteStreakService;

        private TestPlugin(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        private void connect(TestSender sender) {
            scheduler.connect(sender);
            online.add(sender);
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
        public boolean dispatchCommandResult(String command) {
            commands.add(command);
            return true;
        }

        @Override
        public List<CSCommandSender> getOnlinePlayers() {
            return new ArrayList<>(online);
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
            if ("comandosCustom".equals(path) || "rewards.commands".equals(path)) {
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

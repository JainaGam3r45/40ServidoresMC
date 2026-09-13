package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cache.Clock;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
import com.cadiducho.cservidoresmc.model.AckResponse;
import com.cadiducho.cservidoresmc.model.PendingVotesResponse;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestApiClientCache {

    @Test
    void serverStatsUsesCacheBeforeTtl() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(serverStatsJson("Servidor", 1));
        ApiClient apiClient = apiClient(requester, clock);

        ServerStats firstStats = apiClient.fetchServerStats().join();
        ServerStats secondStats = apiClient.fetchServerStats().join();

        assertSame(firstStats, secondStats);
        assertSame(firstStats, apiClient.cachedServerStats());
        assertEquals(0L, apiClient.serverStatsCacheAgeMillis());
        assertEquals("ok", apiClient.apiStatus());
        assertEquals(1, requester.requests());
    }

    @Test
    void serverStatsRefreshesAfterTtl() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(serverStatsJson("Servidor", 1));
        requester.addResponse(serverStatsJson("Servidor", 2));
        ApiClient apiClient = apiClient(requester, clock);

        ServerStats firstStats = apiClient.fetchServerStats().join();
        clock.advanceSeconds(61);
        assertSame(firstStats, apiClient.cachedServerStats());
        assertEquals(61_000L, apiClient.serverStatsCacheAgeMillis());
        ServerStats refreshedStats = apiClient.fetchServerStats().join();

        assertNotSame(firstStats, refreshedStats);
        assertEquals(1, firstStats.getPosition());
        assertEquals(2, refreshedStats.getPosition());
        assertEquals(2, requester.requests());
    }

    @Test
    void serverStatsCanBeInvalidatedManually() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(serverStatsJson("Servidor", 1));
        requester.addResponse(serverStatsJson("Servidor", 2));
        ApiClient apiClient = apiClient(requester, clock);

        ServerStats firstStats = apiClient.fetchServerStats().join();
        apiClient.invalidateServerStatsCache();
        ServerStats refreshedStats = apiClient.fetchServerStats().join();

        assertNotSame(firstStats, refreshedStats);
        assertEquals(2, refreshedStats.getPosition());
        assertEquals(2, requester.requests());
    }

    @Test
    void pendingVotesAreNotCached() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(pendingJson(true, 0));
        requester.addResponse(pendingJson(true, 0));
        ApiClient apiClient = apiClient(requester, clock);

        PendingVotesResponse first = apiClient.fetchPendingVotes("Cadiducho").join();
        PendingVotesResponse second = apiClient.fetchPendingVotes("cadiducho").join();

        assertTrue(first.isPuedeVotarYa());
        assertTrue(second.isPuedeVotarYa());
        assertEquals(2, requester.requests());
    }

    @Test
    void pendingWithVotesInvalidatesStatsCache() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(serverStatsJson("Servidor", 1));
        requester.addResponse(pendingJson(false, 1));
        requester.addResponse(serverStatsJson("Servidor", 2));
        ApiClient apiClient = apiClient(requester, clock);

        ServerStats cachedStats = apiClient.fetchServerStats().join();
        PendingVotesResponse pending = apiClient.fetchPendingVotes("Cadiducho").join();
        assertEquals(1, pending.safePendingVotes().size());

        apiClient.invalidateServerStatsCache();
        ServerStats refreshedStats = apiClient.fetchServerStats().join();

        assertNotSame(cachedStats, refreshedStats);
        assertEquals(2, refreshedStats.getPosition());
        assertEquals(3, requester.requests());
    }

    @Test
    void sendAckPostsToV3Endpoint() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(ackJson());
        ApiClient apiClient = apiClient(requester, clock);

        AckResponse ack = apiClient.sendAck(Collections.singletonList(254411L), "Cadiducho", true, "203.0.113.10").join();

        assertTrue(ack.isEntregado());
        assertEquals(1, requester.requests());
        assertTrue(requester.lastUrl.contains("/api/vote/v3/ack"));
        assertEquals("POST", requester.lastMethod);
        assertTrue(requester.lastAuthorization != null && requester.lastAuthorization.startsWith("Bearer "));
    }

    @Test
    void truncatesLegacyApi2UrlToBase() {
        assertEquals("https://www.40servidoresmc.es",
                ApiClient.truncateToApiBase("https://www.40servidoresmc.es/api2.php?clave="));
        assertEquals("http://localhost:8080",
                ApiClient.truncateToApiBase("http://localhost:8080/api2.php?clave=abc"));
    }

    @Test
    void apiRequestsAreCounted() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        TestPlugin plugin = new TestPlugin();
        requester.addResponse(serverStatsJson("Servidor", 1));
        ApiClient apiClient = new ApiClient(plugin, new Gson(), requester, "http://localhost/api?clave=", clock);

        apiClient.fetchServerStats().join();

        assertEquals(1, plugin.getPluginMetrics().getApiRequests());
    }

    @Test
    void apiFailuresAreCounted() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        TestPlugin plugin = new TestPlugin();
        ApiClient apiClient = new ApiClient(plugin, new Gson(), requester, "http://localhost/api?clave=", clock);

        assertThrows(CompletionException.class, () -> apiClient.fetchServerStats().join());

        assertEquals(1, plugin.getPluginMetrics().getApiRequests());
        assertEquals(1, plugin.getPluginMetrics().getApiFailures());
        assertEquals("error", apiClient.apiStatus());
    }

    @Test
    void pendingVoteChecksAreCountedEachTime() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        TestPlugin plugin = new TestPlugin();
        requester.addResponse(pendingJson(true, 0));
        requester.addResponse(pendingJson(true, 0));
        ApiClient apiClient = new ApiClient(plugin, new Gson(), requester, "http://localhost/api?clave=", clock);

        apiClient.fetchPendingVotes("Cadiducho").join();
        apiClient.fetchPendingVotes("cadiducho").join();

        assertEquals(2, plugin.getPluginMetrics().getVoteChecks());
        assertEquals(2, plugin.getPluginMetrics().getApiRequests());
    }

    @Test
    void retryPendingAcksResendsStoredIds() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(ackJson());
        ApiClient apiClient = apiClient(requester, clock);

        apiClient.addPendingAck("Cadiducho", Collections.singletonList(99L));
        assertFalse(apiClient.peekPendingAcks("Cadiducho").isEmpty());

        AckResponse ack = apiClient.retryPendingAcks("Cadiducho").join();

        assertTrue(ack.isEntregado());
        assertTrue(apiClient.peekPendingAcks("Cadiducho").isEmpty());
        assertEquals(1, requester.requests());
    }

    private ApiClient apiClient(CountingRequester requester, ManualClock clock) {
        return new ApiClient(new TestPlugin(), new Gson(), requester, "http://localhost/api?clave=", clock);
    }

    private static String serverStatsJson(String name, int position) {
        return "{\"nombre\":\"" + name + "\",\"puesto\":" + position + ",\"votoshoy\":10,"
                + "\"votoshoypremiados\":5,\"votossemanales\":20,\"votossemanalespremiados\":15,"
                + "\"ultimos20votos\":[]}";
    }

    private static String pendingJson(boolean puedeVotarYa, int voteCount) {
        StringBuilder votes = new StringBuilder("[");
        for (int i = 0; i < voteCount; i++) {
            if (i > 0) {
                votes.append(',');
            }
            votes.append("{\"id\":").append(100 + i).append(",\"origen\":\"web\"}");
        }
        votes.append(']');
        return "{\"api_version\":3,\"jugador\":\"Cadiducho\",\"votos_pendientes\":" + votes
                + ",\"reserva_segundos\":300,\"puede_votar_ya\":" + puedeVotarYa
                + ",\"siguiente_voto\":null}";
    }

    private static String ackJson() {
        return "{\"api_version\":3,\"confirmados\":[254411],\"ya_confirmados\":[],\"liberados\":[],"
                + "\"desconocidos\":[],\"entregado\":true}";
    }

    private static class ManualClock implements Clock {

        private long timeMillis;

        @Override
        public long currentTimeMillis() {
            return timeMillis;
        }

        private void advanceSeconds(int seconds) {
            timeMillis += seconds * 1000L;
        }
    }

    private static class CountingRequester extends HttpRequester {

        private final AtomicInteger requests = new AtomicInteger();
        private final List<String> responses = new ArrayList<>();
        private String lastUrl = "";
        private String lastMethod = "";
        private String lastAuthorization;

        @Override
        public String request(URL url, String method, String requestName, HttpConfig config, HttpLogger logger) throws IOException {
            int request = requests.incrementAndGet();
            lastUrl = url.toString();
            lastMethod = method;
            lastAuthorization = config == null ? null : config.getAuthorization();
            if (responses.size() < request) {
                throw new IOException("No fake response configured for request " + request + ".");
            }
            return responses.get(request - 1);
        }

        private void addResponse(String response) {
            responses.add(response);
        }

        private int requests() {
            return requests.get();
        }
    }

    private static class TestPlugin implements CSPlugin {

        private final TestConfiguration configuration = new TestConfiguration(this);
        private final PluginMetrics pluginMetrics = new PluginMetrics();

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
            return new File(".");
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
            strings.put("clave", "key");
            strings.put("api.key", "key");
            booleans.put("debug", false);
            booleans.put("cache.enabled", true);
            ints.put("cache.serverStatsTtlSeconds", 60);
            ints.put("cache.voteCheckNegativeTtlSeconds", 5);
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
}

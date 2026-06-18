package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cache.Clock;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    void notVotedResponsesUseShortNegativeCache() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(voteJson("0"));
        ApiClient apiClient = apiClient(requester, clock);

        VoteResponse firstVote = apiClient.validateVote("Cadiducho").join();
        VoteResponse cachedVote = apiClient.validateVote("cadiducho").join();

        assertSame(firstVote, cachedVote);
        assertEquals(VoteStatus.NOT_VOTED, cachedVote.getStatus());
        assertEquals(1, requester.requests());
    }

    @Test
    void successfulVotesAreNotCachedAndInvalidateStats() {
        ManualClock clock = new ManualClock();
        CountingRequester requester = new CountingRequester();
        requester.addResponse(serverStatsJson("Servidor", 1));
        requester.addResponse(voteJson("1"));
        requester.addResponse(voteJson("1"));
        requester.addResponse(serverStatsJson("Servidor", 2));
        ApiClient apiClient = apiClient(requester, clock);

        ServerStats cachedStats = apiClient.fetchServerStats().join();
        VoteResponse firstVote = apiClient.validateVote("Cadiducho").join();
        VoteResponse secondVote = apiClient.validateVote("Cadiducho").join();
        ServerStats refreshedStats = apiClient.fetchServerStats().join();

        assertEquals(VoteStatus.SUCCESS, firstVote.getStatus());
        assertEquals(VoteStatus.SUCCESS, secondVote.getStatus());
        assertNotSame(cachedStats, refreshedStats);
        assertEquals(2, refreshedStats.getPosition());
        assertEquals(4, requester.requests());
    }

    private ApiClient apiClient(CountingRequester requester, ManualClock clock) {
        return new ApiClient(new TestPlugin(), new Gson(), requester, "http://localhost/api?clave=", clock);
    }

    private static String serverStatsJson(String name, int position) {
        return "{\"nombre\":\"" + name + "\",\"puesto\":" + position + ",\"votoshoy\":10,"
                + "\"votoshoypremiados\":5,\"votossemanales\":20,\"votossemanalespremiados\":15,"
                + "\"ultimos20votos\":[]}";
    }

    private static String voteJson(String status) {
        return "{\"web\":\"https://40servidoresmc.es\",\"status\":\"" + status + "\"}";
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

        @Override
        public String request(URL url, String method, String requestName, HttpConfig config, HttpLogger logger) throws IOException {
            int request = requests.incrementAndGet();
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
        public Updater getUpdater() {
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

    private static class TestConfiguration implements CSConfiguration {

        private final CSPlugin plugin;
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Integer> ints = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();

        private TestConfiguration(CSPlugin plugin) {
            this.plugin = plugin;
            strings.put("clave", "key");
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

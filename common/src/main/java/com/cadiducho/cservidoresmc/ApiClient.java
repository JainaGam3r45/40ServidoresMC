package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cache.Clock;
import com.cadiducho.cservidoresmc.cache.SystemClock;
import com.cadiducho.cservidoresmc.cache.TtlCache;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpException;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
import com.cadiducho.cservidoresmc.http.UserAgent;
import com.cadiducho.cservidoresmc.model.AckRequest;
import com.cadiducho.cservidoresmc.model.AckResponse;
import com.cadiducho.cservidoresmc.model.PendingVotesResponse;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.util.PendingAckStore;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public class ApiClient {

    /** Production API host. Not configurable; tests may inject another base via constructor. */
    public static final String API_BASE = "https://www.40servidoresmc.es";
    private static final String SERVER_STATS_CACHE_KEY = "server-stats";
    private static final String API_STATUS_UNKNOWN = "unknown";
    private static final String API_STATUS_OK = "ok";
    private static final String API_STATUS_ERROR = "error";
    private static final int DEFAULT_SERVER_STATS_TTL_SECONDS = 60;

    private final CSPlugin plugin;
    private final Gson gson;
    private final HttpRequester httpRequester;
    private final Executor executor;
    private final String configuredApiUrl;
    private final TtlCache<String, ServerStats> serverStatsCache;
    private final PendingAckStore pendingAckStore;
    private volatile String apiStatus = API_STATUS_UNKNOWN;

    public ApiClient(CSPlugin plugin, Gson gson) {
        this(plugin, gson, new HttpRequester(), null, new SystemClock(), plugin.getAsyncExecutor(), new PendingAckStore());
    }

    ApiClient(CSPlugin plugin, Gson gson, HttpRequester httpRequester, String apiUrl) {
        this(plugin, gson, httpRequester, apiUrl, new SystemClock(), ForkJoinPool.commonPool(), new PendingAckStore());
    }

    ApiClient(CSPlugin plugin, Gson gson, HttpRequester httpRequester, String apiUrl, Clock clock) {
        this(plugin, gson, httpRequester, apiUrl, clock, ForkJoinPool.commonPool(), new PendingAckStore());
    }

    public ApiClient(CSPlugin plugin, Gson gson, Executor executor) {
        this(plugin, gson, new HttpRequester(), null, new SystemClock(), executor, new PendingAckStore());
    }

    ApiClient(CSPlugin plugin, Gson gson, HttpRequester httpRequester, String apiUrl, Clock clock, Executor executor) {
        this(plugin, gson, httpRequester, apiUrl, clock, executor, new PendingAckStore());
    }

    ApiClient(CSPlugin plugin, Gson gson, HttpRequester httpRequester, String apiUrl, Clock clock,
              Executor executor, PendingAckStore pendingAckStore) {
        this.plugin = plugin;
        this.gson = gson;
        this.httpRequester = httpRequester;
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
        this.configuredApiUrl = apiUrl;
        this.serverStatsCache = new TtlCache<>(clock);
        this.pendingAckStore = pendingAckStore == null ? new PendingAckStore() : pendingAckStore;
    }

    public String apiKey() {
        return plugin.getCSConfiguration().getString("api.key", "clave", "");
    }

    public int timeOut() {
        return plugin.getCSConfiguration().getInt("api.readTimeout", "readTimeOut", HttpConfig.DEFAULT_TIMEOUT);
    }

    /**
     * Base host for v3 paths and legacy stats. Hardcoded in production;
     * constructor override is only for unit tests / local mocks.
     */
    public String getApiBase() {
        if (configuredApiUrl != null && !configuredApiUrl.trim().isEmpty()) {
            return truncateToApiBase(configuredApiUrl.trim());
        }
        return API_BASE;
    }

    static String truncateToApiBase(String url) {
        String trimmed = url.trim();
        int legacy = indexOfIgnoreCase(trimmed, "/api2.php");
        if (legacy >= 0) {
            trimmed = trimmed.substring(0, legacy);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public CompletableFuture<PendingVotesResponse> fetchPendingVotes(String nick) {
        return fetchPendingVotes(nick, VoteTrace.noop());
    }

    public CompletableFuture<PendingVotesResponse> fetchPendingVotes(String nick, VoteTrace trace) {
        final VoteTrace voteTrace = trace == null ? VoteTrace.noop() : trace;
        return submitRequest(() -> {
            try {
                plugin.getPluginMetrics().incrementVoteChecks();
                String path = "/api/vote/v3/pending?nick=" + urlEncode(nick);
                String fullUrl = getApiBase() + path;
                long started = System.currentTimeMillis();
                plugin.getPluginMetrics().incrementApiRequests();
                String body = httpRequester.request(
                        new URL(fullUrl),
                        "GET",
                        "API v3 pending",
                        bearerConfig(null),
                        httpLogger()
                );
                long elapsed = System.currentTimeMillis() - started;
                voteTrace.pendingHttp(nick, 200, elapsed, path);
                PendingVotesResponse response = gson.fromJson(body, PendingVotesResponse.class);
                if (response == null) {
                    throw new IOException("Empty pending response");
                }
                voteTrace.pendingBody(
                        response.safePendingVotes().size(),
                        response.isPuedeVotarYa(),
                        response.getSiguienteVoto(),
                        response.getReservaSegundos(),
                        body
                );
                apiStatus = API_STATUS_OK;
                return response;
            } catch (HttpException e) {
                plugin.getPluginMetrics().incrementApiFailures();
                apiStatus = API_STATUS_ERROR;
                voteTrace.pendingHttp(nick, e.getStatusCode(), -1L, "/api/vote/v3/pending");
                voteTrace.error(e.getMessage());
                throw new IllegalStateException("Cannot execute V3 pending API call: " + e.getMessage(), e);
            } catch (IOException | JsonSyntaxException e) {
                plugin.getPluginMetrics().incrementApiFailures();
                apiStatus = API_STATUS_ERROR;
                voteTrace.error(e.getMessage());
                throw new IllegalStateException("Cannot execute V3 pending API call: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<AckResponse> sendAck(List<Long> voteIds, String nick, boolean delivered, String userIp) {
        return sendAck(voteIds, nick, delivered, userIp, VoteTrace.noop());
    }

    public CompletableFuture<AckResponse> sendAck(List<Long> voteIds, String nick, boolean delivered,
                                                  String userIp, VoteTrace trace) {
        final VoteTrace voteTrace = trace == null ? VoteTrace.noop() : trace;
        final List<Long> ids = voteIds == null ? Collections.<Long>emptyList() : new ArrayList<Long>(voteIds);
        final String ip = userIp == null ? "" : userIp;
        return submitRequest(() -> {
            try {
                AckRequest payload = new AckRequest(ids, delivered, nick, ip);
                String json = gson.toJson(payload);
                plugin.getPluginMetrics().incrementApiRequests();
                String body = httpRequester.request(
                        new URL(getApiBase() + "/api/vote/v3/ack"),
                        "POST",
                        "API v3 ack",
                        bearerConfig(json),
                        httpLogger()
                );
                voteTrace.ackHttp(200, delivered, ids.toString(), voteTrace.describeIp(ip));
                voteTrace.ackBody(body);
                AckResponse response = gson.fromJson(body, AckResponse.class);
                apiStatus = API_STATUS_OK;
                return response;
            } catch (HttpException e) {
                plugin.getPluginMetrics().incrementApiFailures();
                apiStatus = API_STATUS_ERROR;
                voteTrace.ackHttp(e.getStatusCode(), delivered, ids.toString(), voteTrace.describeIp(ip));
                voteTrace.error(e.getMessage());
                throw new IllegalStateException("Cannot execute V3 ack API call: " + e.getMessage(), e);
            } catch (IOException | JsonSyntaxException e) {
                plugin.getPluginMetrics().incrementApiFailures();
                apiStatus = API_STATUS_ERROR;
                voteTrace.error(e.getMessage());
                throw new IllegalStateException("Cannot execute V3 ack API call: " + e.getMessage(), e);
            }
        });
    }

    public void addPendingAck(String nick, List<Long> voteIds) {
        pendingAckStore.add(nick, voteIds);
    }

    public List<Long> peekPendingAcks(String nick) {
        return pendingAckStore.peek(nick);
    }

    public CompletableFuture<AckResponse> retryPendingAcks(String nick) {
        return retryPendingAcks(nick, VoteTrace.noop());
    }

    public CompletableFuture<AckResponse> retryPendingAcks(String nick, VoteTrace trace) {
        final VoteTrace voteTrace = trace == null ? VoteTrace.noop() : trace;
        List<Long> ids = pendingAckStore.take(nick);
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        voteTrace.retryAcks(ids.size(), false);
        return sendAck(ids, nick, true, "", voteTrace).handle((ack, error) -> {
            if (error != null || ack == null) {
                pendingAckStore.add(nick, ids);
                voteTrace.retryAcks(ids.size(), false);
                return null;
            }
            voteTrace.retryAcks(ids.size(), true);
            return ack;
        });
    }

    public CompletableFuture<ServerStats> fetchServerStats() {
        ServerStats cachedStats = cacheEnabled() ? serverStatsCache.get(SERVER_STATS_CACHE_KEY) : null;
        if (cachedStats != null) {
            plugin.debugLog("Usando estadísticas cacheadas.");
            return CompletableFuture.completedFuture(cachedStats);
        }

        return submitRequest(() -> {
            try {
                ServerStats serverStats = fetchLegacyStats();
                cacheServerStats(serverStats);
                return serverStats;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute API call: " + e.getMessage(), e);
            }
        });
    }

    public ServerStats cachedServerStats() {
        return cacheEnabled() ? serverStatsCache.peek(SERVER_STATS_CACHE_KEY) : null;
    }

    public long serverStatsCacheAgeMillis() {
        return cacheEnabled() ? serverStatsCache.ageMillis(SERVER_STATS_CACHE_KEY) : -1L;
    }

    public String apiStatus() {
        return apiStatus;
    }

    public void invalidateServerStatsCache() {
        serverStatsCache.invalidate(SERVER_STATS_CACHE_KEY);
    }

    /** Kept for callers that still invalidate after rewards; no-op for vote cache (removed in v3). */
    public void invalidateVoteCache(String player) {
    }

    public void invalidateAllCache() {
        serverStatsCache.clear();
    }

    private ServerStats fetchLegacyStats() throws IOException {
        String url = v2StatsUrl();
        String requestName = "API 40ServidoresMC estadísticas GET";
        plugin.getPluginMetrics().incrementApiRequests();
        try {
            plugin.debugLog(requestName + " iniciado.");
            String body = httpRequester.request(new URL(url), "GET", requestName, httpConfig(), httpLogger());
            ServerStats fetched = gson.fromJson(body, ServerStats.class);
            apiStatus = API_STATUS_OK;
            return fetched;
        } catch (IOException | JsonSyntaxException e) {
            plugin.getPluginMetrics().incrementApiFailures();
            apiStatus = API_STATUS_ERROR;
            plugin.debugLog(requestName + " falló: " + e.getMessage());
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    private String v2StatsUrl() throws IOException {
        return getApiBase() + "/api2.php?clave=" + urlEncode(apiKey()) + "&estadisticas=1";
    }

    private HttpConfig bearerConfig(String body) {
        return HttpConfig.from(plugin.getCSConfiguration(), userAgent(), "Bearer " + apiKey(), body);
    }

    private HttpConfig httpConfig() {
        return HttpConfig.from(plugin.getCSConfiguration(), userAgent());
    }

    private String userAgent() {
        return UserAgent.build(plugin.getPluginVersion(), plugin.getServerPlatform(), plugin.getServerVersion());
    }

    private HttpLogger httpLogger() {
        return new HttpLogger() {
            @Override
            public void debug(String text) {
                plugin.debugLog(text);
            }

            @Override
            public void error(String text) {
                plugin.logError(text);
            }

            @Override
            public void retry(String text) {
                plugin.getPluginMetrics().incrementRetries();
                plugin.debugLog(text);
            }
        };
    }

    private String urlEncode(String text) throws IOException {
        return URLEncoder.encode(text == null ? "" : text, "UTF-8");
    }

    private void cacheServerStats(ServerStats serverStats) {
        if (!cacheEnabled()) {
            return;
        }
        serverStatsCache.put(SERVER_STATS_CACHE_KEY, serverStats, secondsToMillis(serverStatsTtlSeconds()));
    }

    private boolean cacheEnabled() {
        return plugin.getCSConfiguration().getBoolean("cache.enabled", true);
    }

    private int serverStatsTtlSeconds() {
        return plugin.getCSConfiguration().getInt("cache.serverStatsTtlSeconds", DEFAULT_SERVER_STATS_TTL_SECONDS);
    }

    private long secondsToMillis(int seconds) {
        return Math.max(0L, seconds) * 1000L;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    private <T> CompletableFuture<T> submitRequest(Supplier<T> supplier) {
        try {
            return CompletableFuture.supplyAsync(supplier, executor);
        } catch (RejectedExecutionException ex) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("HTTP executor rejected the request.", ex));
            return future;
        }
    }
}

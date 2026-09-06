package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cache.Clock;
import com.cadiducho.cservidoresmc.cache.SystemClock;
import com.cadiducho.cservidoresmc.cache.TtlCache;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
import com.cadiducho.cservidoresmc.http.UserAgent;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public class ApiClient {

    private static final String API_URL = "https://40servidoresmc.es/api2.php?clave=";
    private static final String SERVER_STATS_CACHE_KEY = "server-stats";
    private static final String API_STATUS_UNKNOWN = "unknown";
    private static final String API_STATUS_OK = "ok";
    private static final String API_STATUS_ERROR = "error";
    private static final int DEFAULT_SERVER_STATS_TTL_SECONDS = 60;
    private static final int DEFAULT_VOTE_CHECK_NEGATIVE_TTL_SECONDS = 5;

    private final CSPlugin plugin;
    private final Gson gson;
    private final HttpRequester httpRequester;
    private final Executor executor;
    private final String apiUrl;
    private final TtlCache<String, ServerStats> serverStatsCache;
    private final TtlCache<String, VoteResponse> voteCache;
    private volatile String apiStatus = API_STATUS_UNKNOWN;

    public ApiClient(CSPlugin plugin, Gson gson) {
        this(plugin, gson, new HttpRequester(), API_URL, new SystemClock(), plugin.getAsyncExecutor());
    }

    ApiClient(CSPlugin plugin, Gson gson, HttpRequester httpRequester, String apiUrl) {
        this(plugin, gson, httpRequester, apiUrl, new SystemClock(), ForkJoinPool.commonPool());
    }

    ApiClient(CSPlugin plugin, Gson gson, HttpRequester httpRequester, String apiUrl, Clock clock) {
        this(plugin, gson, httpRequester, apiUrl, clock, ForkJoinPool.commonPool());
    }

    public ApiClient(CSPlugin plugin, Gson gson, Executor executor) {
        this(plugin, gson, new HttpRequester(), API_URL, new SystemClock(), executor);
    }

    ApiClient(CSPlugin plugin, Gson gson, HttpRequester httpRequester, String apiUrl, Clock clock, Executor executor) {
        this.plugin = plugin;
        this.gson = gson;
        this.httpRequester = httpRequester;
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
        this.apiUrl = apiUrl;
        this.serverStatsCache = new TtlCache<>(clock);
        this.voteCache = new TtlCache<>(clock);
    }

    public String apiKey() {
        return plugin.getCSConfiguration().getString("api.key", "clave", "");
    }

    public int timeOut() {
        return plugin.getCSConfiguration().getInt("api.readTimeout", "readTimeOut", HttpConfig.DEFAULT_TIMEOUT);
    }

    public CompletableFuture<VoteResponse> validateVote(String player) {
        String cacheKey = voteCacheKey(player);
        VoteResponse cachedVote = cacheEnabled() ? voteCache.get(cacheKey) : null;
        if (cachedVote != null) {
            plugin.debugLog("Usando validación de voto cacheada para " + player + ".");
            return CompletableFuture.completedFuture(cachedVote);
        }

        return submitRequest(() -> {
            try {
                plugin.getPluginMetrics().incrementVoteChecks();
                VoteResponse voteResponse = fetchData("&nombre=" + urlEncode(player), "GET", VoteResponse.class);
                cacheVoteResponse(cacheKey, voteResponse);
                invalidateCachesForVoteResponse(cacheKey, voteResponse);
                return voteResponse;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute API call: " + e.getMessage(), e);
            }
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
                ServerStats serverStats = fetchData("&estadisticas=1", "GET", ServerStats.class);
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

    public void invalidateVoteCache(String player) {
        voteCache.invalidate(voteCacheKey(player));
    }

    public void invalidateAllCache() {
        serverStatsCache.clear();
        voteCache.clear();
    }

    /**
     * Obtener datos de la API, según unos parámetros dados, y parsearlo a un objeto
     * @param params Parámetros HTTP de la petición
     * @param method Método HTTP
     * @param type Clase a la que convertir los datos recibidos
     * @param <T> Tipo que retornará
     * @return El objeto con los datos solicitados a la API
     * @throws IOException Si falla al parsear o al conectarse a la API
     */
    private <T> T fetchData(String params, String method, Class<T> type) throws IOException {
        URL url = new URL(apiUrl + urlEncode(apiKey()) + params);
        String requestName = "API 40ServidoresMC " + apiOperation(params) + " " + method;
        plugin.getPluginMetrics().incrementApiRequests();
        try {
            plugin.debugLog(requestName + " iniciado.");
            String body = httpRequester.request(url, method, requestName, httpConfig(), httpLogger());
            T fetched = gson.fromJson(body, type);
            apiStatus = API_STATUS_OK;
            return fetched;
        } catch (IOException | JsonSyntaxException e) {
            plugin.getPluginMetrics().incrementApiFailures();
            apiStatus = API_STATUS_ERROR;
            plugin.debugLog(requestName + " falló: " + e.getMessage());
            throw e;
        }
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

    private String apiOperation(String params) {
        if (params != null && params.contains("estadisticas=1")) {
            return "estadísticas";
        }
        if (params != null && params.contains("nombre=")) {
            return "voto";
        }
        return "petición";
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

    private void cacheVoteResponse(String cacheKey, VoteResponse voteResponse) {
        if (!cacheEnabled() || voteResponse == null || voteResponse.getStatus() != VoteStatus.NOT_VOTED) {
            return;
        }

        voteCache.put(cacheKey, voteResponse, secondsToMillis(voteCheckNegativeTtlSeconds()));
    }

    private void invalidateCachesForVoteResponse(String cacheKey, VoteResponse voteResponse) {
        if (voteResponse == null) {
            return;
        }

        VoteStatus status = voteResponse.getStatus();
        if (status == VoteStatus.SUCCESS || status == VoteStatus.ALREADY_VOTED) {
            voteCache.invalidate(cacheKey);
            invalidateServerStatsCache();
        }
    }

    private boolean cacheEnabled() {
        return plugin.getCSConfiguration().getBoolean("cache.enabled", true);
    }

    private int serverStatsTtlSeconds() {
        return plugin.getCSConfiguration().getInt("cache.serverStatsTtlSeconds", DEFAULT_SERVER_STATS_TTL_SECONDS);
    }

    private int voteCheckNegativeTtlSeconds() {
        return plugin.getCSConfiguration().getInt("cache.voteCheckNegativeTtlSeconds", DEFAULT_VOTE_CHECK_NEGATIVE_TTL_SECONDS);
    }

    private long secondsToMillis(int seconds) {
        return Math.max(0L, seconds) * 1000L;
    }

    private String voteCacheKey(String player) {
        return (player == null ? "" : player).toLowerCase(Locale.ROOT);
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

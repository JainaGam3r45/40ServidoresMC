package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class ApiClient {

    private static final String API_URL = "https://40servidoresmc.es/api2.php?clave=";

    private final CSPlugin plugin;
    private final Gson gson;
    private final HttpRequester httpRequester;
    private final String apiUrl;

    public ApiClient(CSPlugin plugin, Gson gson) {
        this(plugin, gson, new HttpRequester(), API_URL);
    }

    ApiClient(CSPlugin plugin, Gson gson, HttpRequester httpRequester, String apiUrl) {
        this.plugin = plugin;
        this.gson = gson;
        this.httpRequester = httpRequester;
        this.apiUrl = apiUrl;
    }

    public String apiKey() {
        return plugin.getCSConfiguration().getString("clave");
    }

    public int timeOut() {
        return plugin.getCSConfiguration().getInt("readTimeOut", HttpConfig.DEFAULT_TIMEOUT);
    }

    public CompletableFuture<VoteResponse> validateVote(String player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchData("&nombre=" + urlEncode(player), "GET", VoteResponse.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute API call: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<ServerStats> fetchServerStats() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchData("&estadisticas=1", "GET", ServerStats.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute API call: " + e.getMessage(), e);
            }
        });
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
        String body = httpRequester.request(url, method, "API 40ServidoresMC " + method, httpConfig(), httpLogger());
        return gson.fromJson(body, type);
    }

    private HttpConfig httpConfig() {
        return HttpConfig.from(plugin.getCSConfiguration());
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
        };
    }

    private String urlEncode(String text) throws IOException {
        return URLEncoder.encode(text == null ? "" : text, "UTF-8");
    }
}

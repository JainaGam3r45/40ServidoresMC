package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
import com.cadiducho.cservidoresmc.http.UserAgent;
import com.cadiducho.cservidoresmc.model.updater.GitHubReleaseInfo;
import com.cadiducho.cservidoresmc.model.updater.UpdateCheckResult;
import com.cadiducho.cservidoresmc.model.updater.UpdateCheckStatus;
import com.cadiducho.cservidoresmc.model.updater.UpdateNoticeFormatter;
import com.cadiducho.cservidoresmc.model.updater.UpdaterInfo;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Clase para comprobar las actualizaciones a través de Github
 * @author Cadiducho
 */
public class Updater {

    private static final String RELEASE_URL = "https://api.github.com/repos/JainaGam3r45/40ServidoresMC/releases/latest";
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/JainaGam3r45/40ServidoresMC/development/etc/v3.json";
    private static final String RELEASE_TAG_URL = "https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v%s";
    private static final String ERROR = "Error obteniendo la versión.";

    private final CSPlugin plugin;
    private final String installedVersion;
    private final String minecraftVersion;
    private final HttpRequester httpRequester;
    private final Gson gson;
    private final String releaseUrl;
    private final String updateUrl;
    private final Executor executor;
    private final AtomicReference<UpdateCheckResult> cachedResult;
    private final AtomicReference<CompletableFuture<UpdateCheckResult>> inFlight = new AtomicReference<>();

    public Updater(CSPlugin instance, String vInstalada, String vMinecraft) {
        this(instance, vInstalada, vMinecraft, new HttpRequester(), new Gson(), RELEASE_URL, UPDATE_URL, instance.getAsyncExecutor());
    }

    Updater(CSPlugin instance, String vInstalada, String vMinecraft, HttpRequester httpRequester, Gson gson, String releaseUrl, String updateUrl) {
        this(instance, vInstalada, vMinecraft, httpRequester, gson, releaseUrl, updateUrl, ForkJoinPool.commonPool());
    }

    Updater(CSPlugin instance, String vInstalada, String vMinecraft, HttpRequester httpRequester, Gson gson, String releaseUrl, String updateUrl, Executor executor) {
        this.plugin = instance;
        this.installedVersion = vInstalada;
        this.minecraftVersion = vMinecraft;
        this.httpRequester = httpRequester;
        this.gson = gson;
        this.releaseUrl = releaseUrl;
        this.updateUrl = updateUrl;
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
        this.cachedResult = new AtomicReference<>(UpdateCheckResult.pending(vInstalada));
    }

    /**
     * Comprobar si hay nueva versión
     * @param sender Jugador al que se avisará
     */
    public void checkearVersion(CSCommandSender sender) {
        checkearVersion(sender, false);
    }

    /**
     * Comprobar si hay nueva versión
     * @param sender Jugador al que se avisará
     * @param confirmation Si es true, se avisará si no hay nueva versión
     */
    public void checkearVersion(CSCommandSender sender, boolean confirmation) {
        boolean automaticConsoleCheck = sender == null;
        CSCommandSender finalSender = automaticConsoleCheck ? new CSConsoleSender(plugin) : sender;

        plugin.debugLog("Buscando nueva versión para Minecraft " + minecraftVersion + "...");
        checkForUpdates().whenComplete((result, error) -> {
            if (!plugin.isActive()) {
                return;
            }

            UpdateCheckResult finalResult = error == null
                    ? result
                    : UpdateCheckResult.error(installedVersion, error.getMessage());

            if (finalResult == null) {
                finalResult = UpdateCheckResult.error(installedVersion, ERROR);
            }

            boolean shouldNotifyConsole = !automaticConsoleCheck || notifyConsole();
            if (!shouldNotifyConsole) {
                return;
            }

            if (finalResult.isUpdateAvailable()) {
                sendLines(finalSender, noticeFormatter().updateAvailable(finalResult));
                return;
            }

            if (!automaticConsoleCheck && confirmation) {
                sendManualResult(finalSender, finalResult);
            }
        });
    }

    public synchronized CompletableFuture<UpdateCheckResult> checkForUpdates() {
        CompletableFuture<UpdateCheckResult> current = inFlight.get();
        if (current != null && !current.isDone()) {
            return current;
        }

        CompletableFuture<UpdateCheckResult> check = queryUpdateStatus().thenApply(result -> {
            cachedResult.set(result);
            return result;
        });
        inFlight.set(check);
        check.whenComplete((result, error) -> {
            if (error != null) {
                cachedResult.set(UpdateCheckResult.error(installedVersion, error.getMessage()));
            }
            synchronized (Updater.this) {
                if (inFlight.get() == check) {
                    inFlight.set(null);
                }
            }
        });
        return check;
    }

    public UpdateCheckResult getCachedResult() {
        return cachedResult.get();
    }

    public CompletableFuture<UpdateCheckResult> getCurrentCheck() {
        CompletableFuture<UpdateCheckResult> current = inFlight.get();
        return current != null && !current.isDone() ? current : null;
    }

    public boolean sendUpdateNoticeIfAvailable(CSCommandSender sender, UpdateCheckResult result) {
        if (sender == null || result == null || !result.isUpdateAvailable()) {
            return false;
        }
        sendLines(sender, noticeFormatter().updateAvailable(result));
        return true;
    }

    public boolean notifyAdminsOnJoin() {
        return plugin.getCSConfiguration().getBoolean("updater.notifyAdminsOnJoin", true);
    }

    public int joinNotificationDelaySeconds() {
        int seconds = plugin.getCSConfiguration().getInt("updater.joinNotificationDelaySeconds", 3);
        if (seconds < 0) {
            return 0;
        }
        return Math.min(seconds, 30);
    }

    public UpdateNoticeFormatter getNoticeFormatter() {
        return noticeFormatter();
    }

    private UpdateNoticeFormatter noticeFormatter() {
        return new UpdateNoticeFormatter();
    }

    private CompletableFuture<UpdateCheckResult> queryUpdateStatus() {
        return fetchLatestRelease()
                .handle((releaseInfo, error) -> {
                    if (error != null) {
                        plugin.debugLog("No se pudo consultar la última release de GitHub: " + error.getMessage());
                        return null;
                    }
                    return releaseResult(releaseInfo);
                })
                .thenCompose(result -> result != null ? completed(result) : fetchLegacyResult())
                .exceptionally(error -> {
                    if (plugin.isActive()) {
                        plugin.log(ERROR + " El servidor continuará iniciando con normalidad.");
                        plugin.debugLog("Causa del updater: " + error.getMessage());
                    }
                    return UpdateCheckResult.error(installedVersion, error.getMessage());
                });
    }

    private UpdateCheckResult releaseResult(GitHubReleaseInfo releaseInfo) {
        if (releaseInfo == null) {
            return null;
        }

        String availableVersion = releaseInfo.getVersion();
        if (isNewerVersion(availableVersion, installedVersion)) {
            String link = releaseInfo.getHtmlUrl() == null || releaseInfo.getHtmlUrl().trim().isEmpty()
                    ? String.format(RELEASE_TAG_URL, releaseInfo.getTagName())
                    : releaseInfo.getHtmlUrl();
            return UpdateCheckResult.updateAvailable(installedVersion, availableVersion, releaseInfo.getDescription(), link);
        }

        if (isValidVersion(availableVersion)) {
            return UpdateCheckResult.upToDate(installedVersion, availableVersion);
        }

        return null;
    }

    private CompletableFuture<UpdateCheckResult> fetchLegacyResult() {
        return fetchUpdate().thenApply(updaterInfo -> {
            if (updaterInfo == null) {
                return UpdateCheckResult.error(installedVersion, "La respuesta del updater está vacía.");
            }

            Optional<Map.Entry<String, String>> recommendedVersion = updaterInfo.getPluginForMinecraft(minecraftVersion);
            if (!recommendedVersion.isPresent()) {
                return UpdateCheckResult.upToDate(installedVersion, installedVersion);
            }

            String updaterVersion = recommendedVersion.get().getKey();
            String updateDescription = recommendedVersion.get().getValue();
            if (isNewerVersion(updaterVersion, installedVersion)) {
                String link = String.format(RELEASE_TAG_URL, updaterVersion);
                return UpdateCheckResult.updateAvailable(installedVersion, updaterVersion, updateDescription, link);
            }

            return UpdateCheckResult.upToDate(installedVersion, updaterVersion);
        });
    }

    private void sendManualResult(CSCommandSender sender, UpdateCheckResult result) {
        UpdateNoticeFormatter formatter = noticeFormatter();
        if (result.getStatus() == UpdateCheckStatus.ERROR) {
            sendLines(sender, formatter.error(result));
            return;
        }
        sendLines(sender, formatter.upToDate(result));
    }

    private boolean notifyConsole() {
        return plugin.getCSConfiguration().getBoolean("updater.notifyConsole", true);
    }

    private CompletableFuture<GitHubReleaseInfo> fetchLatestRelease() {
        return submitRequest(() -> {
            try {
                String body = httpRequester.get(new URL(releaseUrl), "Última release 40ServidoresMC", httpConfig(), httpLogger());
                return gson.fromJson(body, GitHubReleaseInfo.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute GitHub release fetch: " + e.getMessage(), e);
            }
        });
    }

    private CompletableFuture<UpdaterInfo> fetchUpdate() {
        return submitRequest(() -> {
            try {
                String body = httpRequester.get(new URL(updateUrl), "Updater 40ServidoresMC", httpConfig(), httpLogger());
                return gson.fromJson(body, UpdaterInfo.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute Updater fetch: " + e.getMessage(), e);
            }
        });
    }

    static boolean isNewerVersion(String availableVersion, String installedVersion) {
        if (!isValidVersion(availableVersion) || !isValidVersion(installedVersion)) {
            return false;
        }

        String[] availableParts = normalizeVersion(availableVersion).split("\\.");
        String[] installedParts = normalizeVersion(installedVersion).split("\\.");
        int maxLength = Math.max(availableParts.length, installedParts.length);

        for (int i = 0; i < maxLength; i++) {
            int availablePart = i < availableParts.length ? Integer.parseInt(availableParts[i]) : 0;
            int installedPart = i < installedParts.length ? Integer.parseInt(installedParts[i]) : 0;

            if (availablePart > installedPart) {
                return true;
            }

            if (availablePart < installedPart) {
                return false;
            }
        }

        return false;
    }

    static boolean isValidVersion(String version) {
        return version != null && normalizeVersion(version).matches("\\d+(\\.\\d+)*");
    }

    private static String normalizeVersion(String version) {
        String cleanVersion = version.trim();
        return cleanVersion.startsWith("v") || cleanVersion.startsWith("V") ? cleanVersion.substring(1) : cleanVersion;
    }

    private HttpConfig httpConfig() {
        return HttpConfig.from(plugin.getCSConfiguration(), userAgent());
    }

    private String userAgent() {
        return UserAgent.build(installedVersion, plugin.getServerPlatform(), minecraftVersion);
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

    private void sendLines(CSCommandSender sender, List<String> lines) {
        plugin.runSenderIfActive(sender, resolved -> {
            for (String line : lines) {
                plugin.sendFormattedMessage(resolved, line);
            }
        });
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

    private CompletableFuture<UpdateCheckResult> completed(UpdateCheckResult result) {
        return CompletableFuture.completedFuture(result);
    }
}

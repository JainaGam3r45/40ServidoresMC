package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
import com.cadiducho.cservidoresmc.model.updater.GitHubReleaseInfo;
import com.cadiducho.cservidoresmc.model.updater.UpdaterInfo;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Clase para comprobar las actualizaciones a través de Github
 * @author Cadiducho
 */
public class Updater {

    private static final String RELEASE_URL = "https://api.github.com/repos/JainaGam3r45/40ServidoresMC/releases/latest";
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/JainaGam3r45/40ServidoresMC/development/etc/v3.json";
    private static final String RELEASE_TAG_URL = "https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v%s";

    private static String versionInstalada, versionMinecraft;
    private static CSPlugin plugin;
    private final HttpRequester httpRequester;
    private final Gson gson;
    private final String releaseUrl;
    private final String updateUrl;

    public Updater(CSPlugin instance, String vInstalada, String vMinecraft) {
        this(instance, vInstalada, vMinecraft, new HttpRequester(), new Gson(), RELEASE_URL, UPDATE_URL);
    }

    Updater(CSPlugin instance, String vInstalada, String vMinecraft, HttpRequester httpRequester, Gson gson, String releaseUrl, String updateUrl) {
        plugin = instance;
        versionInstalada = vInstalada;
        versionMinecraft = vMinecraft;
        this.httpRequester = httpRequester;
        this.gson = gson;
        this.releaseUrl = releaseUrl;
        this.updateUrl = updateUrl;
    }
    
    private final String ERROR = "Error obteniendo la versión.";
    private final String UPDATED = "Versión actualizada";
    private final String NEW_VERSION = "Versión desactualizada. Nueva versión: %s. Changelog: %s. Descarga en: %s";

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
        if (sender == null) {
            // Se hace al iniciar el plugin
            sender = new CSConsoleSender(plugin);
        }
        plugin.debugLog("Buscando nueva versión para Minecraft " + versionMinecraft + "...");

        final CSCommandSender finalSender = sender;
        fetchLatestRelease().thenAccept((GitHubReleaseInfo releaseInfo) -> {
            if (releaseInfo == null) {
                checkLegacyUpdate(finalSender, confirmation);
                return;
            }

            if (isNewerVersion(releaseInfo.getVersion(), versionInstalada)) {
                String link = releaseInfo.getHtmlUrl() == null ? String.format(RELEASE_TAG_URL, releaseInfo.getTagName()) : releaseInfo.getHtmlUrl();
                String format = String.format(NEW_VERSION, releaseInfo.getVersion(), releaseInfo.getDescription(), link);
                finalSender.sendMessageWithTag(format);
                return;
            }

            if (isValidVersion(releaseInfo.getVersion())) {
                finalSender.sendMessageWithTag(UPDATED);
                return;
            }

            checkLegacyUpdate(finalSender, confirmation);
        }).exceptionally(e -> {
            plugin.debugLog("No se pudo consultar la última release de GitHub: " + e.getMessage());
            checkLegacyUpdate(finalSender, confirmation);
            return null;
        });
    }

    private void checkLegacyUpdate(CSCommandSender sender, boolean confirmation) {
        fetchUpdate().thenAccept((UpdaterInfo updaterInfo) -> {
            Optional<Map.Entry<String, String>> recommendedVersion = updaterInfo.getPluginForMinecraft(versionMinecraft);
            if (recommendedVersion.isPresent()) {
                String updaterVersion = recommendedVersion.get().getKey();
                String updateDescription = recommendedVersion.get().getValue();

                if (isNewerVersion(updaterVersion, versionInstalada)) {
                    String link = String.format(RELEASE_TAG_URL, updaterVersion);
                    String format = String.format(NEW_VERSION, updaterVersion, updateDescription, link);
                    sender.sendMessageWithTag(format);
                } else {
                    sender.sendMessageWithTag(UPDATED);
                }
            } else if (confirmation) {
                sender.sendMessageWithTag("No hay versión más moderna recomendada para tu versión de Minecraft.");
            }
        }).exceptionally(e -> {
            plugin.log(ERROR + " El servidor continuará iniciando con normalidad.");
            plugin.debugLog("Causa del updater: " + e.getMessage());
            return null;
        });
    }

    private CompletableFuture<GitHubReleaseInfo> fetchLatestRelease() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = httpRequester.get(new URL(releaseUrl), "Última release 40ServidoresMC", httpConfig(), httpLogger());
                return gson.fromJson(body, GitHubReleaseInfo.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute GitHub release fetch: " + e.getMessage(), e);
            }
        });
    }

    private CompletableFuture<UpdaterInfo> fetchUpdate() {
        return CompletableFuture.supplyAsync(() -> {
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

}

package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
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

    private static final String UPDATE_URL = "https://raw.githubusercontent.com/Cadiducho/40ServidoresMC/development/etc/v3.json";

    private static String versionInstalada, versionMinecraft;
    private static CSPlugin plugin;
    private final HttpRequester httpRequester;
    private final Gson gson;
    private final String updateUrl;

    public Updater(CSPlugin instance, String vInstalada, String vMinecraft) {
        this(instance, vInstalada, vMinecraft, new HttpRequester(), new Gson(), UPDATE_URL);
    }

    Updater(CSPlugin instance, String vInstalada, String vMinecraft, HttpRequester httpRequester, Gson gson, String updateUrl) {
        plugin = instance;
        versionInstalada = vInstalada;
        versionMinecraft = vMinecraft;
        this.httpRequester = httpRequester;
        this.gson = gson;
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
        fetchUpdate().thenAccept((UpdaterInfo updaterInfo) -> {
            Optional<Map.Entry<String, String>> recommendedVersion = updaterInfo.getPluginForMinecraft(versionMinecraft);
            if (recommendedVersion.isPresent()) {
                String updaterVersion = recommendedVersion.get().getKey();
                String updateDescription = recommendedVersion.get().getValue();

                // Si existe versión recomendada para esa versión de minecraft, pero no es la que está instalada, avisar
                if (!updaterVersion.equals(versionInstalada)) {
                    String link = String.format("https://github.com/Cadiducho/40ServidoresMC/releases/tag/v%s", updaterVersion);
                    String format = String.format(NEW_VERSION, updaterVersion, updateDescription, link);
                    finalSender.sendMessageWithTag(format);
                } else {
                    finalSender.sendMessageWithTag(UPDATED);
                }
            } else if (confirmation) {
                finalSender.sendMessageWithTag("No hay versión más moderna recomendada para tu versión de Minecraft.");
            }
        }).exceptionally(e -> {
            plugin.log(ERROR + " El servidor continuará iniciando con normalidad.");
            plugin.debugLog("Causa del updater: " + e.getMessage());
            return null;
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

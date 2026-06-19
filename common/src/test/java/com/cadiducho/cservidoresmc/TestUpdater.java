package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.model.updater.GitHubReleaseInfo;
import com.cadiducho.cservidoresmc.model.updater.UpdateCheckResult;
import com.cadiducho.cservidoresmc.model.updater.UpdateCheckStatus;
import com.cadiducho.cservidoresmc.model.updater.UpdateNoticeFormatter;
import com.cadiducho.cservidoresmc.model.updater.UpdaterInfo;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.http.HttpConfig;
import com.cadiducho.cservidoresmc.http.HttpLogger;
import com.cadiducho.cservidoresmc.http.HttpRequester;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestUpdater {

    @Test
    void parseUpdateRequest() {
        String file = "{\n" +
                "    \"pluginVersions\": {\n" +
                "        \"3.0\": \"Reescritura del sistema para hacerlo compatible con Spigot, Sponge y BungeeCord\"\n" +
                "    },\n" +
                "    \"minecraftVersions\": {\n" +
                "        \"1.8.8\": \"3.0\",\n" +
                "        \"1.12.2\": \"3.0\",\n" +
                "        \"1.13.2\": \"3.0\",\n" +
                "        \"1.14.4\": \"3.0\",\n" +
                "        \"1.15.2\": \"3.0\",\n" +
                "        \"1.16.2\": \"3.0\",\n" +
                "        \"1.16.4\": \"3.0\",\n" +
                "        \"1.16.5\": \"3.0\"\n" +
                "    }\n" +
                "}";
        Gson gson = new Gson();
        UpdaterInfo updaterInfo = gson.fromJson(file, UpdaterInfo.class);
        assertNotNull(updaterInfo);
        assertEquals("3.0", updaterInfo.getMinecraftVersions().get("1.16.5"));
        Optional<Map.Entry<String, String>> versionEntry = updaterInfo.getPluginForMinecraft("1.16.5");
        assertTrue(versionEntry.isPresent());

        String updaterVersion = versionEntry.get().getKey();
        String updateDescription = versionEntry.get().getValue();
        assertEquals("3.0", updaterVersion);
        assertEquals("Reescritura del sistema para hacerlo compatible con Spigot, Sponge y BungeeCord", updateDescription);
    }

    @Test
    void parseGitHubReleaseRequest() {
        String file = "{\n" +
                "    \"tag_name\": \"v3.1.0\",\n" +
                "    \"html_url\": \"https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v3.1.0\",\n" +
                "    \"name\": \"v3.1.0\",\n" +
                "    \"body\": \"## Cambios incluidos\\n\\n* Cliente HTTP robusto\"\n" +
                "}";
        Gson gson = new Gson();
        GitHubReleaseInfo releaseInfo = gson.fromJson(file, GitHubReleaseInfo.class);
        assertNotNull(releaseInfo);
        assertEquals("3.1.0", releaseInfo.getVersion());
        assertEquals("https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v3.1.0", releaseInfo.getHtmlUrl());
        assertEquals("Cambios incluidos", releaseInfo.getDescription());
    }

    @Test
    void compareSemanticVersions() {
        assertTrue(Updater.isNewerVersion("v3.1.1", "3.1.0"));
        assertTrue(Updater.isNewerVersion("3.2.0", "3.1.9"));
        assertTrue(Updater.isNewerVersion("4.0.0", "3.9.9"));
        assertFalse(Updater.isNewerVersion("v3.1.0", "3.1.0"));
        assertFalse(Updater.isNewerVersion("3.0.9", "3.1.0"));
        assertFalse(Updater.isNewerVersion("release-3.2.0", "3.1.0"));
    }

    @Test
    void formatsUpdateNoticeAndRemainsReadableWithoutColor() {
        UpdateNoticeFormatter formatter = new UpdateNoticeFormatter();
        UpdateCheckResult result = UpdateCheckResult.updateAvailable(
                "3.1.0",
                "3.2.0",
                "**Mejoras** del updater",
                "https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v3.2.0"
        );

        List<String> lines = formatter.updateAvailable(result);

        assertTrue(lines.get(1).contains("Nueva actualización disponible"));
        assertTrue(lines.stream().anyMatch(line -> line.contains("&c3.1.0")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("&a3.2.0")));
        String plain = UpdateNoticeFormatter.stripColor(String.join("\n", lines));
        assertFalse(plain.contains("&"));
        assertTrue(plain.contains("40ServidoresMC"));
        assertTrue(plain.contains("Nueva actualización disponible"));
    }

    @Test
    void sanitizesRemoteReleaseText() {
        String dirty = "## &cCambios §ksecretos\n\n* [Release](https://example.com) con `markdown` y texto extra";

        String clean = UpdateNoticeFormatter.sanitizeSummary(dirty);

        assertFalse(clean.contains("&c"));
        assertFalse(clean.contains("§"));
        assertFalse(clean.contains("https://"));
        assertFalse(clean.contains("`"));
        assertTrue(clean.contains("Cambios"));
    }

    @Test
    void manualCheckSendsFormattedUpdateAndCachesStatus() {
        CountingRequester requester = new CountingRequester();
        requester.addResponse("{\"tag_name\":\"v3.2.0\",\"html_url\":\"https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v3.2.0\",\"body\":\"## Fixes\"}");
        TestPlugin plugin = new TestPlugin();
        Updater updater = updater(plugin, requester, "3.1.0");
        CaptureSender sender = new CaptureSender("Admin", true);

        updater.checkearVersion(sender, true);

        assertEquals(UpdateCheckStatus.UPDATE_AVAILABLE, updater.getCachedResult().getStatus());
        assertEquals(1, requester.requests());
        assertTrue(sender.messages.stream().anyMatch(line -> line.contains("Nueva actualización disponible")));
        assertTrue(sender.messages.stream().anyMatch(line -> line.contains("&a3.2.0")));
    }

    @Test
    void manualCheckReportsUpToDate() {
        CountingRequester requester = new CountingRequester();
        requester.addResponse("{\"tag_name\":\"v3.2.0\",\"html_url\":\"https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v3.2.0\",\"body\":\"## Fixes\"}");
        TestPlugin plugin = new TestPlugin();
        Updater updater = updater(plugin, requester, "3.2.0");
        CaptureSender sender = new CaptureSender("Admin", true);

        updater.checkearVersion(sender, true);

        assertEquals(UpdateCheckStatus.UP_TO_DATE, updater.getCachedResult().getStatus());
        assertTrue(sender.messages.stream().anyMatch(line -> line.contains("está actualizado")));
    }

    @Test
    void automaticCheckDoesNotPrintUpToDateMessage() {
        CountingRequester requester = new CountingRequester();
        requester.addResponse("{\"tag_name\":\"v3.2.0\",\"html_url\":\"https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v3.2.0\",\"body\":\"## Fixes\"}");
        TestPlugin plugin = new TestPlugin();
        Updater updater = updater(plugin, requester, "3.2.0");

        updater.checkearVersion(null);

        assertEquals(UpdateCheckStatus.UP_TO_DATE, updater.getCachedResult().getStatus());
        assertTrue(plugin.formattedMessages.isEmpty());
    }

    @Test
    void fallbackLegacyUpdateIsUsedWhenReleaseIsInvalid() {
        CountingRequester requester = new CountingRequester();
        requester.addResponse("{\"tag_name\":\"snapshot\",\"html_url\":\"\"}");
        requester.addResponse("{\"pluginVersions\":{\"3.2.0\":\"Actualización estable\"},\"minecraftVersions\":{\"1.8.8\":\"3.2.0\"}}");
        TestPlugin plugin = new TestPlugin();
        Updater updater = updater(plugin, requester, "3.1.0");
        CaptureSender sender = new CaptureSender("Admin", true);

        updater.checkearVersion(sender, true);

        assertEquals(UpdateCheckStatus.UPDATE_AVAILABLE, updater.getCachedResult().getStatus());
        assertEquals(2, requester.requests());
        assertTrue(sender.messages.stream().anyMatch(line -> line.contains("Actualización estable")));
    }

    @Test
    void notificationSessionPreventsDuplicatesUntilNotificationIsCleared() {
        UpdateNotificationSession session = new UpdateNotificationSession();

        assertTrue(session.begin("uuid"));
        assertFalse(session.begin("uuid"));
        session.clearPending("uuid");
        assertTrue(session.begin("uuid"));
        session.markNotified("uuid");
        assertFalse(session.begin("uuid"));
    }

    private Updater updater(TestPlugin plugin, CountingRequester requester, String installedVersion) {
        Executor directExecutor = Runnable::run;
        return new Updater(plugin, installedVersion, "1.8.8", requester, new Gson(),
                "http://localhost/release", "http://localhost/legacy", directExecutor);
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

    private static class CaptureSender implements CSCommandSender {

        private final String name;
        private final boolean permission;
        private final List<String> messages = new ArrayList<>();

        private CaptureSender(String name, boolean permission) {
            this.name = name;
            this.permission = permission;
        }

        @Override
        public String TAG() {
            return "&8[&b40ServidoresMC&8]";
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
        public boolean hasPermission(String permission) {
            return this.permission;
        }
    }

    private static class TestPlugin implements CSPlugin {

        private final TestConfiguration configuration = new TestConfiguration(this);
        private final List<String> formattedMessages = new ArrayList<>();
        private boolean active = true;

        @Override
        public void log(String text) {
            formattedMessages.add(text);
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
            return new PluginMetrics();
        }

        @Override
        public String getPluginVersion() {
            return "test";
        }

        @Override
        public void dispatchCommand(String command) {
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void runSync(Runnable task) {
            task.run();
        }

        @Override
        public void sendFormattedMessage(CSCommandSender sender, String message) {
            formattedMessages.add(message);
            sender.sendMessage(message);
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
            strings.put("api.key", "key");
            booleans.put("debug", false);
            booleans.put("updater.notifyConsole", true);
            booleans.put("updater.notifyAdminsOnJoin", true);
            ints.put("api.retries", 0);
            ints.put("api.retryBackoffMillis", 1);
            ints.put("api.readTimeout", 1000);
            ints.put("api.connectTimeout", 1000);
            ints.put("updater.joinNotificationDelaySeconds", 3);
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

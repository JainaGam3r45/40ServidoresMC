package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.PluginMetrics;
import com.cadiducho.cservidoresmc.RewardService;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestConfigMigrator {

    @TempDir
    Path tempDir;

    @Test
    void migratesLegacySpanishKeysToEnglishStructure() throws Exception {
        File config = writeConfig(
                "# Comentario personalizado\n" +
                        "debug: true\n" +
                        "clave: personalizada\n" +
                        "broadcast:\n" +
                        "  activado: false\n" +
                        "  mensajeBroadcast: '&bMensaje propio'\n" +
                        "mensaje: '&dPremio propio'\n" +
                        "alreadyRewardedMessage: '&aYa reclamaste. Vuelve en %time%.'\n" +
                        "comandosCustom:\n" +
                        "  - give %player% stone 1\n" +
                        "readTimeOut: 7000\n" +
                        "connectTimeOut: 8000\n" +
                        "httpRetries: 4\n" +
                        "httpRetryBackoff: 600\n" +
                        "tag: '&8[&bCustom&8]'\n" +
                        "configVer: 7\n" +
                        "customExtra: keep-me\n"
        );
        TestPlugin plugin = new TestPlugin(tempDir.toFile());

        new ConfigMigrator(plugin, config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("# 40ServidoresMC configuration"));
        assertFalse(migrated.contains("# Comentario personalizado"));
        assertTrue(migrated.contains("debug: true"));
        assertTrue(migrated.contains("configVersion: 8"));
        assertTrue(migrated.contains("api:\n"));
        assertTrue(migrated.contains("  key: \"personalizada\""));
        assertTrue(migrated.contains("  readTimeout: 7000"));
        assertTrue(migrated.contains("  connectTimeout: 8000"));
        assertTrue(migrated.contains("  retries: 4"));
        assertTrue(migrated.contains("  retryBackoffMillis: 600"));
        assertTrue(migrated.contains("messages:\n"));
        assertTrue(migrated.contains("  prefix: \"&8[&bCustom&8]\""));
        assertTrue(migrated.contains("  voteClaim: \"&dPremio propio\""));
        assertTrue(migrated.contains("  alreadyRewarded: \"&aYa reclamaste. Vuelve en %time%.\""));
        assertTrue(migrated.contains("  commands:"));
        assertTrue(migrated.contains("    noPermission:"));
        assertTrue(migrated.contains("  metrics:"));
        assertTrue(migrated.contains("    lines:"));
        assertFalse(migrated.contains("invalidApiKey:"));
        assertFalse(migrated.contains("    updateAvailable:"));
        assertTrue(migrated.contains("updater:\n"));
        assertTrue(migrated.contains("  notifyConsole: true"));
        assertTrue(migrated.contains("  notifyAdminsOnJoin: true"));
        assertTrue(migrated.contains("  joinNotificationDelaySeconds: 3"));
        assertTrue(migrated.contains("broadcast:\n"));
        assertTrue(migrated.contains("  enabled: false"));
        assertTrue(migrated.contains("  message: \"&bMensaje propio\""));
        assertTrue(migrated.contains("rewards:\n"));
        assertTrue(migrated.contains("  commands:\n    - \"give %player% stone 1\""));
        assertFalse(migrated.contains("customExtra:"));
        assertFalse(migrated.contains("clave:"));
        assertFalse(migrated.contains("configVer:"));
        assertFalse(migrated.contains("comandosCustom:"));
        assertFalse(migrated.contains("mensajeBroadcast:"));
        assertFalse(migrated.contains("\n\n\n"));
        assertTrue(plugin.logs.get(0).contains("config.yml actualizado"));
        assertTrue(plugin.errors.get(0).contains("customExtra"));
        assertEquals(1, backupCount());
    }

    @Test
    void preservesCustomApiKey() throws Exception {
        File config = writeConfig(
                "configVersion: 8\n" +
                        "api:\n" +
                        "  key: \"server-api-key\"\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("  key: \"server-api-key\""));
        assertTrue(migrated.contains("  readTimeout: 5000"));
    }

    @Test
    void preservesCustomCommandList() throws Exception {
        File config = writeConfig(
                "rewards:\n" +
                        "  commands:\n" +
                        "    - eco give %player% 100\n" +
                        "    - lp user %player% parent add voter\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("  commands:\n" +
                "    - \"eco give %player% 100\"\n" +
                "    - \"lp user %player% parent add voter\""));
    }

    @Test
    void rebuildsOldStructureUsingTemplateComments() throws Exception {
        File config = writeConfig(
                "debug: false\n" +
                        "api:\n" +
                        "  key: key\n" +
                        "# Old local comment\n" +
                        "tag: \"custom\"\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("# 40ServidoresMC configuration"));
        assertTrue(migrated.contains("# Retry failed transient requests such as timeouts and 5xx responses."));
        assertFalse(migrated.contains("# Old local comment"));
        assertTrue(migrated.contains("  prefix: \"custom\""));
        assertTrue(migrated.contains("cache:\n"));
        assertTrue(migrated.contains("  # Cache API responses to reduce HTTP requests."));
        assertTrue(migrated.contains("autoReward:\n" +
                "  # Recheck votes shortly after a player uses the vote command.\n" +
                "  enabled: true\n" +
                "  recheckDelaysSeconds:\n" +
                "    - 10\n" +
                "    - 30\n" +
                "    - 60"));
        assertFalse(migrated.contains("\n\n\n"));
    }

    @Test
    void existingNewKeysAreNotOverwrittenByLegacyKeys() throws Exception {
        File config = writeConfig(
                "configVersion: 8\n" +
                        "api:\n" +
                        "  key: \"new-key\"\n" +
                        "clave: old-key\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("  key: \"new-key\""));
        assertFalse(migrated.contains("old-key"));
        assertFalse(migrated.contains("clave:"));
    }

    @Test
    void outdatedConfigVersionIsUpdatedToTemplateDefault() throws Exception {
        File config = writeConfig(
                "configVersion: 3\n" +
                        "debug: false\n" +
                        "api:\n" +
                        "  key: \"server-key\"\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("configVersion: 8"));
        assertFalse(migrated.contains("configVersion: 3"));
        assertTrue(migrated.contains("  key: \"server-key\""));
    }

    @Test
    void legacyConfigVerDoesNotPreserveOldVersionNumber() throws Exception {
        File config = writeConfig(
                "configVer: 3\n" +
                        "debug: false\n" +
                        "clave: server-key\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("configVersion: 8"));
        assertFalse(migrated.contains("configVer:"));
        assertFalse(migrated.contains("configVersion: 3"));
    }

    @Test
    void extraKeysAreOnlyKeptInBackupAndWarned() throws Exception {
        File config = writeConfig(
                "debug: false\n" +
                        "unknown:\n" +
                        "  value: keep-me\n"
        );
        TestPlugin plugin = new TestPlugin(tempDir.toFile());

        new ConfigMigrator(plugin, config).migrate();

        String migrated = normalize(read(config));
        assertFalse(migrated.contains("unknown:"));
        assertFalse(migrated.contains("keep-me"));
        assertTrue(plugin.errors.get(0).contains("unknown"));
        assertEquals(1, backupCount());
    }

    @Test
    void repeatedMigrationDoesNotDuplicateKeys() throws Exception {
        File config = writeConfig(
                "debug: false\n" +
                        "clave: key\n"
        );
        TestPlugin plugin = new TestPlugin(tempDir.toFile());

        new ConfigMigrator(plugin, config).migrate();
        String firstRun = read(config);
        new ConfigMigrator(plugin, config).migrate();
        String secondRun = read(config);

        assertEquals(firstRun, secondRun);
        assertEquals(1, backupCount());
    }

    @Test
    void pathTrackerBuildsNestedPathsFromTwoSpaceIndentation() {
        YamlPathTracker tracker = new YamlPathTracker();

        assertEquals("api", tracker.pathFor(0, "api"));
        tracker.put(0, "api");
        assertEquals("api.key", tracker.pathFor(2, "key"));
        tracker.put(0, "messages");
        assertEquals("messages.prefix", tracker.pathFor(2, "prefix"));
    }

    @Test
    void rendererFormatsListsWithTwoSpaces() throws Exception {
        YamlConfiguration user = new YamlConfiguration();
        user.loadFromString("rewards:\n  commands:\n    - say hi\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("rewards:\n  commands:\n    - default\n");
        ConfigTemplate template = ConfigTemplate.parse("rewards:\n  commands:\n    - default\n");

        String rendered = normalize(new ConfigTemplateRenderer(template, new ConfigValueResolver(user, defaults)).render());

        assertEquals("rewards:\n  commands:\n    - \"say hi\"\n", rendered);
    }

    @Test
    void invalidYamlIsNotModified() throws Exception {
        File config = writeConfig(
                "debug: false\n" +
                        "placeholderapi:\n" +
                        "  fallbacks: [broken\n"
        );
        String original = read(config);
        TestPlugin plugin = new TestPlugin(tempDir.toFile());

        new ConfigMigrator(plugin, config).migrate();

        assertEquals(original, read(config));
        assertEquals(0, backupCount());
        assertTrue(plugin.errors.get(0).contains("No se modificó el archivo"));
    }

    private File writeConfig(String text) throws Exception {
        File config = tempDir.resolve("config.yml").toFile();
        Files.write(config.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return config;
    }

    private String read(File config) throws Exception {
        return new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8);
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n");
    }

    private int backupCount() {
        File[] backups = tempDir.toFile().listFiles((dir, name) -> name.startsWith("config.yml.backup-"));
        return backups == null ? 0 : backups.length;
    }

    private static class TestPlugin implements CSPlugin {

        private final File dataFolder;
        private final List<String> logs = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        private TestPlugin(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        @Override
        public void log(String text) {
            logs.add(text);
        }

        @Override
        public void logError(String text) {
            errors.add(text);
        }

        @Override
        public void registerCommands() {
        }

        @Override
        public CSConfiguration getCSConfiguration() {
            return null;
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
            return dataFolder;
        }

        @Override
        public Updater getUpdater() {
            return null;
        }

        @Override
        public PluginMetrics getPluginMetrics() {
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
        public List<CSCommandSender> getOnlinePlayers() {
            return new ArrayList<>();
        }

        @Override
        public void broadcastMessage(String message) {
        }
    }
}

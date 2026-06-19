package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.PluginMetrics;
import com.cadiducho.cservidoresmc.RewardService;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
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
                        "  - give {0} stone 1\n" +
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
        assertTrue(migrated.contains("# Comentario personalizado"));
        assertTrue(migrated.contains("debug: true"));
        assertTrue(migrated.contains("customExtra: keep-me"));
        assertTrue(migrated.contains("configVersion: 7"));
        assertTrue(migrated.contains("api:\n"));
        assertTrue(migrated.contains("  key: personalizada"));
        assertTrue(migrated.contains("  readTimeout: 7000"));
        assertTrue(migrated.contains("  connectTimeout: 8000"));
        assertTrue(migrated.contains("  retries: 4"));
        assertTrue(migrated.contains("  retryBackoffMillis: 600"));
        assertTrue(migrated.contains("messages:\n"));
        assertTrue(migrated.contains("  prefix: '&8[&bCustom&8]'"));
        assertTrue(migrated.contains("  voteClaim: '&dPremio propio'"));
        assertTrue(migrated.contains("  alreadyRewarded: '&aYa reclamaste. Vuelve en %time%.'"));
        assertTrue(migrated.contains("broadcast:\n"));
        assertTrue(migrated.contains("  # Announce vote rewards to all online players."));
        assertTrue(migrated.contains("  enabled: false"));
        assertTrue(migrated.contains("  message: '&bMensaje propio'"));
        assertTrue(migrated.contains("rewards:\n"));
        assertTrue(migrated.contains("  commands:\n    - \"give {0} stone 1\""));
        assertFalse(migrated.contains("clave:"));
        assertFalse(migrated.contains("configVer:"));
        assertFalse(migrated.contains("comandosCustom:"));
        assertFalse(migrated.contains("mensajeBroadcast:"));
        assertFalse(migrated.contains("\n\n\n"));
        assertTrue(plugin.logs.get(0).contains("config.yml actualizado"));
        assertEquals(1, backupCount());
    }

    @Test
    void addsNestedKeysInsideExistingParent() throws Exception {
        File config = writeConfig(
                "debug: false\n" +
                        "placeholderapi:\n" +
                        "  enabled: false\n" +
                        "  fallbacks:\n" +
                        "    notAvailable: '-'\n" +
                        "    zero: 'cero'\n" +
                        "    false: 'no'\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("placeholderapi:\n  enabled: false\n  formats:"));
        assertTrue(migrated.contains("    unavailable: '-'"));
        assertTrue(migrated.contains("    numberZero: 'cero'"));
        assertTrue(migrated.contains("    booleanTrue: \"true\""));
        assertTrue(migrated.contains("    booleanFalse: 'no'"));
        assertTrue(migrated.contains("    dateTime: \"dd/MM/yyyy HH:mm\""));
        assertFalse(migrated.contains("fallbacks:"));
        assertFalse(migrated.contains("notAvailable:"));
        assertFalse(migrated.contains("\n\n\n"));
    }

    @Test
    void insertsSimpleKeysWithoutBlankLinesBetweenThem() throws Exception {
        File config = writeConfig(
                "debug: false\n" +
                        "api:\n" +
                        "  key: key\n" +
                        "\n" +
                        "streakRewards:\n" +
                        "  3:\n" +
                        "    - \"give %player% diamond 1\"\n" +
                        "\n" +
                        "tag: \"custom\"\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("  readTimeout: 5000"));
        assertTrue(migrated.contains("  connectTimeout: 5000"));
        assertTrue(migrated.contains("  retries: 2"));
        assertTrue(migrated.contains("  retryBackoffMillis: 250"));
        assertTrue(migrated.contains("cache:\n"));
        assertTrue(migrated.contains("  # Cache API responses to reduce HTTP requests."));
        assertTrue(migrated.contains("  enabled: true"));
        assertTrue(migrated.contains("  serverStatsTtlSeconds: 60"));
        assertTrue(migrated.contains("  voteCheckNegativeTtlSeconds: 5"));
        assertTrue(migrated.contains("autoReward:\n" +
                "  # Recheck votes shortly after a player uses the vote command.\n" +
                "  enabled: true\n" +
                "  recheckDelaysSeconds:\n" +
                "    - 10\n" +
                "    - 30\n" +
                "    - 60"));
        assertFalse(migrated.contains("readTimeout: 5000\n\n  connectTimeout: 5000"));
        assertFalse(migrated.contains("retries: 2\n\n  retryBackoffMillis: 250"));
        assertFalse(migrated.contains("# %40servidoresmc_"));
    }

    @Test
    void existingNewKeysAreNotOverwrittenByLegacyKeys() throws Exception {
        File config = writeConfig(
                "configVersion: 7\n" +
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

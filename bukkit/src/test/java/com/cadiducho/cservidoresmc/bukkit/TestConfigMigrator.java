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
    void addsMissingKeysWithoutChangingUserContent() throws Exception {
        File config = writeConfig(
                "# Comentario personalizado\n" +
                        "debug: true\n" +
                        "clave: personalizada\n" +
                        "broadcast:\n" +
                        "    activado: false\n" +
                        "    mensajeBroadcast: '&bMensaje propio'\n" +
                        "mensaje: '&dPremio propio'\n" +
                        "comandosCustom:\n" +
                        "- give {0} stone 1\n" +
                        "customExtra: keep-me\n"
        );
        TestPlugin plugin = new TestPlugin(tempDir.toFile());

        new ConfigMigrator(plugin, config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("# Comentario personalizado"));
        assertTrue(migrated.contains("debug: true"));
        assertTrue(migrated.contains("clave: personalizada"));
        assertTrue(migrated.contains("customExtra: keep-me"));
        assertTrue(migrated.contains("cache:"));
        assertTrue(migrated.contains("placeholderapi:"));
        assertTrue(migrated.contains("autoReward:"));
        assertTrue(migrated.contains("voteReminder:"));
        assertTrue(migrated.contains("alreadyRewardedMessage:"));
        assertTrue(migrated.contains("configVer: 7"));
        assertFalse(migrated.contains("\n\n\n"));
        assertTrue(plugin.logs.get(0).contains("config.yml actualizado"));
        assertEquals(1, backupCount());
    }

    @Test
    void addsNestedKeysInsideExistingParent() throws Exception {
        File config = writeConfig(
                "debug: false\n" +
                        "placeholderapi:\n" +
                        "    enabled: false\n" +
                        "    fallbacks:\n" +
                        "        notAvailable: '-'\n"
        );

        new ConfigMigrator(new TestPlugin(tempDir.toFile()), config).migrate();

        String migrated = normalize(read(config));
        assertTrue(migrated.contains("    fallbacks:\n" +
                "        notAvailable: '-'\n" +
                "\n" +
                "        zero: \"0\""));
        assertTrue(migrated.contains("        false: \"false\""));
        assertFalse(migrated.contains("\n\n\n"));
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

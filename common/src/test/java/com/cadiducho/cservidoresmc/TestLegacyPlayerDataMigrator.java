package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLegacyPlayerDataMigrator {

    private static final String UUID = "0f50d3c1-2d53-47d8-9f5a-10153b5f9770";

    @TempDir
    File tempDir;

    @Test
    void combinesLegacyPropertiesIntoPlayerFile() throws Exception {
        writeProperties("rewarded-votes.properties", property("2026-06-18.cadiducho", "1000"));
        writeProperties("vote-reminders.properties", property("cadiducho.lastVoteAt", "900"), property("cadiducho.lastReminderFor", "800"));
        writeProperties("vote-streaks.properties",
                property("uuid." + UUID + ".name", "Cadiducho"),
                property("uuid." + UUID + ".uuid", UUID),
                property("uuid." + UUID + ".lastDay", "2026-06-18"),
                property("uuid." + UUID + ".streak", "3"),
                property("uuid." + UUID + ".bestStreak", "5"),
                property("uuid." + UUID + ".milestones", "1,3"));

        new LegacyPlayerDataMigrator(new TestPlugin(), tempDir).migrate();

        String player = readPlayer();
        assertTrue(player.contains("name: \"Cadiducho\""));
        assertTrue(player.contains("lastVoteAt: 1000"));
        assertTrue(player.contains("lastRewardAt: 1000"));
        assertTrue(player.contains("lastRewardDate: \"2026-06-18\""));
        assertTrue(player.contains("currentStreak: 3"));
        assertTrue(player.contains("bestStreak: 5"));
        assertTrue(player.contains("lastReminderAt: 800"));
        assertTrue(player.contains("rewardedMilestones: [1, 3]"));
        assertFalse(new File(tempDir, "rewarded-votes.properties").exists());
        assertTrue(new File(tempDir, "backup").exists());
    }

    @Test
    void doesNotOverwriteNewerPlayerData() throws Exception {
        File players = new File(tempDir, "players");
        assertTrue(players.mkdirs());
        Files.write(new File(players, UUID + ".yml").toPath(), (
                "name: \"Cadiducho\"\n" +
                        "lastVoteAt: 5000\n" +
                        "lastRewardAt: 5000\n" +
                        "lastRewardDate: \"2026-06-19\"\n" +
                        "currentStreak: 7\n" +
                        "bestStreak: 7\n" +
                        "lastReminderAt: 4000\n" +
                        "lastVoteDay: \"2026-06-19\"\n" +
                        "rewardedMilestones: [7]\n").getBytes(StandardCharsets.UTF_8));
        writeProperties("rewarded-votes.properties", property("2026-06-18.cadiducho", "1000"));

        new LegacyPlayerDataMigrator(new TestPlugin(), tempDir).migrate();

        String player = readPlayer();
        assertTrue(player.contains("lastVoteAt: 5000"));
        assertTrue(player.contains("lastRewardAt: 5000"));
        assertTrue(player.contains("lastRewardDate: \"2026-06-19\""));
        assertTrue(player.contains("bestStreak: 7"));
    }

    private Property property(String key, String value) {
        return new Property(key, value);
    }

    private void writeProperties(String name, Property... properties) throws Exception {
        Properties fileProperties = new Properties();
        for (Property property : properties) {
            fileProperties.setProperty(property.key, property.value);
        }
        try (FileOutputStream outputStream = new FileOutputStream(new File(tempDir, name))) {
            fileProperties.store(outputStream, "test");
        }
    }

    private String readPlayer() throws Exception {
        return new String(Files.readAllBytes(new File(new File(tempDir, "players"), UUID + ".yml").toPath()), StandardCharsets.UTF_8);
    }

    private static class Property {

        private final String key;
        private final String value;

        private Property(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class TestPlugin implements CSPlugin {

        @Override
        public void log(String text) {
        }

        @Override
        public void logError(String text) {
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
            return null;
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
        public String resolvePlayerUniqueId(String player) {
            return "cadiducho".equalsIgnoreCase(player) ? UUID : "";
        }

        @Override
        public void broadcastMessage(String message) {
        }
    }
}

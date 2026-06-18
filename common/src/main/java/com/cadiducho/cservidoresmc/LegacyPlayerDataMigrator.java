package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class LegacyPlayerDataMigrator {

    private static final String REWARDED_VOTES = "rewarded-votes.properties";
    private static final String VOTE_REMINDERS = "vote-reminders.properties";
    private static final String VOTE_STREAKS = "vote-streaks.properties";

    private final CSPlugin plugin;
    private final File dataFolder;
    private final PlayerVoteStore playerVoteStore;

    public LegacyPlayerDataMigrator(CSPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
        this.playerVoteStore = new PlayerVoteStore(dataFolder, plugin);
    }

    public void migrate() {
        if (!hasLegacyFiles()) {
            return;
        }

        Map<String, PlayerVoteStore.LegacyPlayerData> players = new HashMap<>();
        Properties unresolved = new Properties();

        try {
            readRewardedVotes(players);
            readVoteReminders(players);
            readVoteStreaks(players);
            mergePlayers(players, unresolved);
            moveLegacyFiles(unresolved);
            plugin.log("Datos antiguos de jugadores migrados a la carpeta players/.");
        } catch (IOException ex) {
            plugin.logError("No se pudieron migrar los datos antiguos de jugadores. Los .properties se conservaron: " + ex.getMessage());
        }
    }

    private boolean hasLegacyFiles() {
        return file(REWARDED_VOTES).exists() || file(VOTE_REMINDERS).exists() || file(VOTE_STREAKS).exists();
    }

    private void readRewardedVotes(Map<String, PlayerVoteStore.LegacyPlayerData> players) throws IOException {
        Properties properties = load(REWARDED_VOTES);
        for (String key : properties.stringPropertyNames()) {
            RewardKey rewardKey = parseRewardKey(key);
            if (rewardKey.player.isEmpty()) {
                continue;
            }
            legacy(players, rewardKey.player, "").recordReward(parseLong(properties.getProperty(key)), rewardKey.date);
        }
    }

    private void readVoteReminders(Map<String, PlayerVoteStore.LegacyPlayerData> players) throws IOException {
        Properties properties = load(VOTE_REMINDERS);
        for (String key : properties.stringPropertyNames()) {
            if (key.endsWith(".lastVoteAt")) {
                String player = key.substring(0, key.length() - ".lastVoteAt".length());
                legacy(players, player, "").recordVote(parseLong(properties.getProperty(key)));
            } else if (key.endsWith(".lastReminderFor")) {
                String player = key.substring(0, key.length() - ".lastReminderFor".length());
                legacy(players, player, "").recordReminder(parseLong(properties.getProperty(key)));
            }
        }
    }

    private void readVoteStreaks(Map<String, PlayerVoteStore.LegacyPlayerData> players) throws IOException {
        Properties properties = load(VOTE_STREAKS);
        Map<String, String> namesByKey = new HashMap<>();
        Map<String, String> uuidsByKey = new HashMap<>();
        Set<String> baseKeys = new HashSet<>();

        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("names.")) {
                continue;
            }
            String baseKey = baseKey(key);
            if (baseKey.isEmpty()) {
                continue;
            }
            baseKeys.add(baseKey);
            if (key.endsWith(".name")) {
                namesByKey.put(baseKey, properties.getProperty(key));
            } else if (key.endsWith(".uuid")) {
                uuidsByKey.put(baseKey, properties.getProperty(key));
            }
        }

        for (String baseKey : baseKeys) {
            String name = namesByKey.containsKey(baseKey) ? namesByKey.get(baseKey) : nameFromBaseKey(baseKey);
            String uuid = uuidsByKey.containsKey(baseKey) ? uuidsByKey.get(baseKey) : uuidFromBaseKey(baseKey);
            String lastDay = properties.getProperty(baseKey + ".lastDay", "");
            int streak = parseInt(properties.getProperty(baseKey + ".streak"));
            int bestStreak = parseInt(properties.getProperty(baseKey + ".bestStreak"));
            Set<Integer> milestones = parseMilestones(properties.getProperty(baseKey + ".milestones", ""));
            legacy(players, name, uuid).recordStreak(lastDay, streak, bestStreak, milestones);
        }
    }

    private void mergePlayers(Map<String, PlayerVoteStore.LegacyPlayerData> players, Properties unresolved) throws IOException {
        for (PlayerVoteStore.LegacyPlayerData player : players.values()) {
            PlayerVoteStore.MergeResult result = playerVoteStore.mergeLegacy(player);
            if (result == PlayerVoteStore.MergeResult.FAILED) {
                throw new IOException("no se pudo guardar " + player.key());
            }
            if (result == PlayerVoteStore.MergeResult.UNRESOLVED) {
                unresolved.setProperty(player.key(), "No se pudo resolver UUID; datos conservados en backups legacy.");
                plugin.logError("No se pudo resolver UUID para datos antiguos de " + player.key() + ". Se conservaron en backup.");
            }
        }
    }

    private void moveLegacyFiles(Properties unresolved) throws IOException {
        File backupFolder = new File(dataFolder, "backup" + File.separator + "migrated" + File.separator + timestamp());
        Files.createDirectories(backupFolder.toPath());
        moveIfExists(REWARDED_VOTES, backupFolder);
        moveIfExists(VOTE_REMINDERS, backupFolder);
        moveIfExists(VOTE_STREAKS, backupFolder);
        if (!unresolved.isEmpty()) {
            File unresolvedFile = new File(backupFolder, "unresolved-legacy.properties");
            try (FileOutputStream outputStream = new FileOutputStream(unresolvedFile)) {
                unresolved.store(outputStream, "40ServidoresMC unresolved legacy player data");
            }
        }
    }

    private void moveIfExists(String name, File backupFolder) throws IOException {
        File source = file(name);
        if (!source.exists()) {
            return;
        }
        Files.move(source.toPath(), new File(backupFolder, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private Properties load(String name) throws IOException {
        Properties properties = new Properties();
        File source = file(name);
        if (!source.exists()) {
            return properties;
        }
        try (FileInputStream inputStream = new FileInputStream(source)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private PlayerVoteStore.LegacyPlayerData legacy(Map<String, PlayerVoteStore.LegacyPlayerData> players, String name, String uuid) {
        String normalizedUuid = uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT);
        if (normalizedUuid.isEmpty()) {
            normalizedUuid = plugin.resolvePlayerUniqueId(name).trim().toLowerCase(Locale.ROOT);
        }
        String key = normalizedUuid.isEmpty() ? "name." + normalizePlayer(name) : "uuid." + normalizedUuid;
        PlayerVoteStore.LegacyPlayerData player = players.get(key);
        if (player == null) {
            player = new PlayerVoteStore.LegacyPlayerData(name, normalizedUuid);
            players.put(key, player);
        } else {
            player.updateName(name);
        }
        return player;
    }

    private RewardKey parseRewardKey(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0) {
            return new RewardKey("", key);
        }
        return new RewardKey(key.substring(0, dot), key.substring(dot + 1));
    }

    private String baseKey(String key) {
        for (String suffix : new String[]{".lastDay", ".streak", ".bestStreak", ".milestones", ".name", ".uuid"}) {
            if (key.endsWith(suffix)) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return "";
    }

    private String nameFromBaseKey(String baseKey) {
        return baseKey.startsWith("name.") ? baseKey.substring("name.".length()) : "";
    }

    private String uuidFromBaseKey(String baseKey) {
        return baseKey.startsWith("uuid.") ? baseKey.substring("uuid.".length()) : "";
    }

    private Set<Integer> parseMilestones(String value) {
        Set<Integer> milestones = new TreeSet<>();
        if (value == null || value.trim().isEmpty()) {
            return milestones;
        }
        for (String part : value.split(",")) {
            int milestone = parseInt(part.trim());
            if (milestone > 0) {
                milestones.add(milestone);
            }
        }
        return milestones;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String normalizePlayer(String player) {
        return (player == null ? "" : player).toLowerCase(Locale.ROOT);
    }

    private File file(String name) {
        return new File(dataFolder, name);
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
    }

    private static class RewardKey {

        private final String date;
        private final String player;

        private RewardKey(String date, String player) {
            this.date = date;
            this.player = player == null ? "" : player;
        }
    }
}

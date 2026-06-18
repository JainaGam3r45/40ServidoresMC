package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class PlayerVoteStore {

    private static final String YAML_EXTENSION = ".yml";

    private final File playersFolder;
    private final CSPlugin plugin;
    private final Map<String, String> namesByUuid = new HashMap<>();
    private final Map<String, String> uuidsByName = new HashMap<>();

    public PlayerVoteStore(File dataFolder, CSPlugin plugin) {
        this(new File(dataFolder, "players"), plugin, true);
    }

    PlayerVoteStore(File folder, CSPlugin plugin, boolean directPlayersFolder) {
        this.playersFolder = directPlayersFolder ? folder : new File(folder, "players");
        this.plugin = plugin;
        ensurePlayersFolder();
        rebuildNameIndex();
    }

    public synchronized MarkResult markRewarded(CSCommandSender sender, String rewardDate, long rewardedAt) {
        PlayerVoteData player = load(sender);
        if (!player.hasUuid()) {
            return MarkResult.FAILED;
        }
        if (rewardDate.equals(player.lastRewardDate)) {
            return MarkResult.DUPLICATE;
        }

        PlayerVoteData previous = player.copy();
        player.lastVoteAt = Math.max(player.lastVoteAt, rewardedAt);
        player.lastRewardAt = Math.max(player.lastRewardAt, rewardedAt);
        player.lastRewardDate = rewardDate;
        if (!save(player)) {
            save(previous);
            return MarkResult.FAILED;
        }
        return MarkResult.MARKED;
    }

    public synchronized boolean markRewarded(String playerName, String rewardDate, long rewardedAt) {
        PlayerVoteData player = loadByName(playerName);
        if (!player.hasUuid()) {
            return false;
        }
        player.name = preferName(player.name, playerName);
        player.lastVoteAt = Math.max(player.lastVoteAt, rewardedAt);
        player.lastRewardAt = Math.max(player.lastRewardAt, rewardedAt);
        player.lastRewardDate = rewardDate;
        return save(player);
    }

    public synchronized boolean recordVote(CSCommandSender sender, long votedAt) {
        PlayerVoteData player = load(sender);
        if (!player.hasUuid()) {
            return false;
        }
        player.lastVoteAt = Math.max(player.lastVoteAt, votedAt);
        return save(player);
    }

    public synchronized boolean recordVote(String playerName, long votedAt) {
        PlayerVoteData player = loadByName(playerName);
        if (!player.hasUuid()) {
            return false;
        }
        player.name = preferName(player.name, playerName);
        player.lastVoteAt = Math.max(player.lastVoteAt, votedAt);
        return save(player);
    }

    public synchronized long lastVoteAt(String playerName) {
        return loadByName(playerName).lastVoteAt;
    }

    public synchronized long lastVoteAt(CSCommandSender sender) {
        return load(sender).lastVoteAt;
    }

    public synchronized boolean wasRemindedFor(String playerName, long voteCycle) {
        return loadByName(playerName).lastReminderAt == voteCycle;
    }

    public synchronized boolean markReminded(String playerName, long voteCycle) {
        PlayerVoteData player = loadByName(playerName);
        if (!player.hasUuid()) {
            return false;
        }
        player.lastReminderAt = voteCycle;
        return save(player);
    }

    public synchronized boolean hasRewardedOnDate(CSCommandSender sender, String rewardDate) {
        PlayerVoteData player = load(sender);
        return rewardDate.equals(player.lastRewardDate);
    }

    public synchronized VoteStreakStore.Snapshot recordStreak(String playerName, String uuid, LocalDate voteDay) {
        PlayerVoteData player = load(playerName, uuid);
        if (!player.hasUuid()) {
            return new VoteStreakStore.Snapshot("", playerName, "", "", 0, 0, Collections.emptySet(), false);
        }

        String lastDay = player.lastVoteDay;
        int currentStreak = player.currentStreak;
        int bestStreak = Math.max(player.bestStreak, currentStreak);
        Set<Integer> rewardedMilestones = new TreeSet<>(player.rewardedMilestones);
        boolean sameDay = voteDay.toString().equals(lastDay);

        if (!sameDay) {
            if (isYesterday(lastDay, voteDay)) {
                currentStreak++;
            } else {
                currentStreak = 1;
                rewardedMilestones.clear();
            }
        }

        player.name = preferName(player.name, playerName);
        player.uuid = normalizeUuid(uuid);
        player.lastVoteDay = voteDay.toString();
        player.currentStreak = currentStreak;
        player.bestStreak = Math.max(bestStreak, currentStreak);
        player.rewardedMilestones = rewardedMilestones;

        boolean saved = save(player);
        return new VoteStreakStore.Snapshot(player.key(), player.name, player.uuid, player.lastVoteDay,
                player.currentStreak, player.bestStreak, player.rewardedMilestones, saved);
    }

    public synchronized VoteStreakStore.Snapshot findStreak(String playerName) {
        PlayerVoteData player = loadByName(playerName);
        return new VoteStreakStore.Snapshot(player.key(), player.name, player.uuid, player.lastVoteDay,
                player.currentStreak, Math.max(player.bestStreak, player.currentStreak),
                player.rewardedMilestones, true);
    }

    public synchronized boolean resetStreak(String playerName) {
        PlayerVoteData player = loadByName(playerName);
        if (!player.hasUuid()) {
            return false;
        }
        player.lastVoteDay = "";
        player.currentStreak = 0;
        player.bestStreak = 0;
        player.rewardedMilestones.clear();
        return save(player);
    }

    public synchronized boolean markMilestoneRewarded(String key, int milestone) {
        PlayerVoteData player = loadByKey(key);
        if (!player.hasUuid() || player.rewardedMilestones.contains(milestone)) {
            return false;
        }
        player.rewardedMilestones.add(milestone);
        return save(player);
    }

    public synchronized MergeResult mergeLegacy(LegacyPlayerData legacy) {
        PlayerVoteData player = load(legacy.name, legacy.uuid);
        if (!player.hasUuid()) {
            return MergeResult.UNRESOLVED;
        }

        player.name = preferName(player.name, legacy.name);
        player.lastVoteAt = Math.max(player.lastVoteAt, legacy.lastVoteAt);
        player.lastRewardAt = Math.max(player.lastRewardAt, legacy.lastRewardAt);
        player.lastReminderAt = Math.max(player.lastReminderAt, legacy.lastReminderAt);
        player.currentStreak = Math.max(player.currentStreak, legacy.currentStreak);
        player.bestStreak = Math.max(Math.max(player.bestStreak, player.currentStreak), legacy.bestStreak);
        if (isNewerDay(legacy.lastVoteDay, player.lastVoteDay)) {
            player.lastVoteDay = legacy.lastVoteDay;
        }
        if (isNewerDay(legacy.lastRewardDate, player.lastRewardDate)) {
            player.lastRewardDate = legacy.lastRewardDate;
        }
        player.rewardedMilestones.addAll(legacy.rewardedMilestones);
        return save(player) ? MergeResult.MERGED : MergeResult.FAILED;
    }

    public File getPlayersFolder() {
        return playersFolder;
    }

    private PlayerVoteData load(CSCommandSender sender) {
        return load(sender.getName(), sender.getUniqueId());
    }

    private PlayerVoteData load(String playerName, String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid.isEmpty()) {
            normalizedUuid = resolveUuid(playerName);
        }
        if (normalizedUuid.isEmpty()) {
            return new PlayerVoteData(playerName, "");
        }

        PlayerVoteData player = read(playerFile(normalizedUuid));
        player.uuid = normalizedUuid;
        player.name = preferName(player.name, playerName);
        return player;
    }

    private PlayerVoteData loadByName(String playerName) {
        String normalizedName = normalizePlayer(playerName);
        String uuid = uuidsByName.get(normalizedName);
        if (uuid == null || uuid.isEmpty()) {
            uuid = resolveUuid(playerName);
        }
        return load(playerName, uuid);
    }

    private PlayerVoteData loadByKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return new PlayerVoteData("", "");
        }
        if (key.startsWith("uuid.")) {
            return load("", key.substring("uuid.".length()));
        }
        if (key.startsWith("name.")) {
            return loadByName(key.substring("name.".length()));
        }
        return load("", key);
    }

    private String resolveUuid(String playerName) {
        String normalizedName = normalizePlayer(playerName);
        String indexed = uuidsByName.get(normalizedName);
        if (indexed != null && !indexed.isEmpty()) {
            return indexed;
        }
        String resolved = normalizeUuid(plugin.resolvePlayerUniqueId(playerName));
        if (!resolved.isEmpty()) {
            uuidsByName.put(normalizedName, resolved);
        }
        return resolved;
    }

    private PlayerVoteData read(File file) {
        PlayerVoteData player = new PlayerVoteData("", stripExtension(file.getName()));
        if (!file.exists()) {
            return player;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                readLine(player, line);
            }
        } catch (IOException ex) {
            plugin.logError("No se pudo cargar el archivo de jugador " + file.getName() + ": " + ex.getMessage());
        }
        return player;
    }

    private void readLine(PlayerVoteData player, String line) {
        int separator = line.indexOf(':');
        if (separator < 0) {
            return;
        }

        String key = line.substring(0, separator).trim();
        String value = line.substring(separator + 1).trim();
        if ("name".equals(key)) {
            player.name = unquote(value);
        } else if ("lastVoteAt".equals(key)) {
            player.lastVoteAt = parseLong(value);
        } else if ("lastRewardAt".equals(key)) {
            player.lastRewardAt = parseLong(value);
        } else if ("lastRewardDate".equals(key)) {
            player.lastRewardDate = unquote(value);
        } else if ("currentStreak".equals(key)) {
            player.currentStreak = parseInt(value);
        } else if ("bestStreak".equals(key)) {
            player.bestStreak = parseInt(value);
        } else if ("lastReminderAt".equals(key)) {
            player.lastReminderAt = parseLong(value);
        } else if ("lastVoteDay".equals(key)) {
            player.lastVoteDay = unquote(value);
        } else if ("rewardedMilestones".equals(key)) {
            player.rewardedMilestones = parseMilestones(value);
        }
    }

    private boolean save(PlayerVoteData player) {
        ensurePlayersFolder();
        if (!player.hasUuid()) {
            return false;
        }

        try {
            Files.write(playerFile(player.uuid).toPath(), render(player).getBytes(StandardCharsets.UTF_8));
            index(player);
            return true;
        } catch (IOException ex) {
            plugin.logError("No se pudo guardar el archivo de jugador " + player.uuid + ": " + ex.getMessage());
            return false;
        }
    }

    private String render(PlayerVoteData player) {
        String lineSeparator = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        builder.append("name: ").append(quote(player.name)).append(lineSeparator);
        builder.append("lastVoteAt: ").append(player.lastVoteAt).append(lineSeparator);
        builder.append("lastRewardAt: ").append(player.lastRewardAt).append(lineSeparator);
        builder.append("lastRewardDate: ").append(quote(player.lastRewardDate)).append(lineSeparator);
        builder.append("currentStreak: ").append(player.currentStreak).append(lineSeparator);
        builder.append("bestStreak: ").append(player.bestStreak).append(lineSeparator);
        builder.append("lastReminderAt: ").append(player.lastReminderAt).append(lineSeparator);
        builder.append("lastVoteDay: ").append(quote(player.lastVoteDay)).append(lineSeparator);
        builder.append("rewardedMilestones: ").append(renderMilestones(player.rewardedMilestones)).append(lineSeparator);
        return builder.toString();
    }

    private void rebuildNameIndex() {
        File[] files = playersFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(YAML_EXTENSION));
        if (files == null) {
            return;
        }
        for (File file : files) {
            PlayerVoteData player = read(file);
            if (!player.hasUuid()) {
                player.uuid = stripExtension(file.getName());
            }
            index(player);
        }
    }

    private void index(PlayerVoteData player) {
        if (!player.hasUuid()) {
            return;
        }
        namesByUuid.put(player.uuid, player.name);
        String normalizedName = normalizePlayer(player.name);
        if (!normalizedName.isEmpty()) {
            uuidsByName.put(normalizedName, player.uuid);
        }
    }

    private void ensurePlayersFolder() {
        if (!playersFolder.exists() && !playersFolder.mkdirs()) {
            plugin.logError("No se pudo crear la carpeta de jugadores: " + playersFolder.getAbsolutePath());
        }
    }

    private File playerFile(String uuid) {
        return new File(playersFolder, normalizeUuid(uuid) + YAML_EXTENSION);
    }

    private boolean isYesterday(String lastDay, LocalDate voteDay) {
        if (lastDay == null || lastDay.trim().isEmpty()) {
            return false;
        }
        try {
            return LocalDate.parse(lastDay).plusDays(1L).equals(voteDay);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isNewerDay(String candidate, String current) {
        if (candidate == null || candidate.trim().isEmpty()) {
            return false;
        }
        if (current == null || current.trim().isEmpty()) {
            return true;
        }
        try {
            return LocalDate.parse(candidate).isAfter(LocalDate.parse(current));
        } catch (RuntimeException ignored) {
            return candidate.compareTo(current) > 0;
        }
    }

    private String preferName(String current, String candidate) {
        if (candidate != null && !candidate.trim().isEmpty()) {
            return candidate;
        }
        return current == null ? "" : current;
    }

    private String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String unquote(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String renderMilestones(Set<Integer> milestones) {
        if (milestones == null || milestones.isEmpty()) {
            return "[]";
        }
        List<String> values = new ArrayList<>();
        for (Integer milestone : new TreeSet<>(milestones)) {
            values.add(String.valueOf(milestone));
        }
        return "[" + String.join(", ", values) + "]";
    }

    private Set<Integer> parseMilestones(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        Set<Integer> milestones = new TreeSet<>();
        if (trimmed.isEmpty()) {
            return milestones;
        }
        for (String part : trimmed.split(",")) {
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

    private String stripExtension(String fileName) {
        return fileName.endsWith(YAML_EXTENSION) ? fileName.substring(0, fileName.length() - YAML_EXTENSION.length()) : fileName;
    }

    private String normalizePlayer(String player) {
        return (player == null ? "" : player).toLowerCase(Locale.ROOT);
    }

    private String normalizeUuid(String uuid) {
        return uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT);
    }

    public enum MarkResult {
        MARKED,
        DUPLICATE,
        FAILED
    }

    public enum MergeResult {
        MERGED,
        UNRESOLVED,
        FAILED
    }

    public static class LegacyPlayerData {

        private String name;
        private final String uuid;
        private long lastVoteAt;
        private long lastRewardAt;
        private long lastReminderAt;
        private String lastVoteDay = "";
        private String lastRewardDate = "";
        private int currentStreak;
        private int bestStreak;
        private Set<Integer> rewardedMilestones = new HashSet<>();

        public LegacyPlayerData(String name, String uuid) {
            this.name = name == null ? "" : name;
            this.uuid = uuid == null ? "" : uuid;
        }

        public void recordVote(long value) {
            lastVoteAt = Math.max(lastVoteAt, value);
        }

        public void updateName(String name) {
            if (name != null && !name.trim().isEmpty()) {
                this.name = name;
            }
        }

        public void recordReward(long value, String date) {
            lastRewardAt = Math.max(lastRewardAt, value);
            lastVoteAt = Math.max(lastVoteAt, value);
            if (date != null && !date.trim().isEmpty()) {
                lastRewardDate = date;
            }
        }

        public void recordReminder(long value) {
            lastReminderAt = Math.max(lastReminderAt, value);
        }

        public void recordStreak(String day, int streak, int bestStreak, Set<Integer> milestones) {
            if (day != null && !day.trim().isEmpty()) {
                lastVoteDay = day;
            }
            this.currentStreak = Math.max(this.currentStreak, streak);
            this.bestStreak = Math.max(this.bestStreak, bestStreak);
            this.rewardedMilestones.addAll(milestones);
        }

        public String key() {
            return uuid == null || uuid.trim().isEmpty() ? "name." + name.toLowerCase(Locale.ROOT) : "uuid." + uuid.toLowerCase(Locale.ROOT);
        }
    }

    private static class PlayerVoteData {

        private String name;
        private String uuid;
        private long lastVoteAt;
        private long lastRewardAt;
        private String lastRewardDate = "";
        private int currentStreak;
        private int bestStreak;
        private long lastReminderAt;
        private String lastVoteDay = "";
        private Set<Integer> rewardedMilestones = new TreeSet<>();

        private PlayerVoteData(String name, String uuid) {
            this.name = name == null ? "" : name;
            this.uuid = uuid == null ? "" : uuid;
        }

        private boolean hasUuid() {
            return uuid != null && !uuid.trim().isEmpty();
        }

        private String key() {
            return "uuid." + uuid;
        }

        private PlayerVoteData copy() {
            PlayerVoteData copy = new PlayerVoteData(name, uuid);
            copy.lastVoteAt = lastVoteAt;
            copy.lastRewardAt = lastRewardAt;
            copy.lastRewardDate = lastRewardDate;
            copy.currentStreak = currentStreak;
            copy.bestStreak = bestStreak;
            copy.lastReminderAt = lastReminderAt;
            copy.lastVoteDay = lastVoteDay;
            copy.rewardedMilestones = new TreeSet<>(rewardedMilestones);
            return copy;
        }
    }
}

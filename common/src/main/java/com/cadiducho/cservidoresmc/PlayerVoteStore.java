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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class PlayerVoteStore {

    private static final String YAML_EXTENSION = ".yml";
    private static final int MAX_CACHED_PLAYERS = 4096;

    private final File playersFolder;
    private final CSPlugin plugin;
    private final Executor loaderExecutor;
    private final ConcurrentMap<String, PlayerVoteData> playersByUuid = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> uuidsByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<Void>> pendingLoads = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<Void>> writeTails = new ConcurrentHashMap<>();

    public PlayerVoteStore(File dataFolder, CSPlugin plugin) {
        this(new File(dataFolder, "players"), plugin, true, plugin == null ? null : plugin.getAsyncExecutor());
    }

    PlayerVoteStore(File folder, CSPlugin plugin, boolean directPlayersFolder) {
        this(folder, plugin, directPlayersFolder, plugin == null ? null : plugin.getAsyncExecutor());
    }

    PlayerVoteStore(File folder, CSPlugin plugin, boolean directPlayersFolder, Executor loaderExecutor) {
        this.playersFolder = directPlayersFolder ? folder : new File(folder, "players");
        this.plugin = plugin;
        this.loaderExecutor = loaderExecutor == null ? Runnable::run : loaderExecutor;
        ensurePlayersFolder();
    }

    public MarkResult markRewarded(CSCommandSender sender, String rewardDate, long rewardedAt) {
        return markRewarded(sender.getName(), sender.getUniqueId(), rewardDate, rewardedAt, true);
    }

    public MarkResult markRewarded(String playerName, String uuid, String rewardDate, long rewardedAt) {
        return markRewarded(playerName, uuid, rewardDate, rewardedAt, true);
    }

    public boolean markRewarded(String playerName, String rewardDate, long rewardedAt) {
        return markRewarded(playerName, "", rewardDate, rewardedAt, false) != MarkResult.FAILED;
    }

    public boolean recordVote(CSCommandSender sender, long votedAt) {
        return recordVoteData(sender.getName(), sender.getUniqueId(), votedAt);
    }

    public boolean recordVote(String playerName, long votedAt) {
        return recordVoteData(playerName, "", votedAt);
    }

    public boolean recordVote(String playerName, String uuid, long votedAt) {
        return recordVoteData(playerName, uuid, votedAt);
    }

    public long lastVoteAt(String playerName) {
        return loadByName(playerName).lastVoteAt;
    }

    public long lastVoteAt(String playerName, String uuid) {
        return load(playerName, uuid).lastVoteAt;
    }

    public long lastVoteAt(CSCommandSender sender) {
        return load(sender).lastVoteAt;
    }

    public long cachedLastVoteAt(String playerName, String uuid) {
        PlayerVoteData player = cachedPlayer(playerName, uuid);
        return player == null ? 0L : player.lastVoteAt;
    }

    public boolean wasRemindedFor(String playerName, long voteCycle) {
        return loadByName(playerName).lastReminderAt == voteCycle;
    }

    public boolean wasRemindedFor(String playerName, String uuid, long voteCycle) {
        return load(playerName, uuid).lastReminderAt == voteCycle;
    }

    public boolean markReminded(String playerName, long voteCycle) {
        return updateByName(playerName, player -> {
            if (!player.hasUuid()) {
                return UpdateResult.failed(player);
            }
            player.lastReminderAt = voteCycle;
            return UpdateResult.saved(player);
        }).saved;
    }

    public boolean markReminded(String playerName, String uuid, long voteCycle) {
        return update(playerName, uuid, player -> {
            if (!player.hasUuid()) {
                return UpdateResult.failed(player);
            }
            player.name = preferName(player.name, playerName);
            player.lastReminderAt = voteCycle;
            return UpdateResult.saved(player);
        }).saved;
    }

    public boolean hasRewardedOnDate(CSCommandSender sender, String rewardDate) {
        PlayerVoteData cached = cachedPlayer(sender.getName(), sender.getUniqueId());
        if (cached != null) {
            return rewardDate.equals(cached.lastRewardDate);
        }
        return load(sender).lastRewardDate.equals(rewardDate);
    }

    public boolean cachedRewardedOnDate(String playerName, String uuid, String rewardDate) {
        PlayerVoteData cached = cachedPlayer(playerName, uuid);
        return cached != null && rewardDate.equals(cached.lastRewardDate);
    }

    public VoteStreakStore.Snapshot recordStreak(String playerName, String uuid, LocalDate voteDay) {
        UpdateResult result = update(playerName, uuid, player -> {
            if (!player.hasUuid()) {
                return UpdateResult.failed(player);
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
            return UpdateResult.saved(player);
        });

        PlayerVoteData player = result.player;
        return new VoteStreakStore.Snapshot(player.key(), player.name, player.uuid, player.lastVoteDay,
                player.currentStreak, player.bestStreak, player.rewardedMilestones, result.saved);
    }

    public VoteStreakStore.Snapshot findStreak(String playerName) {
        PlayerVoteData player = loadByName(playerName);
        return snapshot(player, true);
    }

    public VoteStreakStore.Snapshot cachedStreak(String playerName, String uuid) {
        PlayerVoteData player = cachedPlayer(playerName, uuid);
        return player == null ? null : snapshot(player, true);
    }

    public boolean resetStreak(String playerName) {
        return updateByName(playerName, player -> {
            if (!player.hasUuid()) {
                return UpdateResult.failed(player);
            }
            player.lastVoteDay = "";
            player.currentStreak = 0;
            player.bestStreak = 0;
            player.rewardedMilestones.clear();
            return UpdateResult.saved(player);
        }).saved;
    }

    public boolean markMilestoneRewarded(String key, int milestone) {
        UpdateResult result = updateByKey(key, player -> {
            if (!player.hasUuid() || player.rewardedMilestones.contains(milestone)) {
                return UpdateResult.failed(player);
            }
            player.rewardedMilestones.add(milestone);
            return UpdateResult.saved(player);
        });
        return result.saved;
    }

    public MergeResult mergeLegacy(LegacyPlayerData legacy) {
        UpdateResult result = update(legacy.name, legacy.uuid, player -> {
            if (!player.hasUuid()) {
                return UpdateResult.unresolved(player);
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
            return UpdateResult.saved(player);
        });

        if (result.unresolved) {
            return MergeResult.UNRESOLVED;
        }
        return result.saved ? MergeResult.MERGED : MergeResult.FAILED;
    }

    public File getPlayersFolder() {
        return playersFolder;
    }

    public void requestLoad(String playerName, String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        String normalizedName = normalizePlayer(playerName);
        String key = !normalizedUuid.isEmpty() ? "uuid." + normalizedUuid : "name." + normalizedName;
        if (key.endsWith(".")) {
            return;
        }
        pendingLoads.computeIfAbsent(key, ignored -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> load(playerName, normalizedUuid), loaderExecutor);
            future.whenComplete((ignoredResult, ignoredError) -> pendingLoads.remove(key));
            return future;
        });
    }

    public void warmUp(List<CSCommandSender> onlinePlayers) {
        if (onlinePlayers == null) {
            return;
        }
        for (CSCommandSender player : onlinePlayers) {
            requestLoad(player.getName(), player.getUniqueId());
        }
    }

    public void cleanupCache(Set<String> onlineUuids) {
        Set<String> protectedUuids = onlineUuids == null ? Collections.emptySet() : onlineUuids;
        if (playersByUuid.size() <= MAX_CACHED_PLAYERS) {
            return;
        }
        for (String uuid : new ArrayList<>(playersByUuid.keySet())) {
            if (playersByUuid.size() <= MAX_CACHED_PLAYERS || protectedUuids.contains(uuid)) {
                continue;
            }
            PlayerVoteData removed = playersByUuid.remove(uuid);
            if (removed != null) {
                uuidsByName.remove(normalizePlayer(removed.name), uuid);
            }
        }
    }

    private MarkResult markRewarded(String playerName, String uuid, String rewardDate, long rewardedAt, boolean rejectDuplicate) {
        UpdateResult result = update(playerName, uuid, player -> {
            if (!player.hasUuid()) {
                return UpdateResult.failed(player);
            }
            if (rejectDuplicate && rewardDate.equals(player.lastRewardDate)) {
                return UpdateResult.duplicate(player);
            }
            player.name = preferName(player.name, playerName);
            player.lastVoteAt = Math.max(player.lastVoteAt, rewardedAt);
            player.lastRewardAt = Math.max(player.lastRewardAt, rewardedAt);
            player.lastRewardDate = rewardDate;
            return UpdateResult.saved(player);
        });
        if (result.duplicate) {
            return MarkResult.DUPLICATE;
        }
        return result.saved ? MarkResult.MARKED : MarkResult.FAILED;
    }

    private boolean recordVoteData(String playerName, String uuid, long votedAt) {
        return update(playerName, uuid, player -> {
            if (!player.hasUuid()) {
                return UpdateResult.failed(player);
            }
            player.name = preferName(player.name, playerName);
            player.lastVoteAt = Math.max(player.lastVoteAt, votedAt);
            return UpdateResult.saved(player);
        }).saved;
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

        PlayerVoteData cached = playersByUuid.get(normalizedUuid);
        if (cached != null) {
            return cached.copy();
        }

        PlayerVoteData player = read(playerFile(normalizedUuid));
        player.uuid = normalizedUuid;
        player.name = preferName(player.name, playerName);
        publish(player);
        return player.copy();
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

    private UpdateResult updateByName(String playerName, Function<PlayerVoteData, UpdateResult> update) {
        String uuid = uuidsByName.get(normalizePlayer(playerName));
        if (uuid == null || uuid.isEmpty()) {
            uuid = resolveUuid(playerName);
        }
        return update(playerName, uuid, update);
    }

    private UpdateResult updateByKey(String key, Function<PlayerVoteData, UpdateResult> update) {
        PlayerVoteData player = loadByKey(key);
        return update(player.name, player.uuid, update);
    }

    private UpdateResult update(String playerName, String uuid, Function<PlayerVoteData, UpdateResult> update) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid.isEmpty()) {
            normalizedUuid = resolveUuid(playerName);
        }
        if (normalizedUuid.isEmpty()) {
            return UpdateResult.failed(new PlayerVoteData(playerName, ""));
        }

        String key = normalizedUuid;
        CompletableFuture<UpdateResult> result = new CompletableFuture<>();
        writeTails.compute(key, (ignored, previous) -> {
            CompletableFuture<Void> safePrevious = previous == null ? CompletableFuture.completedFuture(null) : previous;
            return safePrevious.handle((ignoredValue, ignoredError) -> null).thenRunAsync(() -> {
                try {
                    result.complete(applyUpdate(playerName, key, update));
                } catch (RuntimeException ex) {
                    result.completeExceptionally(ex);
                }
            }, Runnable::run);
        }).whenComplete((ignoredValue, ignoredError) -> writeTails.remove(key));

        try {
            return result.join();
        } catch (RuntimeException ex) {
            logError("No se pudo actualizar el jugador " + key + ": " + ex.getMessage());
            return UpdateResult.failed(new PlayerVoteData(playerName, key));
        }
    }

    private UpdateResult applyUpdate(String playerName, String uuid, Function<PlayerVoteData, UpdateResult> update) {
        PlayerVoteData current = load(playerName, uuid);
        UpdateResult updated = update.apply(current.copy());
        if (updated.unresolved || updated.duplicate || !updated.saved) {
            publish(updated.player);
            return updated;
        }
        if (!save(updated.player)) {
            return UpdateResult.failed(current);
        }
        publish(updated.player);
        return updated;
    }

    private PlayerVoteData cachedPlayer(String playerName, String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid.isEmpty()) {
            normalizedUuid = uuidsByName.get(normalizePlayer(playerName));
        }
        if (normalizedUuid == null || normalizedUuid.isEmpty()) {
            return null;
        }
        PlayerVoteData player = playersByUuid.get(normalizedUuid);
        return player == null ? null : player.copy();
    }

    private String resolveUuid(String playerName) {
        String normalizedName = normalizePlayer(playerName);
        String indexed = uuidsByName.get(normalizedName);
        if (indexed != null && !indexed.isEmpty()) {
            return indexed;
        }
        String resolved = normalizeUuid(plugin == null ? "" : plugin.resolvePlayerUniqueId(playerName));
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
            logError("No se pudo cargar el archivo de jugador " + file.getName() + ": " + ex.getMessage());
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
            return true;
        } catch (IOException ex) {
            logError("No se pudo guardar el archivo de jugador " + player.uuid + ": " + ex.getMessage());
            return false;
        }
    }

    private void publish(PlayerVoteData player) {
        if (!player.hasUuid()) {
            return;
        }
        PlayerVoteData snapshot = player.copy();
        playersByUuid.put(snapshot.uuid, snapshot);
        String normalizedName = normalizePlayer(snapshot.name);
        if (!normalizedName.isEmpty()) {
            uuidsByName.put(normalizedName, snapshot.uuid);
        }
    }

    private VoteStreakStore.Snapshot snapshot(PlayerVoteData player, boolean saved) {
        return new VoteStreakStore.Snapshot(player.key(), player.name, player.uuid, player.lastVoteDay,
                player.currentStreak, Math.max(player.bestStreak, player.currentStreak),
                player.rewardedMilestones, saved);
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

    private void ensurePlayersFolder() {
        if (!playersFolder.exists() && !playersFolder.mkdirs()) {
            logError("No se pudo crear la carpeta de jugadores: " + playersFolder.getAbsolutePath());
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

    private void logError(String message) {
        if (plugin != null) {
            plugin.logError(message);
        }
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

    private static class UpdateResult {

        private final PlayerVoteData player;
        private final boolean saved;
        private final boolean duplicate;
        private final boolean unresolved;

        private UpdateResult(PlayerVoteData player, boolean saved, boolean duplicate, boolean unresolved) {
            this.player = player;
            this.saved = saved;
            this.duplicate = duplicate;
            this.unresolved = unresolved;
        }

        private static UpdateResult saved(PlayerVoteData player) {
            return new UpdateResult(player, true, false, false);
        }

        private static UpdateResult failed(PlayerVoteData player) {
            return new UpdateResult(player, false, false, false);
        }

        private static UpdateResult duplicate(PlayerVoteData player) {
            return new UpdateResult(player, false, true, false);
        }

        private static UpdateResult unresolved(PlayerVoteData player) {
            return new UpdateResult(player, false, false, true);
        }
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

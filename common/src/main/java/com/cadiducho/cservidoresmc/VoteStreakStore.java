package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class VoteStreakStore {

    private static final String LAST_DAY_SUFFIX = ".lastDay";
    private static final String STREAK_SUFFIX = ".streak";
    private static final String MILESTONES_SUFFIX = ".milestones";
    private static final String NAME_SUFFIX = ".name";
    private static final String UUID_SUFFIX = ".uuid";
    private static final String NAME_INDEX_PREFIX = "names.";

    private final File file;
    private final CSPlugin plugin;
    private final Properties streaks = new Properties();

    VoteStreakStore(File file, CSPlugin plugin) {
        this.file = file;
        this.plugin = plugin;
        load();
    }

    synchronized Snapshot recordVote(String player, String uuid, LocalDate voteDay) {
        String key = resolveKey(player, uuid);
        String lastDay = streaks.getProperty(key + LAST_DAY_SUFFIX);
        int streak = getInt(key + STREAK_SUFFIX);
        Set<Integer> rewardedMilestones = getMilestones(key);
        boolean sameDay = voteDay.toString().equals(lastDay);

        if (!sameDay) {
            if (isYesterday(lastDay, voteDay)) {
                streak++;
            } else {
                streak = 1;
                rewardedMilestones.clear();
            }
        }

        setIdentity(key, player, uuid);
        streaks.setProperty(key + LAST_DAY_SUFFIX, voteDay.toString());
        streaks.setProperty(key + STREAK_SUFFIX, String.valueOf(streak));
        setMilestones(key, rewardedMilestones);

        if (!save()) {
            return new Snapshot(key, player, uuid, lastDay, getInt(key + STREAK_SUFFIX), rewardedMilestones, false);
        }

        return new Snapshot(key, player, uuid, voteDay.toString(), streak, rewardedMilestones, true);
    }

    synchronized Snapshot find(String player) {
        String key = resolveKey(player, "");
        return snapshot(key);
    }

    synchronized boolean reset(String player) {
        String key = resolveKey(player, "");
        removeRecord(key);
        removeNameIndexes(key);
        return save();
    }

    synchronized boolean markMilestoneRewarded(String key, int milestone) {
        Set<Integer> rewardedMilestones = getMilestones(key);
        if (rewardedMilestones.contains(milestone)) {
            return false;
        }

        rewardedMilestones.add(milestone);
        setMilestones(key, rewardedMilestones);
        if (!save()) {
            rewardedMilestones.remove(milestone);
            setMilestones(key, rewardedMilestones);
            return false;
        }
        return true;
    }

    private Snapshot snapshot(String key) {
        String player = streaks.getProperty(key + NAME_SUFFIX, "");
        String uuid = streaks.getProperty(key + UUID_SUFFIX, "");
        String lastDay = streaks.getProperty(key + LAST_DAY_SUFFIX, "");
        int streak = getInt(key + STREAK_SUFFIX);
        return new Snapshot(key, player, uuid, lastDay, streak, getMilestones(key), true);
    }

    private String resolveKey(String player, String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (!normalizedUuid.isEmpty()) {
            return "uuid." + normalizedUuid;
        }

        String normalizedPlayer = normalizePlayer(player);
        String indexedKey = streaks.getProperty(NAME_INDEX_PREFIX + normalizedPlayer);
        if (indexedKey != null && !indexedKey.trim().isEmpty()) {
            return indexedKey;
        }
        return "name." + normalizedPlayer;
    }

    private void setIdentity(String key, String player, String uuid) {
        String normalizedPlayer = normalizePlayer(player);
        streaks.setProperty(key + NAME_SUFFIX, player == null ? "" : player);
        streaks.setProperty(key + UUID_SUFFIX, normalizeUuid(uuid));
        if (!normalizedPlayer.isEmpty()) {
            streaks.setProperty(NAME_INDEX_PREFIX + normalizedPlayer, key);
        }
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

    private int getInt(String key) {
        String value = streaks.getProperty(key);
        if (value == null) {
            return 0;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Set<Integer> getMilestones(String key) {
        String value = streaks.getProperty(key + MILESTONES_SUFFIX, "");
        Set<Integer> milestones = new TreeSet<>();
        if (value.trim().isEmpty()) {
            return milestones;
        }

        for (String part : value.split(",")) {
            try {
                milestones.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return milestones;
    }

    private void setMilestones(String key, Set<Integer> milestones) {
        if (milestones.isEmpty()) {
            streaks.remove(key + MILESTONES_SUFFIX);
            return;
        }

        StringBuilder value = new StringBuilder();
        for (Integer milestone : new TreeSet<>(milestones)) {
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(milestone);
        }
        streaks.setProperty(key + MILESTONES_SUFFIX, value.toString());
    }

    private void removeRecord(String key) {
        streaks.remove(key + LAST_DAY_SUFFIX);
        streaks.remove(key + STREAK_SUFFIX);
        streaks.remove(key + MILESTONES_SUFFIX);
        streaks.remove(key + NAME_SUFFIX);
        streaks.remove(key + UUID_SUFFIX);
    }

    private void removeNameIndexes(String key) {
        Set<String> names = new HashSet<>();
        for (String propertyName : streaks.stringPropertyNames()) {
            if (propertyName.startsWith(NAME_INDEX_PREFIX) && key.equals(streaks.getProperty(propertyName))) {
                names.add(propertyName);
            }
        }
        for (String name : names) {
            streaks.remove(name);
        }
    }

    private String normalizePlayer(String player) {
        return (player == null ? "" : player).toLowerCase(Locale.ROOT);
    }

    private String normalizeUuid(String uuid) {
        return uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(file)) {
            streaks.load(inputStream);
        } catch (IOException e) {
            plugin.logError("No se pudo cargar el registro local de rachas de voto: " + e.getMessage());
        }
    }

    private boolean save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            streaks.store(outputStream, "40ServidoresMC vote streaks");
            return true;
        } catch (IOException e) {
            plugin.logError("No se pudo guardar el registro local de rachas de voto: " + e.getMessage());
            return false;
        }
    }

    public static class Snapshot {

        private final String key;
        private final String player;
        private final String uuid;
        private final String lastDay;
        private final int streak;
        private final Set<Integer> rewardedMilestones;
        private final boolean saved;

        Snapshot(String key, String player, String uuid, String lastDay, int streak, Set<Integer> rewardedMilestones, boolean saved) {
            this.key = key;
            this.player = player;
            this.uuid = uuid;
            this.lastDay = lastDay;
            this.streak = streak;
            this.rewardedMilestones = new TreeSet<>(rewardedMilestones);
            this.saved = saved;
        }

        public String getKey() {
            return key;
        }

        public String getPlayer() {
            return player;
        }

        public String getUuid() {
            return uuid;
        }

        public String getLastDay() {
            return lastDay;
        }

        public int getStreak() {
            return streak;
        }

        public Set<Integer> getRewardedMilestones() {
            return Collections.unmodifiableSet(rewardedMilestones);
        }

        public boolean isSaved() {
            return saved;
        }
    }
}

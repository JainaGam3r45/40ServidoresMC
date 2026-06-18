package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

class VoteReminderStore {

    private static final String LAST_VOTE_SUFFIX = ".lastVoteAt";
    private static final String LAST_REMINDER_SUFFIX = ".lastReminderFor";

    private final File file;
    private final CSPlugin plugin;
    private final Properties reminders = new Properties();

    VoteReminderStore(File file, CSPlugin plugin) {
        this.file = file;
        this.plugin = plugin;
        load();
    }

    synchronized boolean recordVote(String player, long votedAt) {
        reminders.setProperty(lastVoteKey(player), String.valueOf(votedAt));
        return save();
    }

    synchronized long lastVoteAt(String player) {
        return getLong(lastVoteKey(player));
    }

    synchronized boolean wasRemindedFor(String player, long voteCycle) {
        return getLong(lastReminderKey(player)) == voteCycle;
    }

    synchronized boolean markReminded(String player, long voteCycle) {
        reminders.setProperty(lastReminderKey(player), String.valueOf(voteCycle));
        return save();
    }

    private long getLong(String key) {
        String value = reminders.getProperty(key);
        if (value == null) {
            return 0L;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String lastVoteKey(String player) {
        return normalizePlayer(player) + LAST_VOTE_SUFFIX;
    }

    private String lastReminderKey(String player) {
        return normalizePlayer(player) + LAST_REMINDER_SUFFIX;
    }

    private String normalizePlayer(String player) {
        return (player == null ? "" : player).toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(file)) {
            reminders.load(inputStream);
        } catch (IOException e) {
            plugin.logError("No se pudo cargar el registro local de recordatorios de voto: " + e.getMessage());
        }
    }

    private boolean save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            reminders.store(outputStream, "40ServidoresMC vote reminders");
            return true;
        } catch (IOException e) {
            plugin.logError("No se pudo guardar el registro local de recordatorios de voto: " + e.getMessage());
            return false;
        }
    }
}

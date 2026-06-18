package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

class RewardStore {

    private final File file;
    private final CSPlugin plugin;
    private final Properties rewardedVotes = new Properties();

    RewardStore(File file, CSPlugin plugin) {
        this.file = file;
        this.plugin = plugin;
        load();
    }

    synchronized MarkResult markRewarded(String player, String date) {
        String key = rewardKey(player, date);
        if (rewardedVotes.containsKey(key)) {
            return MarkResult.DUPLICATE;
        }

        rewardedVotes.setProperty(key, String.valueOf(System.currentTimeMillis()));
        if (!save()) {
            rewardedVotes.remove(key);
            return MarkResult.FAILED;
        }
        return MarkResult.MARKED;
    }

    String rewardKey(String player, String date) {
        return date + "." + normalizePlayer(player);
    }

    private String normalizePlayer(String player) {
        return (player == null ? "" : player).toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(file)) {
            rewardedVotes.load(inputStream);
        } catch (IOException e) {
            plugin.logError("No se pudo cargar el registro local de votos premiados: " + e.getMessage());
        }
    }

    private boolean save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            rewardedVotes.store(outputStream, "40ServidoresMC rewarded votes");
            return true;
        } catch (IOException e) {
            plugin.logError("No se pudo guardar el registro local de votos premiados: " + e.getMessage());
            return false;
        }
    }

    enum MarkResult {
        MARKED,
        DUPLICATE,
        FAILED
    }
}

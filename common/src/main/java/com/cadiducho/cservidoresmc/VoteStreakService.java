package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class VoteStreakService {

    private final CSPlugin plugin;
    private final VoteStreakStore store;
    private final Supplier<LocalDate> currentDay;

    public VoteStreakService(CSPlugin plugin) {
        this(plugin,
                new File(plugin.getPluginDataFolder(), "vote-streaks.properties"),
                () -> LocalDate.now(ZoneId.systemDefault()));
    }

    VoteStreakService(CSPlugin plugin, File streakFile, Supplier<LocalDate> currentDay) {
        this.plugin = plugin;
        this.store = new VoteStreakStore(streakFile, plugin);
        this.currentDay = currentDay;
    }

    public VoteStreakStore.Snapshot recordVote(CSCommandSender sender) {
        String player = sender.getName();
        String uuid = sender.getUniqueId();
        VoteStreakStore.Snapshot snapshot = store.recordVote(player, uuid, currentDay.get());
        if (!snapshot.isSaved()) {
            debug("No se pudo guardar la racha de " + player + ".");
            return snapshot;
        }

        deliverMilestoneRewards(player, uuid, snapshot);
        return snapshot;
    }

    public VoteStreakStore.Snapshot find(String player) {
        return store.find(player);
    }

    public boolean reset(String player) {
        return store.reset(player);
    }

    public int nextRewardMilestone(int streak) {
        for (Integer milestone : rewardsByMilestone().keySet()) {
            if (milestone > streak) {
                return milestone;
            }
        }
        return 0;
    }

    private void deliverMilestoneRewards(String player, String uuid, VoteStreakStore.Snapshot snapshot) {
        Map<Integer, List<String>> rewards = rewardsByMilestone();
        for (Map.Entry<Integer, List<String>> reward : rewards.entrySet()) {
            int milestone = reward.getKey();
            if (milestone <= 0 || milestone > snapshot.getStreak()) {
                continue;
            }

            List<String> commands = reward.getValue();
            if (commands == null || commands.isEmpty() || snapshot.getRewardedMilestones().contains(milestone)) {
                continue;
            }

            if (!store.markMilestoneRewarded(snapshot.getKey(), milestone)) {
                debug("Premio de racha " + milestone + " omitido para " + player + ": ya estaba marcado o no se pudo guardar.");
                continue;
            }

            for (String command : commands) {
                dispatchMilestoneCommand(command, player, uuid, snapshot.getStreak());
            }
        }
    }

    private void dispatchMilestoneCommand(String command, String player, String uuid, int streak) {
        String parsedCommand = command
                .replace("%player%", player)
                .replace("%uuid%", uuid == null ? "" : uuid)
                .replace("%streak%", String.valueOf(streak));
        plugin.runSync(() -> plugin.dispatchCommand(parsedCommand));
    }

    private Map<Integer, List<String>> rewardsByMilestone() {
        Map<String, List<String>> configuredRewards = plugin.getCSConfiguration().getStringListMap("streakRewards", Collections.emptyMap());
        Map<Integer, List<String>> rewards = new TreeMap<>();
        for (Map.Entry<String, List<String>> reward : configuredRewards.entrySet()) {
            try {
                rewards.put(Integer.parseInt(reward.getKey()), reward.getValue());
            } catch (NumberFormatException e) {
                debug("Milestone de racha inválido: " + reward.getKey());
            }
        }
        return rewards;
    }

    private void debug(String message) {
        if (plugin.isDebug()) {
            plugin.log("[VoteStreak] " + message);
        }
    }
}

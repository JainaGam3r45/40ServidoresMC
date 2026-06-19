package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.RewardService;
import com.cadiducho.cservidoresmc.VoteReminderService;
import com.cadiducho.cservidoresmc.VoteStreakService;
import com.cadiducho.cservidoresmc.VoteStreakStore;
import com.cadiducho.cservidoresmc.model.ServerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PlaceholderHook extends PlaceholderExpansion {

    private final BukkitPlugin bukkitPlugin;

    public PlaceholderHook(BukkitPlugin bukkitPlugin) {
        this.bukkitPlugin = bukkitPlugin;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getAuthor() {
        return "Cadiducho";
    }

    @Override
    public String getIdentifier() {
        return "40servidoresmc";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (identifier == null) {
            return notAvailable();
        }

        String placeholder = identifier.toLowerCase(Locale.ROOT);
        ServerStats serverStats = bukkitPlugin.getApiClient().cachedServerStats();

        if ("server_rank".equals(placeholder)) {
            return serverStats == null || serverStats.getServerName() == null ? notAvailable() : String.valueOf(serverStats.getPosition());
        }
        if ("server_votes".equals(placeholder)) {
            return serverStats == null || serverStats.getTotalVotes() == null ? zero() : String.valueOf(serverStats.getTotalVotes());
        }
        if ("votes_today".equals(placeholder)) {
            return serverStats == null ? zero() : String.valueOf(serverStats.getDayVotes());
        }
        if ("votes_month".equals(placeholder)) {
            return serverStats == null || serverStats.getMonthVotes() == null ? zero() : String.valueOf(serverStats.getMonthVotes());
        }
        if ("rewarded_today".equals(placeholder)) {
            return serverStats == null ? zero() : String.valueOf(serverStats.getRewardedDayVotes());
        }
        if ("api_status".equals(placeholder)) {
            return bukkitPlugin.getApiClient().apiStatus();
        }
        if ("cache_age".equals(placeholder)) {
            long ageMillis = bukkitPlugin.getApiClient().serverStatsCacheAgeMillis();
            return ageMillis < 0L ? notAvailable() : formatDuration(ageMillis);
        }

        String playerName = playerName(player);
        if (playerName == null) {
            return playerFallback(placeholder);
        }

        VoteReminderService voteReminderService = bukkitPlugin.getVoteReminderService();
        VoteStreakService voteStreakService = bukkitPlugin.getVoteStreakService();
        RewardService rewardService = bukkitPlugin.getRewardService();

        if ("player_last_vote".equals(placeholder)) {
            long lastVoteAt = voteReminderService == null ? 0L : voteReminderService.lastVoteAt(playerName);
            return lastVoteAt <= 0L ? notAvailable() : formatDate(lastVoteAt);
        }
        if ("player_last_vote_ago".equals(placeholder)) {
            long lastVoteAt = voteReminderService == null ? 0L : voteReminderService.lastVoteAt(playerName);
            return lastVoteAt <= 0L ? notAvailable() : formatDuration(System.currentTimeMillis() - lastVoteAt);
        }
        if ("player_can_vote".equals(placeholder)) {
            return voteReminderService == null ? booleanValue(false) : booleanValue(voteReminderService.canVote(playerName));
        }
        if ("player_next_vote_in".equals(placeholder)) {
            long nextVoteIn = voteReminderService == null ? -1L : voteReminderService.nextVoteInMillis(playerName);
            return nextVoteIn < 0L ? notAvailable() : formatDuration(nextVoteIn);
        }
        if ("player_streak".equals(placeholder)) {
            VoteStreakStore.Snapshot snapshot = voteStreakService == null ? null : voteStreakService.find(playerName);
            return snapshot == null ? zero() : String.valueOf(snapshot.getStreak());
        }
        if ("player_best_streak".equals(placeholder)) {
            VoteStreakStore.Snapshot snapshot = voteStreakService == null ? null : voteStreakService.find(playerName);
            return snapshot == null ? zero() : String.valueOf(snapshot.getBestStreak());
        }
        if ("player_next_streak_reward".equals(placeholder)) {
            VoteStreakStore.Snapshot snapshot = voteStreakService == null ? null : voteStreakService.find(playerName);
            int currentStreak = snapshot == null ? 0 : snapshot.getStreak();
            int nextReward = voteStreakService == null ? 0 : voteStreakService.nextRewardMilestone(currentStreak);
            return nextReward <= 0 ? notAvailable() : String.valueOf(nextReward);
        }
        if ("player_pending_reward".equals(placeholder)) {
            return rewardService == null ? booleanValue(false) : booleanValue(rewardService.hasPendingReward(playerName));
        }

        return null;
    }

    private String playerFallback(String placeholder) {
        if ("player_can_vote".equals(placeholder) || "player_pending_reward".equals(placeholder)) {
            return booleanValue(false);
        }
        if ("player_streak".equals(placeholder) || "player_best_streak".equals(placeholder)) {
            return zero();
        }
        return notAvailable();
    }

    private String playerName(OfflinePlayer player) {
        if (player == null || player.getName() == null || player.getName().trim().isEmpty()) {
            return null;
        }
        return player.getName();
    }

    private String formatDuration(long millis) {
        long safeMillis = Math.max(0L, millis);
        long days = TimeUnit.MILLISECONDS.toDays(safeMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(safeMillis) % 24L;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(safeMillis) % 60L;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(safeMillis) % 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat(dateTimeFormat(), Locale.ROOT).format(new Date(millis));
    }

    private String notAvailable() {
        return bukkitPlugin.getCSConfiguration().getString("placeholderapi.formats.unavailable", "placeholderapi.fallbacks.notAvailable", "N/A");
    }

    private String zero() {
        return bukkitPlugin.getCSConfiguration().getString("placeholderapi.formats.numberZero", "placeholderapi.fallbacks.zero", "0");
    }

    private String booleanValue(boolean value) {
        if (value) {
            return bukkitPlugin.getCSConfiguration().getString("placeholderapi.formats.booleanTrue", "true");
        }
        return bukkitPlugin.getCSConfiguration().getString("placeholderapi.formats.booleanFalse", "placeholderapi.fallbacks.false", "false");
    }

    private String dateTimeFormat() {
        return bukkitPlugin.getCSConfiguration().getString("placeholderapi.formats.dateTime", "dd/MM/yyyy HH:mm");
    }
}

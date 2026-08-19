package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RewardService {

    private static final List<String> DEFAULT_RECHECK_DELAYS = Arrays.asList("10", "30", "60");

    private final CSPlugin plugin;
    private final PlayerVoteStore playerVoteStore;
    private final CSScheduler scheduler;
    private final Supplier<String> currentDate;
    private final Supplier<Long> clock;
    private final Set<String> pendingRechecks = new HashSet<>();

    public RewardService(CSPlugin plugin) {
        this(plugin,
                playerVoteStore(plugin),
                plugin.getScheduler(),
                () -> new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()),
                System::currentTimeMillis);
    }

    RewardService(CSPlugin plugin, File dataPath, CSScheduler scheduler, Supplier<String> currentDate) {
        this(plugin, new PlayerVoteStore(dataFolder(dataPath), plugin), scheduler, currentDate, System::currentTimeMillis);
    }

    RewardService(CSPlugin plugin, File dataFolder, CSScheduler scheduler, Supplier<String> currentDate, Supplier<Long> clock) {
        this(plugin, new PlayerVoteStore(dataFolder, plugin), scheduler, currentDate, clock);
    }

    RewardService(CSPlugin plugin, PlayerVoteStore playerVoteStore, CSScheduler scheduler, Supplier<String> currentDate, Supplier<Long> clock) {
        this.plugin = plugin;
        this.playerVoteStore = playerVoteStore;
        this.scheduler = scheduler;
        this.currentDate = currentDate;
        this.clock = clock;
    }

    public void handleVoteResponse(String player, CSCommandSender sender, VoteResponse voteResponse) {
        PlayerReference reference = PlayerReference.from(sender);
        String playerName = reference.getName().isEmpty() ? player : reference.getName();
        String uuid = reference.getUniqueId();
        if (voteResponse == null || voteResponse.getStatus() == null) {
            sendMessage(reference, messages().voteError());
            return;
        }

        String web = voteResponse.getWeb();
        VoteStatus status = voteResponse.getStatus();

        switch (status) {
            case NOT_VOTED:
                if (sendAlreadyRewardedIfActive(sender)) {
                    return;
                }
                plugin.runSenderIfActive(sender, resolved -> resolved.sendNotVotedTodayLink(messages().notVotedTodayPrefix(), web));
                scheduleAutoReward(playerName, reference);
                break;
            case SUCCESS:
                handleSuccessResponse(playerName, uuid, sender, reference);
                break;
            case ALREADY_VOTED:
                recordVote(playerName, uuid);
                invalidateVoteCaches(playerName);
                sendMessage(reference, messages().voteAlreadyClaimed());
                break;
            case INVALID_kEY:
                sendMessage(reference, messages().invalidApiKey());
                break;
            default:
                sendMessage(reference, messages().voteError());
                break;
        }
    }

    public boolean deliverReward(String player, CSCommandSender sender, boolean notifyDuplicate) {
        return deliverReward(player, sender.getUniqueId(), PlayerReference.from(sender), notifyDuplicate);
    }

    public boolean deliverReward(CSCommandSender sender, boolean notifyDuplicate) {
        return deliverReward(sender.getName(), sender.getUniqueId(), PlayerReference.from(sender), notifyDuplicate);
    }

    public boolean hasPendingReward(String player) {
        synchronized (pendingRechecks) {
            return pendingRechecks.contains(rewardKey(player, currentDate.get()));
        }
    }

    public boolean hasActiveReward(CSCommandSender sender) {
        return playerVoteStore.hasRewardedOnDate(sender, currentDate.get()) && nextVoteInMillis(sender) > 0L;
    }

    public boolean hasCachedActiveReward(String player, String uuid) {
        return playerVoteStore.cachedRewardedOnDate(player, uuid, currentDate.get())
                && cachedNextVoteInMillis(player, uuid) > 0L;
    }

    public boolean sendAlreadyRewardedIfActive(CSCommandSender sender) {
        if (!hasCachedActiveReward(sender.getName(), sender.getUniqueId())) {
            return false;
        }
        sendAlreadyRewardedMessage(sender.getName(), sender.getUniqueId(), PlayerReference.from(sender));
        return true;
    }

    public void shutdown() {
    }

    private boolean deliverReward(String player, String uuid, PlayerReference reference, boolean notifyDuplicate) {
        String date = currentDate.get();
        PlayerVoteStore.MarkResult markResult = playerVoteStore.markRewarded(player, uuid, date, clock.get());
        if (markResult == PlayerVoteStore.MarkResult.FAILED) {
            sendMessage(reference, messages().rewardSaveFailed());
            return false;
        }

        if (markResult == PlayerVoteStore.MarkResult.DUPLICATE) {
            debug("Premio omitido para " + player + ": ya estaba marcado como entregado.");
            invalidateVoteCaches(player);
            if (notifyDuplicate) {
                sendAlreadyRewardedMessage(player, uuid, reference);
            }
            return false;
        }

        recordVote(player, uuid);
        recordStreak(player, uuid);
        invalidateVoteCaches(player);
        sendMessage(reference, messages().voteClaim());

        for (String command : plugin.getCSConfiguration().customCommandsList()) {
            String parsedCommand = PlayerPlaceholders.applyPlayer(command, player);
            scheduler.runGlobal(() -> plugin.dispatchCommand(parsedCommand));
        }

        plugin.getPluginMetrics().incrementRewardsDelivered();

        if (plugin.getCSConfiguration().getBoolean("broadcast.enabled", "broadcast.activado", true)) {
            plugin.broadcastMessage(PlayerPlaceholders.applyPlayer(
                    plugin.getCSConfiguration().getString("broadcast.message", "broadcast.mensajeBroadcast", ""),
                    player));
        }

        debug("Premio entregado a " + player + ".");
        return true;
    }

    private void scheduleAutoReward(String player, PlayerReference reference) {
        if (!enabled()) {
            debug("Auto-reward desactivado para " + player + ".");
            return;
        }

        if (maxAttempts() <= 0) {
            debug("Auto-reward sin intentos configurados para " + player + ".");
            return;
        }

        String pendingKey = rewardKey(player, currentDate.get());
        synchronized (pendingRechecks) {
            if (!pendingRechecks.add(pendingKey)) {
                debug("Ya hay rechecks pendientes para " + player + ".");
                return;
            }
        }

        scheduleAttempt(player, reference, 1, pendingKey);
    }

    private void scheduleAttempt(String player, PlayerReference reference, int attempt, String pendingKey) {
        long delay = delayForAttempt(attempt);
        debug("Recheck " + attempt + " para " + player + " en " + delay + " segundos.");

        scheduler.runAsyncLater(() -> {
            if (!plugin.isActive()) {
                finishRechecks(pendingKey);
                return;
            }
            plugin.getApiClient().invalidateVoteCache(player);
            plugin.getApiClient().validateVote(player).thenAccept(voteResponse -> {
                if (!plugin.isActive()) {
                    finishRechecks(pendingKey);
                    return;
                }
                handleRecheckResponse(player, reference, voteResponse, attempt, pendingKey);
            }).exceptionally(e -> {
                debug("Recheck " + attempt + " falló para " + player + ": " + e.getMessage());
                if (plugin.isActive() && attempt < maxAttempts()) {
                    scheduleAttempt(player, reference, attempt + 1, pendingKey);
                } else {
                    finishRechecks(pendingKey);
                }
                return null;
            });
        }, delay, TimeUnit.SECONDS);
    }

    private void handleRecheckResponse(String player, PlayerReference reference, VoteResponse voteResponse, int attempt, String pendingKey) {
        VoteStatus status = voteResponse == null ? null : voteResponse.getStatus();
        debug("Recheck " + attempt + " para " + player + " devolvió " + status + ".");

        if (status == VoteStatus.SUCCESS) {
            deliverReward(player, reference.getUniqueId(), reference, false);
            finishRechecks(pendingKey);
            return;
        }

        if (status == VoteStatus.ALREADY_VOTED) {
            recordVote(player, reference.getUniqueId());
            invalidateVoteCaches(player);
            finishRechecks(pendingKey);
            return;
        }

        if (attempt < maxAttempts()) {
            scheduleAttempt(player, reference, attempt + 1, pendingKey);
            return;
        }

        finishRechecks(pendingKey);
    }

    private void finishRechecks(String pendingKey) {
        synchronized (pendingRechecks) {
            pendingRechecks.remove(pendingKey);
        }
    }

    private void handleSuccessResponse(String playerName, String uuid, CSCommandSender sender, PlayerReference reference) {
        if (!shouldConfirmSuccess(playerName, uuid)) {
            deliverReward(playerName, uuid, reference, true);
            return;
        }

        plugin.getApiClient().invalidateVoteCache(playerName);
        plugin.getApiClient().validateVote(playerName).thenAccept(confirmed -> {
            if (!plugin.isActive()) {
                return;
            }
            plugin.runSenderIfActive(sender, resolved -> applyConfirmedSuccess(playerName, uuid, resolved, confirmed));
        }).exceptionally(error -> {
            debug("Revalidación de SUCCESS falló para " + playerName + ": " + error.getMessage());
            if (plugin.isActive()) {
                plugin.runSenderIfActive(sender, resolved ->
                        deliverReward(playerName, uuid, PlayerReference.from(resolved), true));
            }
            return null;
        });
    }

    private void applyConfirmedSuccess(String playerName, String uuid, CSCommandSender sender, VoteResponse confirmed) {
        PlayerReference reference = PlayerReference.from(sender);
        if (confirmed == null || confirmed.getStatus() == null) {
            sendMessage(reference, messages().voteError());
            return;
        }

        String web = confirmed.getWeb();
        switch (confirmed.getStatus()) {
            case NOT_VOTED:
                if (sendAlreadyRewardedIfActive(sender)) {
                    return;
                }
                sender.sendNotVotedTodayLink(messages().notVotedTodayPrefix(), web);
                scheduleAutoReward(playerName, reference);
                break;
            case SUCCESS:
                deliverReward(playerName, uuid, reference, true);
                break;
            case ALREADY_VOTED:
                recordVote(playerName, uuid);
                invalidateVoteCaches(playerName);
                sendMessage(reference, messages().voteAlreadyClaimed());
                break;
            default:
                sendMessage(reference, messages().voteError());
                break;
        }
    }

    private boolean shouldConfirmSuccess(String player, String uuid) {
        long lastVoteAt = playerVoteStore.cachedLastVoteAt(player, uuid);
        if (lastVoteAt <= 0L) {
            lastVoteAt = playerVoteStore.lastVoteAt(player, uuid);
        }
        if (lastVoteAt <= 0L) {
            return false;
        }

        long nextVoteIn = cachedNextVoteInMillis(player, uuid);
        if (nextVoteIn < 0L) {
            nextVoteIn = nextVoteInMillis(player, uuid);
        }
        if (nextVoteIn > 0L) {
            return false;
        }

        String lastRewardDate = playerVoteStore.cachedLastRewardDate(player, uuid);
        if (lastRewardDate == null || lastRewardDate.isEmpty()) {
            lastRewardDate = playerVoteStore.lastRewardDate(player, uuid);
        }
        return !lastRewardDate.isEmpty() && !currentDate.get().equals(lastRewardDate);
    }

    private void recordVote(String player, String uuid) {
        VoteReminderService voteReminderService = plugin.getVoteReminderService();
        if (voteReminderService != null) {
            voteReminderService.recordVote(player, uuid);
        }
    }

    private void recordStreak(String player, String uuid) {
        VoteStreakService voteStreakService = plugin.getVoteStreakService();
        if (voteStreakService != null) {
            voteStreakService.recordVote(player, uuid);
        }
    }

    private void invalidateVoteCaches(String player) {
        plugin.getApiClient().invalidateVoteCache(player);
        plugin.getApiClient().invalidateServerStatsCache();
    }

    private boolean enabled() {
        return plugin.getCSConfiguration().getBoolean("autoReward.enabled", true);
    }

    private int maxAttempts() {
        return Math.max(0, plugin.getCSConfiguration().getInt("autoReward.maxAttempts", 3));
    }

    private long delayForAttempt(int attempt) {
        List<String> delays = plugin.getCSConfiguration().getStringList("autoReward.recheckDelaysSeconds", DEFAULT_RECHECK_DELAYS);
        if (delays.isEmpty()) {
            delays = DEFAULT_RECHECK_DELAYS;
        }

        int index = Math.min(Math.max(0, attempt - 1), delays.size() - 1);
        try {
            return Math.max(0L, Long.parseLong(delays.get(index)));
        } catch (NumberFormatException e) {
            return Long.parseLong(DEFAULT_RECHECK_DELAYS.get(Math.min(index, DEFAULT_RECHECK_DELAYS.size() - 1)));
        }
    }

    private long nextVoteInMillis(CSCommandSender sender) {
        long lastVoteAt = playerVoteStore.lastVoteAt(sender);
        if (lastVoteAt <= 0L) {
            return -1L;
        }
        return Math.max(0L, VoteReminderService.VOTE_COOLDOWN_MILLIS - (clock.get() - lastVoteAt));
    }

    private long nextVoteInMillis(String player, String uuid) {
        long lastVoteAt = playerVoteStore.lastVoteAt(player, uuid);
        if (lastVoteAt <= 0L) {
            return -1L;
        }
        return Math.max(0L, VoteReminderService.VOTE_COOLDOWN_MILLIS - (clock.get() - lastVoteAt));
    }

    private long cachedNextVoteInMillis(String player, String uuid) {
        long lastVoteAt = playerVoteStore.cachedLastVoteAt(player, uuid);
        if (lastVoteAt <= 0L) {
            return -1L;
        }
        return Math.max(0L, VoteReminderService.VOTE_COOLDOWN_MILLIS - (clock.get() - lastVoteAt));
    }

    private void sendAlreadyRewardedMessage(String player, String uuid, PlayerReference reference) {
        long nextVoteIn = cachedNextVoteInMillis(player, uuid);
        if (nextVoteIn < 0L) {
            nextVoteIn = nextVoteInMillis(player, uuid);
        }
        long timeLeft = nextVoteIn;
        sendMessage(reference, messages().alreadyRewarded(VoteTimeFormatter.formatDuration(timeLeft)));
    }

    private PluginMessages messages() {
        return plugin.getPluginMessages();
    }

    private String rewardKey(String player, String date) {
        return date + "." + (player == null ? "" : player).toLowerCase(Locale.ROOT);
    }

    private static File dataFolder(File dataPath) {
        return dataPath.getName().endsWith(".properties") ? dataPath.getParentFile() : dataPath;
    }

    private static PlayerVoteStore playerVoteStore(CSPlugin plugin) {
        PlayerVoteStore store = plugin.getPlayerVoteStore();
        return store == null ? new PlayerVoteStore(plugin.getPluginDataFolder(), plugin) : store;
    }

    private void debug(String message) {
        if (plugin.getCSConfiguration().getBoolean("autoReward.debug", false) || plugin.isDebug()) {
            plugin.log("[AutoReward] " + message);
        }
    }

    private void sendMessage(PlayerReference reference, String message) {
        plugin.runPlayerIfActive(reference, sender -> sender.sendMessageWithTag(message));
    }
}

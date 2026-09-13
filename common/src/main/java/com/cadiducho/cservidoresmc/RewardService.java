package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.http.HttpException;
import com.cadiducho.cservidoresmc.model.PendingVote;
import com.cadiducho.cservidoresmc.model.PendingVotesResponse;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;
import com.cadiducho.cservidoresmc.util.IpSanitizer;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Delivers vote rewards using API v3: pending votes then ack.
 */
public class RewardService {

    private static final List<String> DEFAULT_RECHECK_DELAYS = Arrays.asList("10", "30", "60");
    /** Website link shown when the player still needs to vote. Hardcoded on purpose. */
    public static final String VOTE_URL = "https://www.40servidoresmc.es/";

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
                () -> VoteCooldownRules.utcDateString(System.currentTimeMillis()),
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

    public void handlePendingVotes(String player, CSCommandSender sender, PendingVotesResponse pending, VoteTrace trace) {
        PlayerReference reference = PlayerReference.from(sender);
        String playerName = reference.getName().isEmpty() ? player : reference.getName();
        String uuid = reference.getUniqueId();
        VoteTrace voteTrace = trace == null ? VoteTrace.noop() : trace;

        if (pending == null) {
            voteTrace.error("pending response null");
            sendMessage(reference, messages().voteError());
            voteTrace.done();
            return;
        }

        List<PendingVote> votes = pending.safePendingVotes();
        if (!votes.isEmpty()) {
            voteTrace.branch("deliver");
            deliverAndAck(playerName, uuid, reference, votes, voteTrace);
            return;
        }

        if (pending.isPuedeVotarYa()) {
            voteTrace.branch("show_link");
            plugin.runSenderIfActive(sender, resolved ->
                    resolved.sendNotVotedTodayLink(messages().notVotedTodayPrefix(), VOTE_URL));
            scheduleAutoReward(playerName, reference, voteTrace);
            voteTrace.done();
            return;
        }

        voteTrace.branch("already_rewarded");
        String formatted = formatSiguienteVoto(pending.getSiguienteVoto());
        sendMessage(reference, messages().alreadyRewarded(formatted));
        voteTrace.done();
    }

    public void handleVoteApiFailure(CSCommandSender sender, Throwable error, VoteTrace trace) {
        VoteTrace voteTrace = trace == null ? VoteTrace.noop() : trace;
        PlayerReference reference = PlayerReference.from(sender);
        Throwable root = unwrap(error);
        if (root instanceof HttpException && ((HttpException) root).getStatusCode() == 403) {
            voteTrace.branch("invalid_key");
            sendMessage(reference, messages().invalidApiKey());
            voteTrace.done();
            return;
        }
        voteTrace.error(root == null ? "unknown" : root.getMessage());
        sendMessage(reference, messages().apiException());
        voteTrace.done();
    }

    public boolean deliverReward(String player, CSCommandSender sender, boolean notifyDuplicate) {
        return deliverReward(player, sender.getUniqueId(), PlayerReference.from(sender), notifyDuplicate, VoteTrace.noop()).ok;
    }

    public boolean deliverReward(CSCommandSender sender, boolean notifyDuplicate) {
        return deliverReward(sender.getName(), sender.getUniqueId(), PlayerReference.from(sender), notifyDuplicate, VoteTrace.noop()).ok;
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

    /**
     * Local early exit only when the player already has an active local reward window
     * and there are no pending acks waiting to be retried.
     */
    public boolean sendAlreadyRewardedIfActive(CSCommandSender sender, VoteTrace trace) {
        VoteTrace voteTrace = trace == null ? VoteTrace.noop() : trace;
        List<Long> pendingAcks = plugin.getApiClient().peekPendingAcks(sender.getName());
        if (!pendingAcks.isEmpty()) {
            return false;
        }
        if (!hasCachedActiveReward(sender.getName(), sender.getUniqueId())) {
            return false;
        }
        voteTrace.earlyExit("local_active_reward");
        sendAlreadyRewardedMessage(sender.getName(), sender.getUniqueId(), PlayerReference.from(sender));
        voteTrace.done();
        return true;
    }

    public boolean sendAlreadyRewardedIfActive(CSCommandSender sender) {
        return sendAlreadyRewardedIfActive(sender, VoteTrace.noop());
    }

    public void shutdown() {
    }

    private void deliverAndAck(String player, String uuid, PlayerReference reference,
                               List<PendingVote> votes, VoteTrace voteTrace) {
        List<Long> ids = new ArrayList<>();
        for (PendingVote vote : votes) {
            ids.add(vote.getId());
        }

        scheduler.runGlobal(() -> {
            if (!plugin.isActive()) {
                voteTrace.aborted("plugin_inactive");
                return;
            }

            boolean online = plugin.isPlayerOnline(player);
            if (!online) {
                voteTrace.delivery(false, 0, 0, false, "OFFLINE");
                plugin.getApiClient().sendAck(ids, player, false, "", voteTrace)
                        .whenComplete((ack, error) -> {
                            if (error != null) {
                                voteTrace.error("ack_failed_after_offline: " + error.getMessage());
                            }
                            sendMessage(reference, messages().voteDeliveryFailed());
                            voteTrace.done();
                        });
                return;
            }

            DeliveryResult delivery = deliverReward(player, uuid, reference, true, voteTrace);
            boolean delivered = delivery.ok;
            String userIp = IpSanitizer.forAck(plugin.getPlayerIp(player));

            if (!delivered) {
                voteTrace.delivery(false, delivery.commandsOk, delivery.commandsTotal, true, delivery.markResult);
                plugin.getApiClient().sendAck(ids, player, false, userIp, voteTrace)
                        .whenComplete((ack, error) -> {
                            if (error != null) {
                                voteTrace.error("ack_failed_after_delivery_fail: " + error.getMessage());
                            }
                            sendMessage(reference, messages().voteDeliveryFailed());
                            voteTrace.done();
                        });
                return;
            }

            voteTrace.delivery(true, delivery.commandsOk, delivery.commandsTotal, true, delivery.markResult);
            plugin.getApiClient().sendAck(ids, player, true, userIp, voteTrace)
                    .whenComplete((ack, error) -> {
                        if (error != null || ack == null) {
                            plugin.getApiClient().addPendingAck(player, ids);
                            sendMessage(reference, messages().voteAckFailed());
                            voteTrace.done();
                            return;
                        }
                        voteTrace.done();
                    });
        });
    }

    private DeliveryResult deliverReward(String player, String uuid, PlayerReference reference,
                                         boolean notifyDuplicate, VoteTrace voteTrace) {
        String date = currentDate.get();
        PlayerVoteStore.MarkResult markResult = playerVoteStore.markRewarded(player, uuid, date, clock.get());
        if (markResult == PlayerVoteStore.MarkResult.FAILED) {
            sendMessage(reference, messages().rewardSaveFailed());
            return DeliveryResult.failed("FAILED", 0, 0);
        }

        if (markResult == PlayerVoteStore.MarkResult.DUPLICATE) {
            debug("Premio omitido para " + player + ": ya estaba marcado como entregado.");
            plugin.getApiClient().invalidateServerStatsCache();
            if (notifyDuplicate) {
                sendAlreadyRewardedMessage(player, uuid, reference);
            }
            return DeliveryResult.failed("DUPLICATE", 0, 0);
        }

        recordVote(player, uuid);
        recordStreak(player, uuid);
        plugin.getApiClient().invalidateServerStatsCache();
        sendMessage(reference, messages().voteClaim());

        List<String> commands = plugin.getCSConfiguration().customCommandsList();
        int ok = 0;
        for (String command : commands) {
            String parsedCommand = PlayerPlaceholders.applyPlayer(command, player);
            if (plugin.dispatchCommandResult(parsedCommand)) {
                ok++;
            }
        }

        plugin.getPluginMetrics().incrementRewardsDelivered();

        if (plugin.getCSConfiguration().getBoolean("broadcast.enabled", "broadcast.activado", true)) {
            plugin.broadcastMessage(PlayerPlaceholders.applyPlayer(
                    plugin.getCSConfiguration().getString("broadcast.message", "broadcast.mensajeBroadcast", ""),
                    player));
        }

        debug("Premio entregado a " + player + ".");
        boolean allOk = commands.isEmpty() || ok == commands.size();
        return new DeliveryResult(allOk, "NEW", ok, commands.size());
    }

    private void scheduleAutoReward(String player, PlayerReference reference, VoteTrace parentTrace) {
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

        scheduleAttempt(player, reference, 1, pendingKey, parentTrace);
    }

    private void scheduleAttempt(String player, PlayerReference reference, int attempt, String pendingKey, VoteTrace parentTrace) {
        long delay = delayForAttempt(attempt);
        debug("Recheck " + attempt + " para " + player + " en " + delay + " segundos.");
        if (parentTrace != null && parentTrace.isEnabled()) {
            parentTrace.recheck(attempt, delay);
        }

        scheduler.runAsyncLater(() -> {
            if (!plugin.isActive()) {
                finishRechecks(pendingKey);
                return;
            }
            VoteTrace recheckTrace = parentTrace != null && parentTrace.isEnabled() ? parentTrace : VoteTrace.noop();
            plugin.getApiClient().fetchPendingVotes(player, recheckTrace).thenAccept(pending -> {
                if (!plugin.isActive()) {
                    finishRechecks(pendingKey);
                    return;
                }
                handleRecheckPending(player, reference, pending, attempt, pendingKey, recheckTrace);
            }).exceptionally(e -> {
                debug("Recheck " + attempt + " falló para " + player + ": " + e.getMessage());
                if (plugin.isActive() && attempt < maxAttempts()) {
                    scheduleAttempt(player, reference, attempt + 1, pendingKey, parentTrace);
                } else {
                    finishRechecks(pendingKey);
                }
                return null;
            });
        }, delay, TimeUnit.SECONDS);
    }

    private void handleRecheckPending(String player, PlayerReference reference, PendingVotesResponse pending,
                                      int attempt, String pendingKey, VoteTrace voteTrace) {
        debug("Recheck " + attempt + " para " + player + " devolvió pending="
                + (pending == null ? "null" : pending.safePendingVotes().size()));

        if (pending != null && pending.hasPendingVotes()) {
            deliverAndAck(player, reference.getUniqueId(), reference, pending.safePendingVotes(), voteTrace);
            finishRechecks(pendingKey);
            return;
        }

        if (pending != null && !pending.isPuedeVotarYa()) {
            finishRechecks(pendingKey);
            return;
        }

        if (attempt < maxAttempts()) {
            scheduleAttempt(player, reference, attempt + 1, pendingKey, voteTrace);
            return;
        }

        finishRechecks(pendingKey);
    }

    private void finishRechecks(String pendingKey) {
        synchronized (pendingRechecks) {
            pendingRechecks.remove(pendingKey);
        }
    }

    private void recordVote(String player, String uuid) {
        long votedAt = clock.get();
        playerVoteStore.recordVote(player, uuid, votedAt);
        VoteReminderService voteReminderService = plugin.getVoteReminderService();
        if (voteReminderService != null) {
            voteReminderService.recordVote(player, uuid, votedAt);
        }
    }

    private void recordStreak(String player, String uuid) {
        VoteStreakService voteStreakService = plugin.getVoteStreakService();
        if (voteStreakService != null) {
            voteStreakService.recordVote(player, uuid);
        }
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
        return VoteCooldownRules.nextVoteInMillis(playerVoteStore.lastVoteAt(sender), clock.get());
    }

    private long nextVoteInMillis(String player, String uuid) {
        return VoteCooldownRules.nextVoteInMillis(playerVoteStore.lastVoteAt(player, uuid), clock.get());
    }

    private long cachedNextVoteInMillis(String player, String uuid) {
        return VoteCooldownRules.nextVoteInMillis(playerVoteStore.cachedLastVoteAt(player, uuid), clock.get());
    }

    private void sendAlreadyRewardedMessage(String player, String uuid, PlayerReference reference) {
        long nextVoteIn = cachedNextVoteInMillis(player, uuid);
        if (nextVoteIn < 0L) {
            nextVoteIn = nextVoteInMillis(player, uuid);
        }
        long timeLeft = Math.max(0L, nextVoteIn);
        sendMessage(reference, messages().alreadyRewarded(VoteTimeFormatter.formatDuration(timeLeft)));
    }

    String formatSiguienteVoto(String siguienteVoto) {
        if (siguienteVoto == null || siguienteVoto.trim().isEmpty()) {
            return VoteTimeFormatter.formatDuration(0L);
        }
        try {
            long target = OffsetDateTime.parse(siguienteVoto.trim()).toInstant().toEpochMilli();
            long remaining = Math.max(0L, target - clock.get());
            return VoteTimeFormatter.formatDuration(remaining);
        } catch (DateTimeParseException ignored) {
            try {
                long target = Instant.parse(siguienteVoto.trim()).toEpochMilli();
                long remaining = Math.max(0L, target - clock.get());
                return VoteTimeFormatter.formatDuration(remaining);
            } catch (DateTimeParseException ignoredAgain) {
                return siguienteVoto;
            }
        }
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

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            if (current.getCause() instanceof HttpException) {
                return current.getCause();
            }
            current = current.getCause();
        }
        return error;
    }

    private void debug(String message) {
        if (plugin.isDebug()) {
            plugin.log("[AutoReward] " + message);
        }
    }

    private void sendMessage(PlayerReference reference, String message) {
        plugin.runPlayerIfActive(reference, sender -> sender.sendMessageWithTag(message));
    }

    private static final class DeliveryResult {
        private final boolean ok;
        private final String markResult;
        private final int commandsOk;
        private final int commandsTotal;

        private DeliveryResult(boolean ok, String markResult, int commandsOk, int commandsTotal) {
            this.ok = ok;
            this.markResult = markResult;
            this.commandsOk = commandsOk;
            this.commandsTotal = commandsTotal;
        }

        private static DeliveryResult failed(String markResult, int commandsOk, int commandsTotal) {
            return new DeliveryResult(false, markResult, commandsOk, commandsTotal);
        }
    }
}

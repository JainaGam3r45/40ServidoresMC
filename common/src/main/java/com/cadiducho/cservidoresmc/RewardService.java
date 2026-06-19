package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RewardService {

    private static final List<String> DEFAULT_RECHECK_DELAYS = Arrays.asList("10", "30", "60");
    private static final String DEFAULT_ALREADY_REWARDED_MESSAGE = "&aYa has votado y recibido tu recompensa. Podrás volver a votar en &e%time%&a.";

    private final CSPlugin plugin;
    private final PlayerVoteStore playerVoteStore;
    private final RewardScheduler scheduler;
    private final Supplier<String> currentDate;
    private final Supplier<Long> clock;
    private final Set<String> pendingRechecks = new HashSet<>();

    public RewardService(CSPlugin plugin) {
        this(plugin,
                plugin.getPluginDataFolder(),
                new ScheduledRewardScheduler(),
                () -> new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()),
                System::currentTimeMillis);
    }

    RewardService(CSPlugin plugin, File dataPath, RewardScheduler scheduler, Supplier<String> currentDate) {
        this(plugin, dataFolder(dataPath), scheduler, currentDate, System::currentTimeMillis);
    }

    RewardService(CSPlugin plugin, File dataFolder, RewardScheduler scheduler, Supplier<String> currentDate, Supplier<Long> clock) {
        this.plugin = plugin;
        this.playerVoteStore = new PlayerVoteStore(dataFolder, plugin);
        this.scheduler = scheduler;
        this.currentDate = currentDate;
        this.clock = clock;
    }

    public void handleVoteResponse(String player, CSCommandSender sender, VoteResponse voteResponse) {
        if (voteResponse == null || voteResponse.getStatus() == null) {
            sender.sendMessageWithTag("&7Ha ocurrido un error. Prueba más tarde o avisa a un adminsitrador");
            return;
        }

        String web = voteResponse.getWeb();
        VoteStatus status = voteResponse.getStatus();

        switch (status) {
            case NOT_VOTED:
                if (sendAlreadyRewardedIfActive(sender)) {
                    return;
                }
                sender.sendNotVotedTodayLink("&6No has votado hoy! Puedes hacerlo en &a ", web);
                scheduleAutoReward(player, sender);
                break;
            case SUCCESS:
                deliverReward(sender, true);
                break;
            case ALREADY_VOTED:
                markRewarded(sender);
                recordVote(sender);
                invalidateVoteCaches(player);
                sender.sendMessageWithTag("&aGracias por votar, pero ya has obtenido tu premio!");
                break;
            case INVALID_kEY:
                sender.sendMessageWithTag("&cClave incorrecta. Entra en &bhttps://40servidoresmc.es/miservidor.php &cy cambia esta.");
                break;
            default:
                sender.sendMessageWithTag("&7Ha ocurrido un error. Prueba más tarde o avisa a un adminsitrador");
                break;
        }
    }

    public boolean deliverReward(String player, CSCommandSender sender, boolean notifyDuplicate) {
        return deliverReward(sender, notifyDuplicate);
    }

    public boolean deliverReward(CSCommandSender sender, boolean notifyDuplicate) {
        String player = sender.getName();
        String date = currentDate.get();
        PlayerVoteStore.MarkResult markResult = playerVoteStore.markRewarded(sender, date, clock.get());
        if (markResult == PlayerVoteStore.MarkResult.FAILED) {
            sender.sendMessageWithTag("&cNo se pudo registrar tu voto premiado. Avisa a un administrador.");
            return false;
        }

        if (markResult == PlayerVoteStore.MarkResult.DUPLICATE) {
            debug("Premio omitido para " + player + ": ya estaba marcado como entregado.");
            invalidateVoteCaches(player);
            if (notifyDuplicate) {
                sendAlreadyRewardedMessage(sender);
            }
            return false;
        }

        recordVote(sender);
        recordStreak(sender);
        invalidateVoteCaches(player);
        sender.sendMessageWithTag(plugin.getCSConfiguration().getString("messages.voteClaim", "mensaje", ""));

        for (String command : plugin.getCSConfiguration().customCommandsList()) {
            String parsedCommand = PlayerPlaceholders.applyPlayer(command, player);
            plugin.runSync(() -> plugin.dispatchCommand(parsedCommand));
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

    public boolean hasPendingReward(String player) {
        synchronized (pendingRechecks) {
            return pendingRechecks.contains(rewardKey(player, currentDate.get()));
        }
    }

    public boolean hasActiveReward(CSCommandSender sender) {
        return playerVoteStore.hasRewardedOnDate(sender, currentDate.get()) && nextVoteInMillis(sender) > 0L;
    }

    public boolean sendAlreadyRewardedIfActive(CSCommandSender sender) {
        if (!hasActiveReward(sender)) {
            return false;
        }
        sendAlreadyRewardedMessage(sender);
        return true;
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private void scheduleAutoReward(String player, CSCommandSender sender) {
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

        scheduleAttempt(player, sender, 1, pendingKey);
    }

    private void scheduleAttempt(String player, CSCommandSender sender, int attempt, String pendingKey) {
        long delay = delayForAttempt(attempt);
        debug("Recheck " + attempt + " para " + player + " en " + delay + " segundos.");

        scheduler.schedule(() -> {
            plugin.getApiClient().invalidateVoteCache(player);
            plugin.getApiClient().validateVote(player).thenAccept(voteResponse -> {
                handleRecheckResponse(player, sender, voteResponse, attempt, pendingKey);
            }).exceptionally(e -> {
                debug("Recheck " + attempt + " falló para " + player + ": " + e.getMessage());
                if (attempt < maxAttempts()) {
                    scheduleAttempt(player, sender, attempt + 1, pendingKey);
                } else {
                    finishRechecks(pendingKey);
                }
                return null;
            });
        }, delay);
    }

    private void handleRecheckResponse(String player, CSCommandSender sender, VoteResponse voteResponse, int attempt, String pendingKey) {
        VoteStatus status = voteResponse == null ? null : voteResponse.getStatus();
        debug("Recheck " + attempt + " para " + player + " devolvió " + status + ".");

        if (status == VoteStatus.SUCCESS) {
            deliverReward(sender, false);
            finishRechecks(pendingKey);
            return;
        }

        if (status == VoteStatus.ALREADY_VOTED) {
            markRewarded(sender);
            recordVote(sender);
            invalidateVoteCaches(player);
            finishRechecks(pendingKey);
            return;
        }

        if (attempt < maxAttempts()) {
            scheduleAttempt(player, sender, attempt + 1, pendingKey);
            return;
        }

        finishRechecks(pendingKey);
    }

    private void finishRechecks(String pendingKey) {
        synchronized (pendingRechecks) {
            pendingRechecks.remove(pendingKey);
        }
    }

    private void markRewarded(CSCommandSender sender) {
        playerVoteStore.markRewarded(sender, currentDate.get(), clock.get());
    }

    private void recordVote(CSCommandSender sender) {
        VoteReminderService voteReminderService = plugin.getVoteReminderService();
        if (voteReminderService != null) {
            voteReminderService.recordVote(sender);
        }
    }

    private void recordStreak(CSCommandSender sender) {
        VoteStreakService voteStreakService = plugin.getVoteStreakService();
        if (voteStreakService != null) {
            voteStreakService.recordVote(sender);
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

    private void sendAlreadyRewardedMessage(CSCommandSender sender) {
        String message = plugin.getCSConfiguration().getString("messages.alreadyRewarded", "alreadyRewardedMessage", DEFAULT_ALREADY_REWARDED_MESSAGE);
        sender.sendMessageWithTag(message.replace("%time%", VoteTimeFormatter.formatDuration(nextVoteInMillis(sender))));
    }

    private String rewardKey(String player, String date) {
        return date + "." + (player == null ? "" : player).toLowerCase(Locale.ROOT);
    }

    private static File dataFolder(File dataPath) {
        return dataPath.getName().endsWith(".properties") ? dataPath.getParentFile() : dataPath;
    }

    private void debug(String message) {
        if (plugin.getCSConfiguration().getBoolean("autoReward.debug", false) || plugin.isDebug()) {
            plugin.log("[AutoReward] " + message);
        }
    }

    interface RewardScheduler {
        void schedule(Runnable task, long delaySeconds);

        void shutdown();
    }

    private static class ScheduledRewardScheduler implements RewardScheduler {

        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "40servidoresmc-auto-reward");
                thread.setDaemon(true);
                return thread;
            }
        });

        @Override
        public void schedule(Runnable task, long delaySeconds) {
            executor.schedule(task, delaySeconds, TimeUnit.SECONDS);
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }
    }
}

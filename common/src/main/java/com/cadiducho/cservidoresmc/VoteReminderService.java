package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class VoteReminderService {

    static final long VOTE_COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final int DEFAULT_CHECK_INTERVAL_SECONDS = 300;
    private static final int MIN_CHECK_INTERVAL_SECONDS = 60;
    private static final String DEFAULT_MESSAGE = "&aYa puedes volver a votar en 40ServidoresMC. Usa &2/voto40&a para recibir tu premio.";

    private final CSPlugin plugin;
    private final VoteReminderStore store;
    private final ReminderScheduler scheduler;
    private final Supplier<Long> clock;

    public VoteReminderService(CSPlugin plugin) {
        this(plugin,
                new File(plugin.getPluginDataFolder(), "vote-reminders.properties"),
                new ScheduledReminderScheduler(),
                System::currentTimeMillis);
    }

    VoteReminderService(CSPlugin plugin, File reminderFile, ReminderScheduler scheduler, Supplier<Long> clock) {
        this.plugin = plugin;
        this.store = new VoteReminderStore(reminderFile, plugin);
        this.scheduler = scheduler;
        this.clock = clock;
    }

    public void start() {
        if (!enabled()) {
            debug("Recordatorios de voto desactivados.");
            return;
        }

        scheduler.scheduleAtFixedRate(this::checkReminders, checkIntervalSeconds());
    }

    public void recordVote(String player) {
        recordVote(player, clock.get());
    }

    void recordVote(String player, long votedAt) {
        if (!store.recordVote(player, votedAt)) {
            debug("No se pudo guardar el último voto de " + player + ".");
        }
    }

    void checkReminders() {
        if (!enabled()) {
            return;
        }

        for (CSCommandSender sender : onlinePlayersByName().values()) {
            remindIfReady(sender);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private void remindIfReady(CSCommandSender sender) {
        String player = sender.getName();
        long lastVoteAt = store.lastVoteAt(player);
        if (lastVoteAt <= 0L) {
            return;
        }

        long now = clock.get();
        if (now - lastVoteAt < VOTE_COOLDOWN_MILLIS) {
            return;
        }

        if (store.wasRemindedFor(player, lastVoteAt)) {
            return;
        }

        if (!store.markReminded(player, lastVoteAt)) {
            debug("No se pudo marcar el recordatorio de " + player + ".");
            return;
        }

        plugin.runSync(() -> sender.sendMessageWithTag(message()));
    }

    private Map<String, CSCommandSender> onlinePlayersByName() {
        Map<String, CSCommandSender> players = new HashMap<>();
        List<CSCommandSender> onlinePlayers = plugin.getOnlinePlayers();
        for (CSCommandSender player : onlinePlayers) {
            players.put(normalizePlayer(player.getName()), player);
        }
        return players;
    }

    private boolean enabled() {
        return plugin.getCSConfiguration().getBoolean("voteReminder.enabled", true);
    }

    private String message() {
        return plugin.getCSConfiguration().getString("voteReminder.message", DEFAULT_MESSAGE);
    }

    private int checkIntervalSeconds() {
        int configured = plugin.getCSConfiguration().getInt("voteReminder.checkIntervalSeconds", DEFAULT_CHECK_INTERVAL_SECONDS);
        return Math.max(MIN_CHECK_INTERVAL_SECONDS, configured);
    }

    private String normalizePlayer(String player) {
        return (player == null ? "" : player).toLowerCase(Locale.ROOT);
    }

    private void debug(String message) {
        if (plugin.isDebug()) {
            plugin.log("[VoteReminder] " + message);
        }
    }

    interface ReminderScheduler {
        void scheduleAtFixedRate(Runnable task, long intervalSeconds);

        void shutdown();
    }

    private static class ScheduledReminderScheduler implements ReminderScheduler {

        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "40servidoresmc-vote-reminder");
                thread.setDaemon(true);
                return thread;
            }
        });

        @Override
        public void scheduleAtFixedRate(Runnable task, long intervalSeconds) {
            executor.scheduleAtFixedRate(task, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }
    }
}

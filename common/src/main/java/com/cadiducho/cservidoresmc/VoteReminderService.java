package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.CancellableTask;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class VoteReminderService {

    static final long VOTE_COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final int DEFAULT_CHECK_INTERVAL_SECONDS = 300;
    private static final int MIN_CHECK_INTERVAL_SECONDS = 60;
    private static final String DEFAULT_MESSAGE = "&aYa puedes volver a votar en 40ServidoresMC. Usa &2/voto40&a para recibir tu premio.";

    private final CSPlugin plugin;
    private final PlayerVoteStore store;
    private final CSScheduler scheduler;
    private final Supplier<Long> clock;
    private CancellableTask reminderTask;

    public VoteReminderService(CSPlugin plugin) {
        this(plugin,
                playerVoteStore(plugin),
                plugin.getScheduler(),
                System::currentTimeMillis);
    }

    VoteReminderService(CSPlugin plugin, File dataPath, CSScheduler scheduler, Supplier<Long> clock) {
        this(plugin, new PlayerVoteStore(dataFolder(dataPath), plugin), scheduler, clock);
    }

    VoteReminderService(CSPlugin plugin, PlayerVoteStore store, CSScheduler scheduler, Supplier<Long> clock) {
        this.plugin = plugin;
        this.store = store;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    public void start() {
        if (!enabled()) {
            debug("Recordatorios de voto desactivados.");
            return;
        }

        long interval = checkIntervalSeconds();
        reminderTask = scheduler.runAsyncRepeating(this::checkReminders, interval, interval, TimeUnit.SECONDS);
    }

    public void recordVote(String player) {
        recordVote(player, clock.get());
    }

    public void recordVote(String player, String uuid) {
        recordVote(player, uuid, clock.get());
    }

    public void recordVote(CSCommandSender sender) {
        recordVote(sender, clock.get());
    }

    public long lastVoteAt(String player) {
        return store.lastVoteAt(player);
    }

    public long cachedLastVoteAt(String player, String uuid) {
        return store.cachedLastVoteAt(player, uuid);
    }

    public boolean canVote(String player) {
        long lastVoteAt = lastVoteAt(player);
        return lastVoteAt > 0L && nextVoteInMillis(player) <= 0L;
    }

    public long nextVoteInMillis(String player) {
        long lastVoteAt = lastVoteAt(player);
        if (lastVoteAt <= 0L) {
            return -1L;
        }

        return Math.max(0L, VOTE_COOLDOWN_MILLIS - (clock.get() - lastVoteAt));
    }

    public long cachedNextVoteInMillis(String player, String uuid) {
        long lastVoteAt = cachedLastVoteAt(player, uuid);
        if (lastVoteAt <= 0L) {
            return -1L;
        }

        return Math.max(0L, VOTE_COOLDOWN_MILLIS - (clock.get() - lastVoteAt));
    }

    public boolean cachedCanVote(String player, String uuid) {
        long lastVoteAt = cachedLastVoteAt(player, uuid);
        return lastVoteAt > 0L && cachedNextVoteInMillis(player, uuid) <= 0L;
    }

    public void requestLoad(String player, String uuid) {
        store.requestLoad(player, uuid);
    }

    void recordVote(String player, long votedAt) {
        if (!store.recordVote(player, votedAt)) {
            debug("No se pudo guardar el último voto de " + player + ".");
        }
    }

    void recordVote(String player, String uuid, long votedAt) {
        if (!store.recordVote(player, uuid, votedAt)) {
            debug("No se pudo guardar el último voto de " + player + ".");
        }
    }

    void recordVote(CSCommandSender sender, long votedAt) {
        if (!store.recordVote(sender, votedAt)) {
            debug("No se pudo guardar el último voto de " + sender.getName() + ".");
        }
    }

    void checkReminders() {
        if (!enabled()) {
            return;
        }

        scheduler.runGlobal(() -> checkReminders(snapshotOnlinePlayers()));
    }

    public void shutdown() {
        if (reminderTask != null) {
            reminderTask.cancel();
        }
    }

    private void checkReminders(List<PlayerReference> players) {
        scheduler.runAsync(() -> {
            for (PlayerReference player : players) {
                remindIfReady(player);
            }
        });
    }

    private void remindIfReady(PlayerReference player) {
        long lastVoteAt = store.lastVoteAt(player.getName(), player.getUniqueId());
        if (lastVoteAt <= 0L) {
            return;
        }

        long now = clock.get();
        if (now - lastVoteAt < VOTE_COOLDOWN_MILLIS) {
            return;
        }

        if (store.wasRemindedFor(player.getName(), player.getUniqueId(), lastVoteAt)) {
            return;
        }

        if (!store.markReminded(player.getName(), player.getUniqueId(), lastVoteAt)) {
            debug("No se pudo marcar el recordatorio de " + player.getName() + ".");
            return;
        }

        String reminderMessage = message();
        plugin.runPlayerIfActive(player, sender -> sender.sendMessageWithTag(reminderMessage));
    }

    private List<PlayerReference> snapshotOnlinePlayers() {
        Map<String, PlayerReference> players = new HashMap<>();
        List<CSCommandSender> onlinePlayers = plugin.getOnlinePlayers();
        for (CSCommandSender player : onlinePlayers) {
            PlayerReference reference = PlayerReference.from(player);
            if (reference.hasUniqueId()) {
                players.put(normalizePlayer(reference.getName()), reference);
            }
        }
        return new ArrayList<>(players.values());
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

    private static File dataFolder(File dataPath) {
        return dataPath.getName().endsWith(".properties") ? dataPath.getParentFile() : dataPath;
    }

    private static PlayerVoteStore playerVoteStore(CSPlugin plugin) {
        PlayerVoteStore store = plugin.getPlayerVoteStore();
        return store == null ? new PlayerVoteStore(plugin.getPluginDataFolder(), plugin) : store;
    }

    private void debug(String message) {
        if (plugin.isDebug()) {
            plugin.log("[VoteReminder] " + message);
        }
    }

}

package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.UpdateNotificationSession;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.model.updater.UpdateCheckResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BukkitUpdateJoinListener implements Listener {

    private static final String UPDATE_PERMISSION = "40servidores.actualizar";

    private final BukkitPlugin plugin;
    private final UpdateNotificationSession session = new UpdateNotificationSession();

    public BukkitUpdateJoinListener(BukkitPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Updater updater = plugin.getUpdater();
        if (updater == null || !updater.notifyAdminsOnJoin()) {
            return;
        }

        if (!player.isOp() && !player.hasPermission(UPDATE_PERMISSION)) {
            return;
        }

        String uniqueId = player.getUniqueId().toString();
        if (!session.begin(uniqueId)) {
            return;
        }

        long delayTicks = updater.joinNotificationDelaySeconds() * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> notifyIfUpdateAvailable(player.getUniqueId()), delayTicks);
    }

    private void notifyIfUpdateAvailable(UUID playerId) {
        if (!plugin.isActive()) {
            session.clearPending(playerId.toString());
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            session.clearPending(playerId.toString());
            return;
        }

        Updater updater = plugin.getUpdater();
        UpdateCheckResult result = updater.getCachedResult();
        if (send(player, result)) {
            return;
        }

        CompletableFuture<UpdateCheckResult> currentCheck = updater.getCurrentCheck();
        if (currentCheck == null) {
            session.clearPending(playerId.toString());
            return;
        }

        currentCheck.whenComplete((checkedResult, error) -> plugin.runSyncIfActive(() -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline() || error != null || !send(online, checkedResult)) {
                session.clearPending(playerId.toString());
            }
        }));
    }

    private boolean send(Player player, UpdateCheckResult result) {
        if (plugin.getUpdater().sendUpdateNoticeIfAvailable(new BukkitCommandSender(player, plugin), result)) {
            session.markNotified(player.getUniqueId().toString());
            return true;
        }
        return false;
    }
}

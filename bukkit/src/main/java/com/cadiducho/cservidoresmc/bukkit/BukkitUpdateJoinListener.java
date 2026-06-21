package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.UpdateNotificationSession;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.model.updater.UpdateCheckResult;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;
import com.cadiducho.cservidoresmc.scheduler.PlayerTask;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

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

        PlayerReference reference = PlayerReference.of(player.getName(), uniqueId);
        plugin.getScheduler().runPlayerLater(reference, new PlayerTask() {
            @Override
            public void run(CSCommandSender sender) {
                notifyIfUpdateAvailable(reference, sender);
            }

            @Override
            public void unavailable(PlayerReference player) {
                session.clearPending(player.getUniqueId());
            }
        }, updater.joinNotificationDelaySeconds(), java.util.concurrent.TimeUnit.SECONDS);
    }

    private void notifyIfUpdateAvailable(PlayerReference reference, CSCommandSender sender) {
        if (!plugin.isActive()) {
            session.clearPending(reference.getUniqueId());
            return;
        }

        Updater updater = plugin.getUpdater();
        UpdateCheckResult result = updater.getCachedResult();
        if (send(sender, result)) {
            return;
        }

        CompletableFuture<UpdateCheckResult> currentCheck = updater.getCurrentCheck();
        if (currentCheck == null) {
            session.clearPending(reference.getUniqueId());
            return;
        }

        currentCheck.whenComplete((checkedResult, error) -> plugin.runPlayerIfActive(reference, online -> {
            if (error != null || !send(online, checkedResult)) {
                session.clearPending(reference.getUniqueId());
            }
        }));
    }

    private boolean send(CSCommandSender sender, UpdateCheckResult result) {
        if (plugin.getUpdater().sendUpdateNoticeIfAvailable(sender, result)) {
            session.markNotified(sender.getUniqueId());
            return true;
        }
        return false;
    }
}

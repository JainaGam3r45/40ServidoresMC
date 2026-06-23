package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.VoteStreakService;
import com.cadiducho.cservidoresmc.VoteStreakStore;
import com.cadiducho.cservidoresmc.VoteReminderService;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StreakCMD extends CSCommand {

    private static final String VIEW_PERMISSION = "40servidores.streak.view";
    private static final String RESET_PERMISSION = "40servidores.streak";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    protected StreakCMD() {
        super("streak40", null, Collections.emptyList(),
                "Administra rachas de voto",
                "Usa /streak40 o /streak40 view <jugador>");
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        VoteStreakService service = plugin.getVoteStreakService();
        if (service == null) {
            sender.sendMessageWithTag("&cEl sistema de rachas no está disponible.");
            return CommandResult.SUCCESS;
        }

        if (args.isEmpty() || args.get(0).trim().isEmpty()) {
            if (sender.isConsole()) {
                sendConsoleUsage(sender);
                return CommandResult.SUCCESS;
            }
            sendOwnStreak(plugin, sender, service);
            return CommandResult.SUCCESS;
        }

        String action = args.get(0).toLowerCase();

        if ("view".equals(action)) {
            if (!sender.hasPermission(VIEW_PERMISSION)) {
                return CommandResult.NO_PERMISSION;
            }
            if (args.size() < 2 || args.get(1).trim().isEmpty()) {
                sendConsoleUsage(sender);
                return CommandResult.SUCCESS;
            }
            String player = args.get(1);
            VoteStreakStore.Snapshot snapshot = service.cachedFind(player, "");
            if (snapshot == null) {
                service.requestLoad(player, "");
                sender.sendMessageWithTag(loadingMessage(player));
                return CommandResult.SUCCESS;
            }
            sendAdminStreak(sender, snapshot, player);
            return CommandResult.SUCCESS;
        }

        if ("reset".equals(action) || "reiniciar".equals(action)) {
            if (!sender.hasPermission(RESET_PERMISSION)) {
                return CommandResult.NO_PERMISSION;
            }
            if (args.size() < 2 || args.get(1).trim().isEmpty()) {
                sendHelp(sender);
                return CommandResult.SUCCESS;
            }
            String player = args.get(1);
            resetAsync(plugin, sender, service, player);
            return CommandResult.SUCCESS;
        }

        sendHelp(sender);
        return CommandResult.SUCCESS;
    }

    private void sendOwnStreak(CSPlugin plugin, CSCommandSender sender, VoteStreakService service) {
        String player = sender.getName();
        String uuid = sender.getUniqueId();
        VoteStreakStore.Snapshot snapshot = service.cachedFind(player, uuid);
        if (snapshot == null) {
            service.requestLoad(player, uuid);
            VoteReminderService reminderService = plugin.getVoteReminderService();
            if (reminderService != null) {
                reminderService.requestLoad(player, uuid);
            }
            sender.sendMessageWithTag(loadingMessage(player));
            return;
        }

        VoteReminderService reminderService = plugin.getVoteReminderService();
        long lastVoteAt = reminderService == null ? 0L : reminderService.cachedLastVoteAt(player, uuid);
        long nextVoteInMillis = reminderService == null ? -1L : reminderService.cachedNextVoteInMillis(player, uuid);

        sender.sendMessageWithTag("&9Tu racha de votos");
        sender.sendMessageWithTag("&bRacha actual: &6" + snapshot.getStreak() + " días");
        sender.sendMessageWithTag("&bMejor racha: &6" + snapshot.getBestStreak() + " días");
        sender.sendMessageWithTag("&bÚltimo voto: &6" + friendlyLastVote(snapshot.getLastDay()));
        sender.sendMessageWithTag("&bPuedes volver a votar: &6" + friendlyVoteAvailability(lastVoteAt, nextVoteInMillis));
        sender.sendMessageWithTag("&a¡Vota hoy para mantener tu racha!");
    }

    private void sendAdminStreak(CSCommandSender sender, VoteStreakStore.Snapshot snapshot, String requestedPlayer) {
        String player = snapshot.getPlayer().isEmpty() ? requestedPlayer : snapshot.getPlayer();
        sender.sendMessageWithTag("&9Racha de &e" + player + "&9:");
        sender.sendMessageWithTag("&bRacha actual: &6" + snapshot.getStreak());
        sender.sendMessageWithTag("&bMejor racha: &6" + snapshot.getBestStreak());
        sender.sendMessageWithTag("&bÚltimo día de voto: &6" + emptyValue(snapshot.getLastDay()));
        sender.sendMessageWithTag("&bUUID: &6" + emptyValue(snapshot.getUuid()));
        sender.sendMessageWithTag("&bMilestones premiados: &6" + (snapshot.getRewardedMilestones().isEmpty() ? "ninguno" : snapshot.getRewardedMilestones().toString()));
    }

    private String emptyValue(String value) {
        return value == null || value.isEmpty() ? "ninguno" : value;
    }

    private String friendlyLastVote(String lastDay) {
        if (lastDay == null || lastDay.trim().isEmpty()) {
            return "sin votos registrados";
        }

        try {
            LocalDate voteDay = LocalDate.parse(lastDay);
            LocalDate today = LocalDate.now();
            long days = ChronoUnit.DAYS.between(voteDay, today);
            if (days == 0L) {
                return "hoy";
            }
            if (days == 1L) {
                return "ayer";
            }
            if (days > 1L && days <= 7L) {
                return "hace " + days + " días";
            }
            return DISPLAY_DATE.format(voteDay);
        } catch (DateTimeParseException ignored) {
            return lastDay;
        }
    }

    private String friendlyVoteAvailability(long lastVoteAt, long nextVoteInMillis) {
        if (lastVoteAt <= 0L) {
            return "sin votos registrados";
        }
        if (nextVoteInMillis <= 0L) {
            return "ahora";
        }
        return "en " + formatDuration(nextVoteInMillis);
    }

    private String formatDuration(long millis) {
        long safeMillis = Math.max(0L, millis);
        long days = TimeUnit.MILLISECONDS.toDays(safeMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(safeMillis) % 24L;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(safeMillis) % 60L;

        if (days > 0L) {
            return days + " días";
        }
        if (hours > 0L) {
            return hours + " horas";
        }
        if (minutes > 0L) {
            return minutes + " minutos";
        }
        return "menos de un minuto";
    }

    private String loadingMessage(String player) {
        return "&eLos datos de racha de &6" + player + " &ese están cargando. Inténtalo de nuevo en unos segundos.";
    }

    private void resetAsync(CSPlugin plugin, CSCommandSender sender, VoteStreakService service, String player) {
        plugin.getScheduler().runAsync(() -> {
            boolean reset = service.reset(player);
            plugin.runSenderIfActive(sender, resolved -> {
                if (reset) {
                    resolved.sendMessageWithTag("&aRacha de &e" + player + " &areiniciada correctamente.");
                } else {
                    resolved.sendMessageWithTag("&cNo se pudo reiniciar la racha de &e" + player + "&c.");
                }
            });
        });
    }

    private void sendHelp(CSCommandSender sender) {
        sender.sendMessageWithTag("&cUso: /streak40 view <jugador>");
        sender.sendMessageWithTag("&cUso: /streak40 reset <jugador>");
    }

    private void sendConsoleUsage(CSCommandSender sender) {
        sender.sendMessageWithTag("&cUso: /streak40 view <jugador>");
    }
}

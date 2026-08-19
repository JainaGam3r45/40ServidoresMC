package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.Cooldown;
import com.cadiducho.cservidoresmc.PluginMessages;
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
    private final Cooldown cooldown = new Cooldown(5);

    protected StreakCMD() {
        super("streak40", null, Collections.emptyList(),
                "Administra rachas de voto",
                "Usa /streak40 o /streak40 view <jugador>");
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        if (!sender.isConsole() && isReadOperation(args) && cooldown.isCoolingDown(sender.getName())) {
            return CommandResult.COOLDOWN;
        }

        VoteStreakService service = plugin.getVoteStreakService();
        PluginMessages messages = plugin.getPluginMessages();
        if (service == null) {
            sender.sendMessageWithTag(messages.streakUnavailable());
            return CommandResult.SUCCESS;
        }

        if (args.isEmpty() || args.get(0).trim().isEmpty()) {
            if (sender.isConsole()) {
                sendConsoleUsage(sender, messages);
                return CommandResult.SUCCESS;
            }
            cooldown.setOnCooldown(sender.getName());
            sendOwnStreak(plugin, sender, service, messages);
            return CommandResult.SUCCESS;
        }

        String action = args.get(0).toLowerCase();

        if ("view".equals(action)) {
            if (!sender.hasPermission(VIEW_PERMISSION)) {
                return CommandResult.NO_PERMISSION;
            }
            if (args.size() < 2 || args.get(1).trim().isEmpty()) {
                sendConsoleUsage(sender, messages);
                return CommandResult.SUCCESS;
            }
            if (!sender.isConsole()) {
                cooldown.setOnCooldown(sender.getName());
            }
            String player = args.get(1);
            VoteStreakStore.Snapshot snapshot = service.cachedFind(player, "");
            if (snapshot == null) {
                service.requestLoad(player, "");
                sender.sendMessageWithTag(messages.streakLoading(player));
                return CommandResult.SUCCESS;
            }
            sendAdminStreak(sender, snapshot, player, messages);
            return CommandResult.SUCCESS;
        }

        if ("reset".equals(action) || "reiniciar".equals(action)) {
            if (!sender.hasPermission(RESET_PERMISSION)) {
                return CommandResult.NO_PERMISSION;
            }
            if (args.size() < 2 || args.get(1).trim().isEmpty()) {
                sendHelp(sender, messages);
                return CommandResult.SUCCESS;
            }
            String player = args.get(1);
            resetAsync(plugin, sender, service, player, messages);
            return CommandResult.SUCCESS;
        }

        sendHelp(sender, messages);
        return CommandResult.SUCCESS;
    }

    private void sendOwnStreak(CSPlugin plugin, CSCommandSender sender, VoteStreakService service, PluginMessages messages) {
        String player = sender.getName();
        String uuid = sender.getUniqueId();
        VoteStreakStore.Snapshot snapshot = service.cachedFind(player, uuid);
        if (snapshot == null) {
            service.requestLoad(player, uuid);
            VoteReminderService reminderService = plugin.getVoteReminderService();
            if (reminderService != null) {
                reminderService.requestLoad(player, uuid);
            }
            sender.sendMessageWithTag(messages.streakLoading(player));
            return;
        }

        VoteReminderService reminderService = plugin.getVoteReminderService();
        long lastVoteAt = reminderService == null ? 0L : reminderService.cachedLastVoteAt(player, uuid);
        long nextVoteInMillis = reminderService == null ? -1L : reminderService.cachedNextVoteInMillis(player, uuid);

        PluginMessages.sendLines(sender, messages.streakOwnLines(
                snapshot.getStreak(),
                snapshot.getBestStreak(),
                friendlyLastVote(messages, snapshot.getLastDay()),
                friendlyVoteAvailability(messages, lastVoteAt, nextVoteInMillis)));
    }

    private void sendAdminStreak(CSCommandSender sender, VoteStreakStore.Snapshot snapshot, String requestedPlayer, PluginMessages messages) {
        String player = snapshot.getPlayer().isEmpty() ? requestedPlayer : snapshot.getPlayer();
        String milestones = snapshot.getRewardedMilestones().isEmpty()
                ? messages.streakNone()
                : snapshot.getRewardedMilestones().toString();
        PluginMessages.sendLines(sender, messages.streakAdminLines(
                player,
                snapshot.getStreak(),
                snapshot.getBestStreak(),
                emptyValue(messages, snapshot.getLastDay()),
                emptyValue(messages, snapshot.getUuid()),
                milestones));
    }

    private String emptyValue(PluginMessages messages, String value) {
        return value == null || value.isEmpty() ? messages.streakNone() : value;
    }

    private String friendlyLastVote(PluginMessages messages, String lastDay) {
        if (lastDay == null || lastDay.trim().isEmpty()) {
            return messages.streakNoVotesYet();
        }

        try {
            LocalDate voteDay = LocalDate.parse(lastDay);
            LocalDate today = LocalDate.now();
            long days = ChronoUnit.DAYS.between(voteDay, today);
            if (days == 0L) {
                return messages.streakToday();
            }
            if (days == 1L) {
                return messages.streakYesterday();
            }
            if (days > 1L && days <= 7L) {
                return messages.streakDaysAgo(days);
            }
            return DISPLAY_DATE.format(voteDay);
        } catch (DateTimeParseException ignored) {
            return lastDay;
        }
    }

    private String friendlyVoteAvailability(PluginMessages messages, long lastVoteAt, long nextVoteInMillis) {
        if (lastVoteAt <= 0L) {
            return messages.streakNoVotesYet();
        }
        if (nextVoteInMillis <= 0L) {
            return messages.streakCanVoteNow();
        }
        return messages.streakCanVoteIn(formatDuration(nextVoteInMillis));
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

    private void resetAsync(CSPlugin plugin, CSCommandSender sender, VoteStreakService service, String player, PluginMessages messages) {
        plugin.getScheduler().runAsync(() -> {
            boolean reset = service.reset(player);
            plugin.runSenderIfActive(sender, resolved -> {
                if (reset) {
                    resolved.sendMessageWithTag(messages.streakResetSuccess(player));
                } else {
                    resolved.sendMessageWithTag(messages.streakResetFailed(player));
                }
            });
        });
    }

    private void sendHelp(CSCommandSender sender, PluginMessages messages) {
        PluginMessages.sendLines(sender, messages.streakUsageLines());
    }

    private void sendConsoleUsage(CSCommandSender sender, PluginMessages messages) {
        PluginMessages.sendLines(sender, Collections.singletonList(messages.streakUsageLines().get(0)));
    }

    private boolean isReadOperation(List<String> args) {
        return args.isEmpty() || args.get(0).trim().isEmpty() || "view".equalsIgnoreCase(args.get(0));
    }

    @Override
    public int cooldownSecondsLeft(CSCommandSender sender, List<String> args) {
        return cooldown.getTimeLeft(sender.getName());
    }
}

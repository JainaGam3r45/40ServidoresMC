package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.VoteStreakService;
import com.cadiducho.cservidoresmc.VoteStreakStore;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StreakCMD extends CSCommand {

    protected StreakCMD() {
        super("streak40", "40servidores.streak", Collections.emptyList(),
                "Administra rachas de voto",
                "Usa /streak40 ver <jugador> o /streak40 reset <jugador>");
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        VoteStreakService service = plugin.getVoteStreakService();
        if (service == null) {
            sender.sendMessageWithTag("&cEl sistema de rachas no está disponible.");
            return CommandResult.SUCCESS;
        }

        if (args.size() < 2 || args.get(0).trim().isEmpty() || args.get(1).trim().isEmpty()) {
            sendHelp(sender);
            return CommandResult.SUCCESS;
        }

        String action = args.get(0).toLowerCase();
        String player = args.get(1);

        if (Arrays.asList("ver", "view").contains(action)) {
            sendStreak(sender, service.find(player), player);
            return CommandResult.SUCCESS;
        }

        if (Arrays.asList("reset", "reiniciar").contains(action)) {
            if (service.reset(player)) {
                sender.sendMessageWithTag("&aRacha de &e" + player + " &areiniciada correctamente.");
            } else {
                sender.sendMessageWithTag("&cNo se pudo reiniciar la racha de &e" + player + "&c.");
            }
            return CommandResult.SUCCESS;
        }

        sendHelp(sender);
        return CommandResult.SUCCESS;
    }

    private void sendStreak(CSCommandSender sender, VoteStreakStore.Snapshot snapshot, String requestedPlayer) {
        String player = snapshot.getPlayer().isEmpty() ? requestedPlayer : snapshot.getPlayer();
        sender.sendMessageWithTag("&9Racha de &e" + player + "&9:");
        sender.sendMessageWithTag("&bRacha actual: &6" + snapshot.getStreak());
        sender.sendMessageWithTag("&bÚltimo día de voto: &6" + emptyValue(snapshot.getLastDay()));
        sender.sendMessageWithTag("&bUUID: &6" + emptyValue(snapshot.getUuid()));
        sender.sendMessageWithTag("&bMilestones premiados: &6" + (snapshot.getRewardedMilestones().isEmpty() ? "ninguno" : snapshot.getRewardedMilestones().toString()));
    }

    private String emptyValue(String value) {
        return value == null || value.isEmpty() ? "ninguno" : value;
    }

    private void sendHelp(CSCommandSender sender) {
        sender.sendMessageWithTag("&cUso: /streak40 ver <jugador>");
        sender.sendMessageWithTag("&cUso: /streak40 reset <jugador>");
    }
}

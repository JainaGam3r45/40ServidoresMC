package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.Cooldown;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.VoteResponse;

import java.util.Arrays;
import java.util.List;

/**
 * Comando para validar el voto en 40ServidoresMC
 */
public class VoteCMD extends CSCommand {

    protected VoteCMD() {
        super("voto40", "40servidores.voto", Arrays.asList("votar40", "vote40", "mivoto40"),
                "Valida tu voto en el servidor",
                "Usa /voto40 àra validar tu voto en el servidor");
    }

    final Cooldown cooldown = new Cooldown(60);

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        if (sender.isConsole()) {
            return CommandResult.ONLY_PLAYER;
        }

        if (cooldown.isCoolingDown(sender.getName())) {
            return CommandResult.COOLDOWN;
        }

        cooldown.setOnCooldown(sender.getName());

        sender.sendMessageWithTag("&7Obteniendo voto...");
        plugin.getApiClient().validateVote(sender.getName()).thenAccept((VoteResponse voteResponse) -> {
            plugin.getRewardService().handleVoteResponse(sender.getName(), sender, voteResponse);
        }).exceptionally(e -> {
            sender.sendMessageWithTag("&cHa ocurrido una excepción. Avisa a un administrador");
            plugin.logError("Excepción intentando votar: " + e.getMessage());
            return null;
        });

        return CommandResult.SUCCESS;
    }
}

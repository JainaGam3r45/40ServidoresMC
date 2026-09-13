package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.Cooldown;
import com.cadiducho.cservidoresmc.VoteTrace;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.PendingVotesResponse;

import java.util.Arrays;
import java.util.List;

/**
 * Valida el voto en 40ServidoresMC con el protocolo v3 (pending + ack).
 */
public class VoteCMD extends CSCommand {

    protected VoteCMD() {
        super("voto40", "40servidores.voto", Arrays.asList("votar40", "vote40", "mivoto40"),
                "Valida tu voto en el servidor",
                "Usa /voto40 para validar tu voto en el servidor");
    }

    final Cooldown cooldown = new Cooldown(10);

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        if (sender.isConsole()) {
            return CommandResult.ONLY_PLAYER;
        }

        if (cooldown.isCoolingDown(sender.getName())) {
            return CommandResult.COOLDOWN;
        }

        cooldown.setOnCooldown(sender.getName());

        VoteTrace trace = VoteTrace.start(plugin, sender);

        if (plugin.getRewardService().sendAlreadyRewardedIfActive(sender, trace)) {
            return CommandResult.SUCCESS;
        }

        sender.sendMessageWithTag(plugin.getPluginMessages().voteChecking());

        // Fire-and-forget retry of prior delivered-but-unacked vote ids.
        plugin.getApiClient().retryPendingAcks(sender.getName(), trace);

        plugin.getApiClient().fetchPendingVotes(sender.getName(), trace).thenAcceptAsync((PendingVotesResponse pending) -> {
            if (!plugin.isActive()) {
                trace.aborted("plugin_inactive");
                return;
            }
            plugin.runSenderIfActive(sender, resolved ->
                    plugin.getRewardService().handlePendingVotes(resolved.getName(), resolved, pending, trace));
        }, plugin.getAsyncExecutor()).exceptionally(e -> {
            if (!plugin.isActive()) {
                trace.aborted("plugin_inactive");
                return null;
            }
            plugin.runSenderIfActive(sender, resolved ->
                    plugin.getRewardService().handleVoteApiFailure(resolved, e, trace));
            plugin.logError("Excepción intentando votar: " + e.getMessage());
            return null;
        });

        return CommandResult.SUCCESS;
    }

    @Override
    public int cooldownSecondsLeft(CSCommandSender sender, List<String> args) {
        return cooldown.getTimeLeft(sender.getName());
    }
}

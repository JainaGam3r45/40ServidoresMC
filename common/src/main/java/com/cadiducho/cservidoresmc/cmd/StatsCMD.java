package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.Cooldown;
import com.cadiducho.cservidoresmc.PluginMessages;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.ServerVote;

import java.util.Collections;
import java.util.List;

/**
 * Comando para obtener las estadísticas de tu servidor en 40ServidoresMC
 * @author Cadiducho
 */
public class StatsCMD extends CSCommand {

    private final Cooldown cooldown = new Cooldown(10);

    public StatsCMD() {
        super("stats40", "40servidores.stats", Collections.emptyList(),
                "Comprueba las estadísticas de voto",
                "Usa /stats40 para obtener las estadísticas de voto");
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        if (!sender.isConsole() && cooldown.isCoolingDown(sender.getName())) {
            return CommandResult.COOLDOWN;
        }
        if (!sender.isConsole()) {
            cooldown.setOnCooldown(sender.getName());
        }
        plugin.getApiClient().fetchServerStats().thenAccept((ServerStats serverStats) -> {
            if (!plugin.isActive()) {
                return;
            }
            plugin.runSenderIfActive(sender, resolved -> sendStats(plugin, resolved, serverStats));
        }).exceptionally(ex -> {
            plugin.runSenderIfActive(sender, resolved -> resolved.sendMessageWithTag(plugin.getPluginMessages().statsException()));
            plugin.logError("Excepción obteniendo estadisticas: " + ex.getMessage());
            return null;
        });
        return CommandResult.SUCCESS;
    }

    @Override
    public int cooldownSecondsLeft(CSCommandSender sender, List<String> args) {
        return cooldown.getTimeLeft(sender.getName());
    }

    private void sendStats(CSPlugin plugin, CSCommandSender sender, ServerStats serverStats) {
        PluginMessages messages = plugin.getPluginMessages();
        if (serverStats.getServerName() == null) {
            sender.sendMessageWithTag(messages.invalidApiKey());
            return;
        }

        String lastVotes = formatLastVotes(serverStats);
        PluginMessages.sendLines(sender, messages.statsLines(serverStats, lastVotes));
    }

    private String formatLastVotes(ServerStats serverStats) {
        if (serverStats.getLastVotes() == null || serverStats.getLastVotes().isEmpty()) {
            return "";
        }

        StringBuilder usuarios = new StringBuilder();
        for (ServerVote vote : serverStats.getLastVotes()) {
            String color = vote.isRewarded() ? "&a" : "&c";
            usuarios.append(color).append(vote.getName()).append("&6, ");
        }
        return usuarios.substring(0, usuarios.length() - 2) + ".";
    }
}

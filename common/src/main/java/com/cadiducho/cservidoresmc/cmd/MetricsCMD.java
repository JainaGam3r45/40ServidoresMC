package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.PluginMetrics;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.util.Collections;
import java.util.List;

public class MetricsCMD extends CSCommand {

    protected MetricsCMD() {
        super("metrics40", "40servidores.metrics", Collections.emptyList(),
                "Muestra métricas internas del plugin",
                "Usa /metrics40 para consultar las métricas internas del plugin");
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        PluginMetrics metrics = plugin.getPluginMetrics();

        sender.sendMessageWithTag("&9==> &7Métricas internas desde el último arranque");
        sender.sendMessageWithTag("&bPeticiones API: &6" + metrics.getApiRequests());
        sender.sendMessageWithTag("&bFallos API: &6" + metrics.getApiFailures());
        sender.sendMessageWithTag("&bReintentos: &6" + metrics.getRetries());
        sender.sendMessageWithTag("&bRechazos HTTP: &6" + metrics.getHttpRejections());
        sender.sendMessageWithTag("&bComprobaciones de voto: &6" + metrics.getVoteChecks());
        sender.sendMessageWithTag("&bRecompensas entregadas: &6" + metrics.getRewardsDelivered());

        return CommandResult.SUCCESS;
    }
}

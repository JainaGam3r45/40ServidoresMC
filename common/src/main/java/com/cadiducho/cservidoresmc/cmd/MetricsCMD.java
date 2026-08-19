package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.PluginMessages;
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
        PluginMessages messages = plugin.getPluginMessages();
        PluginMessages.sendLines(sender, messages.metricsLines(metrics));
        return CommandResult.SUCCESS;
    }
}

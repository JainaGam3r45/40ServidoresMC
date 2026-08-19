package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Comando para recargar la configuración y sus recompensas
 * @author _arhlex_, Cadiducho
 */
public class ReloadCMD extends CSCommand {

    protected ReloadCMD() {
        super("reload40", "40servidores.recargar", Arrays.asList("recargar40", "config40"),
                "Recargar la configuración del plugin",
                "Usa /reload40 para recargar la configuración"
        );
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        plugin.getCSConfiguration().reload();
        plugin.getApiClient().invalidateAllCache();

        com.cadiducho.cservidoresmc.PluginMessages.sendLines(
                sender,
                plugin.getPluginMessages().reloadLines(plugin.getPluginVersion()));

        plugin.log("Configuracion recargada");
        return CommandResult.SUCCESS;
    }
}

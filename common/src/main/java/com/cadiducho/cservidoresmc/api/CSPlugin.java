package com.cadiducho.cservidoresmc.api;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.PluginMetrics;
import com.cadiducho.cservidoresmc.RewardService;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.VoteReminderService;
import com.cadiducho.cservidoresmc.VoteStreakService;
import com.cadiducho.cservidoresmc.config.CSConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;

public interface CSPlugin {

    void log(String text);
    void logError(String text);

    default boolean isDebug() {
        return getCSConfiguration().getBoolean("debug");
    }

    default void debugLog(String s) {
        if (isDebug()){
            log("[Debug] " + s);
        }
    }

    /**
     * Registrar los comandos en la plataforma deseada
     */
    void registerCommands();

    /**
     * Datos de configuración del plugin, con implementación para cada tipo de servidor
     * @return config
     */
    CSConfiguration getCSConfiguration();

    /**
     * Obtener la versión de la configuración
     * @return la versión de la configuración
     */
    default int configVersion() {
        return 7;
    }

    /**
     * Comprobar si la configuración tiene una clave válida
     */
    default void checkDefaultKey() {
        if (getCSConfiguration().getInt("configVer", 0) != configVersion()) {
            logError("¡Tu configuración es de una versión más antigua a la de este plugin!");
            logError("Actualiza la configuración para evitar errores.");
        }
        if (getCSConfiguration().getString("clave", "key").equalsIgnoreCase("key")) {
            logError("¡Atención! La clave del servidor no está correctamente configurada");
            logError("Accede a la configuración y modifica 'clave' con el valor correcto obtenido en la página web.");
            logError("Este error hará que el plugin no funcione correctamente.");
        }
    }

    /**
     * Instancia del cliente HTTP para la API
     * @return API client
     */
    ApiClient getApiClient();

    /**
     * Servicio encargado de entregar premios por votos
     * @return servicio de premios
     */
    RewardService getRewardService();

    /**
     * Servicio encargado de recordar a los jugadores cuándo pueden volver a votar
     * @return servicio de recordatorios
     */
    default VoteReminderService getVoteReminderService() {
        return null;
    }

    /**
     * Servicio encargado de guardar y premiar rachas de votos
     * @return servicio de rachas
     */
    default VoteStreakService getVoteStreakService() {
        return null;
    }

    /**
     * Directorio de datos del plugin
     * @return carpeta de datos
     */
    File getPluginDataFolder();

    default void shutdownRewardService() {
        RewardService rewardService = getRewardService();
        if (rewardService != null) {
            rewardService.shutdown();
        }
    }

    default void shutdownVoteReminderService() {
        VoteReminderService voteReminderService = getVoteReminderService();
        if (voteReminderService != null) {
            voteReminderService.shutdown();
        }
    }

    /**
     * Instancia del actualizador
     * @return updater
     */
    Updater getUpdater();

    /**
     * Métricas internas acumuladas desde el arranque del plugin
     * @return métricas internas
     */
    PluginMetrics getPluginMetrics();

    /**
     * La versión del plugin en String, por ejemplo "3.0"
     * @return versión
     */
    String getPluginVersion();

    /**
     * Ejecutar un comando deseado por la consola del servidor
     * @param command El comando deseado
     */
    void dispatchCommand(String command);

    /**
     * Obtener jugadores conectados como command senders genéricos
     * @return jugadores conectados
     */
    default List<CSCommandSender> getOnlinePlayers() {
        return Collections.emptyList();
    }

    /**
     * Ejecutar una tarea en el hilo principal de la plataforma
     * @param task tarea
     */
    default void runSync(Runnable task) {
        task.run();
    }

    /**
     * Enviar un mensaje a todos los usuarios
     * @param message el mensaje
     */
    void broadcastMessage(String message);
}

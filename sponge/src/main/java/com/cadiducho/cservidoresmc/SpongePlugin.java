package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommandManager;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.google.gson.Gson;
import com.google.inject.Inject;
import org.bstats.sponge.Metrics;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

import java.io.File;

@Plugin(id = "cservidoresmc", name = "40ServidoresMC", version = SpongePlugin.PLUGIN_VERSION)
public class SpongePlugin implements CSPlugin {

    public static final String PLUGIN_VERSION = "3.2.0";
    @Inject private Logger logger;
    @Inject private Game game;

    private final Metrics metrics;
    private final PluginMetrics pluginMetrics = new PluginMetrics();

    private ApiClient apiClient;
    private Updater updater;
    private RewardService rewardService;
    private CSConfiguration csConfiguration;
    private CSCommandManager csCommandManager;

    @Inject
    public SpongePlugin(Metrics.Factory metricsFactory) {
        int pluginId = 10604;
        metrics = metricsFactory.make(pluginId);
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        csConfiguration = null; //new CSBukkitConfig(instance, getDataFolder() + File.separator + "config.yml");
        //csConfiguration.load(; // FixMe: Confdig

        apiClient = new ApiClient(this, new Gson());
        rewardService = new RewardService(this);
        updater = new Updater(this, getPluginVersion(), this.game.getPlatform().getMinecraftVersion().getName());
        updater.checkearVersion(new CSConsoleSender(this));
    }

    @Listener
    public void onServerStop(GameStoppedServerEvent event) {
        shutdownRewardService();
    }

    @Override
    public void registerCommands() {
        this.csCommandManager = new CSCommandManager(this);
    }

    @Override
    public void log(String text) {
        logger.info(text);
    }

    @Override
    public void logError(String text) {
        logger.error(text);
    }

    @Override
    public CSConfiguration getCSConfiguration() {
        return csConfiguration;
    }

    @Override
    public ApiClient getApiClient() {
        return apiClient;
    }

    @Override
    public RewardService getRewardService() {
        return rewardService;
    }

    @Override
    public File getPluginDataFolder() {
        return new File("config/cservidoresmc");
    }

    @Override
    public Updater getUpdater() {
        return updater;
    }

    @Override
    public PluginMetrics getPluginMetrics() {
        return pluginMetrics;
    }

    @Override
    public String getPluginVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public void dispatchCommand(String command) {
        Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command);
    }

    @Override
    public void broadcastMessage(String message) {
        Sponge.getServer().getBroadcastChannel().send(Text.of(message));
    }
}

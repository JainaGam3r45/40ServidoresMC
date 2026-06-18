package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.*;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.google.gson.Gson;
import com.google.inject.Inject;
import org.bstats.sponge.Metrics;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandManager;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameLoadCompleteEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Plugin(id = "cservidoresmc", name = "40ServidoresMC", version = SpongePlugin.PLUGIN_VERSION)
public class SpongePlugin implements CSPlugin {

    public static final String PLUGIN_VERSION = "3.0.2";
    @Inject private Logger logger;
    @Inject private Game game;

    private final Metrics metrics;
    private final PluginMetrics pluginMetrics = new PluginMetrics();

    private ApiClient apiClient;
    private Updater updater;
    private RewardService rewardService;
    private VoteReminderService voteReminderService;
    private VoteStreakService voteStreakService;
    private CSConfiguration csConfiguration;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDirectory;
    private CSCommandManager csCommandManager;

    @Inject
    public SpongePlugin(Metrics.Factory metricsFactory) {
        int pluginId = 10604;
        metrics = metricsFactory.make(pluginId);
    }


    @Listener
    public void onServerLoad(GameLoadCompleteEvent event) {
        this.csConfiguration = new SpongeConfigAdapter(this, resolveConfig());

        registerCommands();
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        apiClient = new ApiClient(this, new Gson());
        new LegacyPlayerDataMigrator(this, getPluginDataFolder()).migrate();
        voteReminderService = new VoteReminderService(this);
        voteStreakService = new VoteStreakService(this);
        rewardService = new RewardService(this);
        voteReminderService.start();
        updater = new Updater(this, getPluginVersion(), this.game.getPlatform().getMinecraftVersion().getName());
        updater.checkearVersion(new CSConsoleSender(this));

        checkDefaultKey();
    }

    @Listener
    public void onServerStop(GameStoppedServerEvent event) {
        shutdownVoteReminderService();
        shutdownRewardService();
    }

    private Path resolveConfig() {
        Path path = this.configDirectory.resolve("40ServidoresMC.conf");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(this.configDirectory);
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("40ServidoresMC.conf")) {
                    Files.copy(is, path);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return path;
    }

    @Override
    public void registerCommands() {
        this.csCommandManager = new CSCommandManager(this);
        CommandManager cmdService = Sponge.getCommandManager();
        this.csCommandManager.getCommands().forEach(cmd -> {
            List<String> alias = new ArrayList<>(cmd.getAliases());
            alias.add(cmd.getName());
            cmdService.register(this, new SpongeCommandExecutor(csCommandManager, cmd), alias);
        });
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
        return this.csConfiguration;
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
    public VoteReminderService getVoteReminderService() {
        return voteReminderService;
    }

    @Override
    public VoteStreakService getVoteStreakService() {
        return voteStreakService;
    }

    @Override
    public File getPluginDataFolder() {
        return configDirectory.toFile();
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
    public List<CSCommandSender> getOnlinePlayers() {
        List<CSCommandSender> players = new ArrayList<>();
        for (Player player : Sponge.getServer().getOnlinePlayers()) {
            players.add(new SpongeCommandSender(player, this));
        }
        return players;
    }

    @Override
    public String resolvePlayerUniqueId(String player) {
        if (player == null || player.trim().isEmpty()) {
            return "";
        }
        return Sponge.getServer().getPlayer(player)
                .map(value -> value.getUniqueId().toString())
                .orElse("");
    }

    @Override
    public void runSync(Runnable task) {
        Sponge.getScheduler().createTaskBuilder().execute(task).submit(this);
    }

    @Override
    public void broadcastMessage(String message) {
        Sponge.getServer().getBroadcastChannel().send(TextSerializers.FORMATTING_CODE.deserialize(message));
    }
}

package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.*;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.BoundedTaskExecutor;
import com.cadiducho.cservidoresmc.PlayerVoteStore;
import com.cadiducho.cservidoresmc.UpdateNotificationSession;
import com.cadiducho.cservidoresmc.model.updater.UpdateCheckResult;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.cadiducho.cservidoresmc.scheduler.PlayerReference;
import com.cadiducho.cservidoresmc.scheduler.PlayerTask;
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
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Plugin(id = "cservidoresmc", name = "40ServidoresMC", version = SpongePlugin.PLUGIN_VERSION)
public class SpongePlugin implements CSPlugin {

    public static final String PLUGIN_VERSION = "3.2.2";
    @Inject private Logger logger;
    @Inject private Game game;

    private final Metrics metrics;
    private final PluginMetrics pluginMetrics = new PluginMetrics();

    private ApiClient apiClient;
    private Updater updater;
    private RewardService rewardService;
    private VoteReminderService voteReminderService;
    private VoteStreakService voteStreakService;
    private PlayerVoteStore playerVoteStore;
    private CSConfiguration csConfiguration;
    private BoundedTaskExecutor asyncExecutor;
    private SpongeSchedulerAdapter scheduler;
    private volatile boolean active;
    private final UpdateNotificationSession updateNotificationSession = new UpdateNotificationSession();

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
        active = true;
        asyncExecutor = new BoundedTaskExecutor("40servidoresmc-http", pluginMetrics, this::logError);
        scheduler = new SpongeSchedulerAdapter(this, asyncExecutor);
        apiClient = new ApiClient(this, new Gson(), asyncExecutor);
        playerVoteStore = new PlayerVoteStore(getPluginDataFolder(), this);
        new LegacyPlayerDataMigrator(this, getPluginDataFolder(), playerVoteStore).migrate();
        voteReminderService = new VoteReminderService(this);
        voteStreakService = new VoteStreakService(this);
        rewardService = new RewardService(this);
        voteReminderService.start();
        playerVoteStore.warmUp(getOnlinePlayers());
        updater = new Updater(this, getPluginVersion(), this.game.getPlatform().getMinecraftVersion().getName());
        updater.checkearVersion(new CSConsoleSender(this));

        checkDefaultKey();
        printStartupInfo();
    }

    @Listener
    public void onServerStop(GameStoppedServerEvent event) {
        active = false;
        shutdownVoteReminderService();
        shutdownRewardService();
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (asyncExecutor != null) {
            asyncExecutor.shutdownNow();
        }
    }

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event) {
        Player player = event.getTargetEntity();
        if (updater == null || !updater.notifyAdminsOnJoin() || !player.hasPermission("40servidores.actualizar")) {
            return;
        }

        String uniqueId = player.getUniqueId().toString();
        if (!updateNotificationSession.begin(uniqueId)) {
            return;
        }

        PlayerReference reference = PlayerReference.of(player.getName(), uniqueId);
        getScheduler().runPlayerLater(reference, new PlayerTask() {
            @Override
            public void run(CSCommandSender sender) {
                notifyJoinedAdmin(reference, sender);
            }

            @Override
            public void unavailable(PlayerReference player) {
                updateNotificationSession.clearPending(player.getUniqueId());
            }
        }, updater.joinNotificationDelaySeconds(), java.util.concurrent.TimeUnit.SECONDS);
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

    private void printStartupInfo() {
        Sponge.getServer().getConsole().sendMessage(Text.EMPTY);
        Sponge.getServer().getConsole().sendMessage(Text.of(
                TextColors.AQUA, "  _  _    ___   ",
                TextColors.GREEN, "40ServidoresMC ",
                TextColors.AQUA, "v" + getPluginVersion()));
        Sponge.getServer().getConsole().sendMessage(Text.of(
                TextColors.AQUA, " | || |  / _ \\  ",
                TextColors.DARK_GRAY, "Running on Sponge - " + game.getPlatform().getMinecraftVersion().getName()));
        Sponge.getServer().getConsole().sendMessage(Text.of(
                TextColors.AQUA, " |__  _|| | | | ",
                TextColors.DARK_GRAY, "Reescrito por ",
                TextColors.LIGHT_PURPLE, Text.builder("github.com/jainagam3r45").style(org.spongepowered.api.text.format.TextStyles.UNDERLINE).build()));
        Sponge.getServer().getConsole().sendMessage(Text.of(
                TextColors.AQUA, "    |_|  \\___/  "));
        Sponge.getServer().getConsole().sendMessage(Text.EMPTY);
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
    public PlayerVoteStore getPlayerVoteStore() {
        return playerVoteStore;
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
    public boolean isActive() {
        return active;
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor == null ? CSPlugin.super.getAsyncExecutor() : asyncExecutor;
    }

    @Override
    public CSScheduler getScheduler() {
        return scheduler == null ? CSPlugin.super.getScheduler() : scheduler;
    }

    @Override
    public String getPluginVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public void dispatchCommand(String command) {
        getScheduler().runGlobal(() -> Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command));
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
        getScheduler().runGlobal(task);
    }

    @Override
    public void sendFormattedMessage(CSCommandSender sender, String message) {
        if (sender != null && sender.isConsole()) {
            Sponge.getServer().getConsole().sendMessage(TextSerializers.FORMATTING_CODE.deserialize(message));
            return;
        }
        CSPlugin.super.sendFormattedMessage(sender, message);
    }

    @Override
    public void broadcastMessage(String message) {
        getScheduler().runGlobal(() -> Sponge.getServer().getBroadcastChannel().send(TextSerializers.FORMATTING_CODE.deserialize(message)));
    }

    private void notifyJoinedAdmin(PlayerReference reference, CSCommandSender sender) {
        if (!isActive()) {
            updateNotificationSession.clearPending(reference.getUniqueId());
            return;
        }

        UpdateCheckResult result = updater.getCachedResult();
        if (sendUpdateNotice(sender, result)) {
            return;
        }

        CompletableFuture<UpdateCheckResult> currentCheck = updater.getCurrentCheck();
        if (currentCheck == null) {
            updateNotificationSession.clearPending(reference.getUniqueId());
            return;
        }

        currentCheck.whenComplete((checkedResult, error) -> runPlayerIfActive(reference, online -> {
            if (error != null || !sendUpdateNotice(online, checkedResult)) {
                updateNotificationSession.clearPending(reference.getUniqueId());
            }
        }));
    }

    private boolean sendUpdateNotice(CSCommandSender sender, UpdateCheckResult result) {
        if (updater.sendUpdateNoticeIfAvailable(sender, result)) {
            updateNotificationSession.markNotified(sender.getUniqueId());
            return true;
        }
        return false;
    }
}

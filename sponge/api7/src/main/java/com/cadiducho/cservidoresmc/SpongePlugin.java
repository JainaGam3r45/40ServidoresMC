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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
    private VoteReminderService voteReminderService;
    private VoteStreakService voteStreakService;
    private PlayerVoteStore playerVoteStore;
    private CSConfiguration csConfiguration;
    private BoundedTaskExecutor asyncExecutor;
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

        Sponge.getScheduler().createTaskBuilder()
                .delay(updater.joinNotificationDelaySeconds(), TimeUnit.SECONDS)
                .execute(() -> notifyJoinedAdmin(player.getUniqueId()))
                .submit(this);
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
                TextColors.AQUA, "  40  ",
                TextColors.WHITE, "40ServidoresMC ",
                TextColors.GRAY, "v" + getPluginVersion()));
        Sponge.getServer().getConsole().sendMessage(Text.of(
                TextColors.AQUA, "      ",
                TextColors.DARK_GRAY, "Running on Sponge - " + game.getPlatform().getMinecraftVersion().getName()));
        Sponge.getServer().getConsole().sendMessage(Text.of(
                TextColors.AQUA, "      ",
                TextColors.DARK_GRAY, "Reescrito por JainaGam3r45"));
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
    public void sendFormattedMessage(CSCommandSender sender, String message) {
        if (sender != null && sender.isConsole()) {
            Sponge.getServer().getConsole().sendMessage(TextSerializers.FORMATTING_CODE.deserialize(message));
            return;
        }
        CSPlugin.super.sendFormattedMessage(sender, message);
    }

    @Override
    public void broadcastMessage(String message) {
        runSync(() -> Sponge.getServer().getBroadcastChannel().send(TextSerializers.FORMATTING_CODE.deserialize(message)));
    }

    private void notifyJoinedAdmin(UUID playerId) {
        if (!isActive()) {
            updateNotificationSession.clearPending(playerId.toString());
            return;
        }

        Player player = Sponge.getServer().getPlayer(playerId).orElse(null);
        if (player == null) {
            updateNotificationSession.clearPending(playerId.toString());
            return;
        }

        UpdateCheckResult result = updater.getCachedResult();
        if (sendUpdateNotice(player, result)) {
            return;
        }

        CompletableFuture<UpdateCheckResult> currentCheck = updater.getCurrentCheck();
        if (currentCheck == null) {
            updateNotificationSession.clearPending(playerId.toString());
            return;
        }

        currentCheck.whenComplete((checkedResult, error) -> runSyncIfActive(() -> {
            Player online = Sponge.getServer().getPlayer(playerId).orElse(null);
            if (online == null || error != null || !sendUpdateNotice(online, checkedResult)) {
                updateNotificationSession.clearPending(playerId.toString());
            }
        }));
    }

    private boolean sendUpdateNotice(Player player, UpdateCheckResult result) {
        if (updater.sendUpdateNoticeIfAvailable(new SpongeCommandSender(player, this), result)) {
            updateNotificationSession.markNotified(player.getUniqueId().toString());
            return true;
        }
        return false;
    }
}

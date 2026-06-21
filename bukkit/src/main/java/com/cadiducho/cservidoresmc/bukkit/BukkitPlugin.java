package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.BoundedTaskExecutor;
import com.cadiducho.cservidoresmc.LegacyPlayerDataMigrator;
import com.cadiducho.cservidoresmc.PluginMetrics;
import com.cadiducho.cservidoresmc.PlayerVoteStore;
import com.cadiducho.cservidoresmc.RewardService;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.VoteReminderService;
import com.cadiducho.cservidoresmc.VoteStreakService;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommandManager;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.scheduler.CSScheduler;
import com.google.gson.Gson;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Implementación para Bukkit, Spigot y Glowstone
 * @author Cadiducho
 */
public class BukkitPlugin extends JavaPlugin implements CSPlugin {

    @Getter private ApiClient apiClient;
    @Getter private Updater updater;
    @Getter private RewardService rewardService;
    @Getter private VoteReminderService voteReminderService;
    @Getter private VoteStreakService voteStreakService;
    private PlayerVoteStore playerVoteStore;
    @Getter private final PluginMetrics pluginMetrics = new PluginMetrics();
    
    private static BukkitPlugin instance;

    private CSConfiguration csConfiguration;
    private CSCommandManager commandManager;
    private BoundedTaskExecutor asyncExecutor;
    private BukkitSchedulerAdapter scheduler;
    private volatile boolean active;
    
    @Override
    public void onEnable() {
        instance = this;
        active = true;

        /*
         * Generar y cargar Config.yml
         */
        File configFile = new File(getDataFolder() + File.separator + "config.yml");
        new ConfigMigrator(instance, configFile).migrate();
        csConfiguration = new BukkitConfigurationAdapter(instance, configFile);
        playerVoteStore = new PlayerVoteStore(getDataFolder(), instance);
        new LegacyPlayerDataMigrator(instance, getDataFolder(), playerVoteStore).migrate();

        asyncExecutor = new BoundedTaskExecutor("40servidoresmc-http", pluginMetrics, this::logError);
        scheduler = new BukkitSchedulerAdapter(this, asyncExecutor);
        apiClient = new ApiClient(instance, new Gson(), asyncExecutor);
        voteReminderService = new VoteReminderService(instance);
        voteStreakService = new VoteStreakService(instance);
        rewardService = new RewardService(instance);
        voteReminderService.start();
        playerVoteStore.warmUp(getOnlinePlayers());

        /*
         * Comandos y eventos
         */
        debugLog("Registrando comandos y eventos...");
        registerCommands();
        getServer().getPluginManager().registerEvents(new BukkitUpdateJoinListener(instance), instance);

        installPlaceholderAPI();
        
        Metrics metrics = new Metrics(instance, 3909);

        /*
         * Finalizar...
         */
        updater = new Updater(instance, getPluginVersion(), getServer().getBukkitVersion().split("-")[0]);
        debugLog("Checkeando nuevas versiones...");
        updater.checkearVersion(null);

        checkDefaultKey();
        printStartupInfo();
    }

    @Override
    public void onDisable() {
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

    @Override
    public void registerCommands() {
        this.commandManager = new CSCommandManager(instance);
    }

    /**
     * Comprobar si el plugin PlaceholderAPI está activo, y si es así registrar la extensión
     */
    private void installPlaceholderAPI() {
        if (!getCSConfiguration().getBoolean("placeholderapi.enabled", true)) {
            debugLog("PlaceholderAPI desactivado desde la configuración.");
            return;
        }

        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
        }
    }
    
    @Override
    public boolean onCommand(CommandSender bukkitSender, Command cmd, String label, String[] args) {
        if (label.startsWith(("40ServidoresMC:").toLowerCase())) {
            label = label.substring(("40ServidoresMC:").length());
        }
        CSCommandSender csCommandSender;
        if (bukkitSender instanceof ConsoleCommandSender) {
            csCommandSender = new CSConsoleSender(instance);
        } else {
            csCommandSender = new BukkitCommandSender(bukkitSender, this);
        }

        try {
            commandManager.executeCommand(csCommandSender, label, Arrays.asList(args));
        } catch (Exception ex) {
            logError("Error al ejecutar el comando '/" + label + Arrays.toString(args)+"'");
            debugLog(ex.getMessage());
            if (ex.getCause() != null) debugLog(ex.getCause().getMessage());
        }
        return true;
    }

    @Override
    public CSConfiguration getCSConfiguration() {
        return this.csConfiguration;
    }

    private void printStartupInfo() {
        ConsoleCommandSender console = getServer().getConsoleSender();
        console.sendMessage("");
        console.sendMessage(ChatColor.AQUA + "  _  _    ___   " + ChatColor.GREEN + "40ServidoresMC " + ChatColor.AQUA + "v" + getPluginVersion());
        console.sendMessage(ChatColor.AQUA + " | || |  / _ \\  " + ChatColor.DARK_GRAY + "Running on Bukkit - " + getServer().getName() + " " + getServer().getBukkitVersion().split("-")[0]);
        console.sendMessage(ChatColor.AQUA + " |__  _|| | | | " + ChatColor.DARK_GRAY + "Reescrito por " + ChatColor.LIGHT_PURPLE + ChatColor.UNDERLINE + "github.com/jainagam3r45" + ChatColor.RESET);
        console.sendMessage(ChatColor.AQUA + "    |_|  \\___/  ");
        console.sendMessage("");
    }

    @Override
    public File getPluginDataFolder() {
        return getDataFolder();
    }

    @Override
    public PlayerVoteStore getPlayerVoteStore() {
        return playerVoteStore;
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
    public void log(String s) {
        getLogger().log(Level.INFO, s);
    }

    @Override
    public void logError(String s){
       getLogger().log(Level.SEVERE, s);
    }

    @Override
    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }

    @Override
    public void dispatchCommand(String command) {
        getScheduler().runGlobal(() -> getServer().dispatchCommand(getServer().getConsoleSender(), command));
    }

    @Override
    public List<CSCommandSender> getOnlinePlayers() {
        List<CSCommandSender> players = new ArrayList<>();
        for (Player player : getServer().getOnlinePlayers()) {
            players.add(new BukkitCommandSender(player, this));
        }
        return players;
    }

    @Override
    public String resolvePlayerUniqueId(String player) {
        if (player == null || player.trim().isEmpty()) {
            return "";
        }
        Player online = getServer().getPlayerExact(player);
        if (online != null) {
            return online.getUniqueId().toString();
        }
        OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(player);
        return offlinePlayer == null || offlinePlayer.getUniqueId() == null ? "" : offlinePlayer.getUniqueId().toString();
    }

    @Override
    public void runSync(Runnable task) {
        getScheduler().runGlobal(task);
    }

    @Override
    public void sendFormattedMessage(CSCommandSender sender, String message) {
        if (sender != null && sender.isConsole()) {
            getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        CSPlugin.super.sendFormattedMessage(sender, message);
    }

    @Override
    public void broadcastMessage(String message) {
        getScheduler().runGlobal(() -> {
            getServer().getOnlinePlayers().forEach(p -> p.sendMessage(ChatColor.translateAlternateColorCodes('&', message)));
        });
    }

}

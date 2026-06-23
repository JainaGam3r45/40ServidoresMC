package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommandManager;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStreakCommand {

    private static final String UUID = "0f50d3c1-2d53-47d8-9f5a-10153b5f9770";

    @TempDir
    File tempDir;

    private TestPlugin plugin;
    private CSCommandManager commandManager;

    @BeforeEach
    void setUp() {
        plugin = new TestPlugin(tempDir);
        commandManager = new CSCommandManager(plugin);
    }

    @Test
    void playerWithoutArgumentsSeesFriendlyOwnStreak() {
        TestSender sender = player("Cadiducho", UUID);
        plugin.voteStreakService.recordVote(sender);
        plugin.playerVoteStore.recordVote(sender.getName(), sender.getUniqueId(), System.currentTimeMillis() - 25L * 60L * 60L * 1000L);

        commandManager.executeCommand(sender, "streak40", new ArrayList<>());

        String messages = sender.joinedMessages();
        assertTrue(messages.contains("Tu racha de votos"));
        assertTrue(messages.contains("Racha actual"));
        assertTrue(messages.contains("Mejor racha"));
        assertTrue(messages.contains("Puedes volver a votar"));
        assertFalse(messages.contains(UUID));
        assertFalse(messages.contains("90000000"));
        assertFalse(messages.contains("Milestones"));
    }

    @Test
    void viewRequiresDedicatedPermission() {
        TestSender target = player("Cadiducho", UUID);
        plugin.voteStreakService.recordVote(target);
        TestSender sender = player("Admin", "11111111-1111-1111-1111-111111111111");
        sender.permissions.add("40servidores.streak.view");

        commandManager.executeCommand(sender, "streak40", Arrays.asList("view", "Cadiducho"));

        String messages = sender.joinedMessages();
        assertTrue(messages.contains("UUID"));
        assertTrue(messages.contains(UUID));
        assertTrue(messages.contains("Milestones premiados"));
    }

    @Test
    void viewWithoutPermissionIsRejected() {
        TestSender sender = player("Cadiducho", UUID);

        commandManager.executeCommand(sender, "streak40", Arrays.asList("view", "Cadiducho"));

        assertTrue(sender.joinedMessages().contains("No tienes permiso"));
    }

    @Test
    void verAliasIsNotAccepted() {
        TestSender sender = player("Admin", UUID);
        sender.permissions.add("40servidores.streak.view");

        commandManager.executeCommand(sender, "streak40", Arrays.asList("ver", "Cadiducho"));

        String messages = sender.joinedMessages();
        assertTrue(messages.contains("/streak40 view <jugador>"));
        assertFalse(messages.contains("UUID"));
    }

    @Test
    void consoleWithoutArgumentsReceivesViewUsage() {
        TestSender sender = console();

        commandManager.executeCommand(sender, "streak40", new ArrayList<>());

        String messages = sender.joinedMessages();
        assertTrue(messages.contains("/streak40 view <jugador>"));
        assertFalse(messages.contains("/streak40 reset <jugador>"));
    }

    @Test
    void playerWithoutCachedDataReceivesLoadingMessage() {
        TestSender sender = player("Cadiducho", UUID);

        commandManager.executeCommand(sender, "streak40", new ArrayList<>());

        String messages = sender.joinedMessages();
        assertTrue(messages.contains("se están cargando"));
        assertFalse(messages.contains(UUID));
    }

    @Test
    void unknownViewTargetKeepsLoadingBehavior() {
        TestSender sender = player("Admin", UUID);
        sender.permissions.add("40servidores.streak.view");

        commandManager.executeCommand(sender, "streak40", Arrays.asList("view", "Desconocido"));

        assertTrue(sender.joinedMessages().contains("se están cargando"));
    }

    @Test
    void resetKeepsExistingPermission() {
        TestSender sender = player("Admin", UUID);

        commandManager.executeCommand(sender, "streak40", Arrays.asList("reset", "Cadiducho"));

        assertTrue(sender.joinedMessages().contains("No tienes permiso"));
    }

    private TestSender player(String name, String uuid) {
        return new TestSender(name, uuid, false);
    }

    private TestSender console() {
        TestSender sender = new TestSender("CONSOLE", "", true);
        sender.permissions.add("40servidores.streak.view");
        return sender;
    }

    private static class TestPlugin implements CSPlugin {

        private final File dataFolder;
        private final PlayerVoteStore playerVoteStore;
        private final VoteStreakService voteStreakService;
        private final VoteReminderService voteReminderService;
        private final TestConfiguration configuration = new TestConfiguration(this);

        private TestPlugin(File dataFolder) {
            this.dataFolder = dataFolder;
            this.playerVoteStore = new PlayerVoteStore(dataFolder, this);
            this.voteStreakService = new VoteStreakService(this);
            this.voteReminderService = new VoteReminderService(this);
        }

        @Override
        public void log(String text) {
        }

        @Override
        public void logError(String text) {
        }

        @Override
        public void registerCommands() {
        }

        @Override
        public CSConfiguration getCSConfiguration() {
            return configuration;
        }

        @Override
        public ApiClient getApiClient() {
            return null;
        }

        @Override
        public RewardService getRewardService() {
            return null;
        }

        @Override
        public File getPluginDataFolder() {
            return dataFolder;
        }

        @Override
        public PlayerVoteStore getPlayerVoteStore() {
            return playerVoteStore;
        }

        @Override
        public VoteStreakService getVoteStreakService() {
            return voteStreakService;
        }

        @Override
        public VoteReminderService getVoteReminderService() {
            return voteReminderService;
        }

        @Override
        public Updater getUpdater() {
            return null;
        }

        @Override
        public PluginMetrics getPluginMetrics() {
            return null;
        }

        @Override
        public String getPluginVersion() {
            return "test";
        }

        @Override
        public void dispatchCommand(String command) {
        }

        @Override
        public void broadcastMessage(String message) {
        }
    }

    private static class TestConfiguration implements CSConfiguration {

        private final CSPlugin plugin;

        private TestConfiguration(CSPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void reload() {
        }

        @Override
        public String getString(String key, String defValue) {
            return defValue;
        }

        @Override
        public int getInt(String key, int defValue) {
            return defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return defValue;
        }

        @Override
        public List<String> getStringList(String path, List<String> def) {
            return def;
        }

        @Override
        public Map<String, String> getStringMap(String path, Map<String, String> def) {
            return def;
        }

        @Override
        public CSPlugin getPlugin() {
            return plugin;
        }
    }

    private static class TestSender implements CSCommandSender {

        private final String name;
        private final String uuid;
        private final boolean console;
        private final Set<String> permissions = new HashSet<>();
        private final List<String> messages = new ArrayList<>();

        private TestSender(String name, String uuid, boolean console) {
            this.name = name;
            this.uuid = uuid;
            this.console = console;
        }

        @Override
        public String TAG() {
            return "";
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getUniqueId() {
            return uuid;
        }

        @Override
        public boolean isConsole() {
            return console;
        }

        @Override
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }

        private String joinedMessages() {
            return String.join("\n", messages);
        }
    }
}

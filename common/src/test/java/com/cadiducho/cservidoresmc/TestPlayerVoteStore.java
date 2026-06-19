package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPlayerVoteStore {

    private static final String UUID = "0f50d3c1-2d53-47d8-9f5a-10153b5f9770";

    @TempDir
    File tempDir;

    @Test
    void lazyLoadDoesNotExposeDataUntilRequested() throws Exception {
        writePlayer("Cadiducho", UUID, "lastVoteAt: 1000\n");
        PlayerVoteStore store = store();

        assertEquals(0L, store.cachedLastVoteAt("Cadiducho", UUID));
        store.requestLoad("Cadiducho", UUID);

        assertEquals(1000L, store.cachedLastVoteAt("Cadiducho", UUID));
    }

    @Test
    void voteUpdatesSnapshotImmediatelyAfterSuccessfulWrite() {
        PlayerVoteStore store = store();

        assertTrue(store.recordVote(sender("Cadiducho", UUID), 2000L));

        assertEquals(2000L, store.cachedLastVoteAt("Cadiducho", UUID));
    }

    @Test
    void failedWriteDoesNotPublishRewardSnapshot() throws Exception {
        File blockedFolder = new File(tempDir, "blocked");
        assertTrue(blockedFolder.createNewFile());
        PlayerVoteStore store = new PlayerVoteStore(blockedFolder, new TestPlugin(), true, Runnable::run);

        PlayerVoteStore.MarkResult result = store.markRewarded(sender("Cadiducho", UUID), "2026-06-19", 3000L);

        assertEquals(PlayerVoteStore.MarkResult.FAILED, result);
        assertFalse(store.cachedRewardedOnDate("Cadiducho", UUID, "2026-06-19"));
    }

    @Test
    void corruptFileFallsBackToSafeValues() throws Exception {
        writePlayer("Cadiducho", UUID, "lastVoteAt: nope\ncurrentStreak: no\n");
        PlayerVoteStore store = store();

        store.requestLoad("Cadiducho", UUID);

        assertEquals(0L, store.cachedLastVoteAt("Cadiducho", UUID));
        assertEquals(0, store.cachedStreak("Cadiducho", UUID).getStreak());
    }

    @Test
    @Timeout(5)
    void concurrentWritesForSamePlayerPreserveFields() throws Exception {
        PlayerVoteStore store = store();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Runnable> writes = new ArrayList<>();
        writes.add(() -> {
            await(start);
            store.recordVote(sender("Cadiducho", UUID), 5000L);
        });
        writes.add(() -> {
            await(start);
            store.recordStreak("Cadiducho", UUID, LocalDate.parse("2026-06-19"));
        });

        for (Runnable write : writes) {
            executor.submit(write);
        }
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(5000L, store.cachedLastVoteAt("Cadiducho", UUID));
        assertEquals(1, store.cachedStreak("Cadiducho", UUID).getStreak());
    }

    @Test
    void playerRenameUpdatesNameIndex() {
        PlayerVoteStore store = store();

        assertTrue(store.recordVote(sender("Cadiducho", UUID), 1000L));
        assertTrue(store.recordVote(sender("NuevoNombre", UUID), 2000L));

        assertEquals(2000L, store.cachedLastVoteAt("NuevoNombre", ""));
    }

    private PlayerVoteStore store() {
        return new PlayerVoteStore(new File(tempDir, "players"), new TestPlugin(), true, Runnable::run);
    }

    private TestSender sender(String name, String uuid) {
        return new TestSender(name, uuid);
    }

    private void writePlayer(String name, String uuid, String extra) throws Exception {
        File players = new File(tempDir, "players");
        assertTrue(players.mkdirs());
        String text = "name: \"" + name + "\"\n"
                + "lastVoteAt: 0\n"
                + "lastRewardAt: 0\n"
                + "lastRewardDate: \"\"\n"
                + "currentStreak: 0\n"
                + "bestStreak: 0\n"
                + "lastReminderAt: 0\n"
                + "lastVoteDay: \"\"\n"
                + "rewardedMilestones: []\n"
                + extra;
        Files.write(new File(players, uuid + ".yml").toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static class TestSender implements CSCommandSender {

        private final String name;
        private final String uuid;

        private TestSender(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        @Override
        public String TAG() {
            return "";
        }

        @Override
        public void sendMessage(String message) {
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
        public boolean hasPermission(String permission) {
            return true;
        }
    }

    private static class TestPlugin implements CSPlugin {

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
            return null;
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
            return null;
        }

        @Override
        public Updater getUpdater() {
            return null;
        }

        @Override
        public PluginMetrics getPluginMetrics() {
            return new PluginMetrics();
        }

        @Override
        public String getPluginVersion() {
            return "test";
        }

        @Override
        public void dispatchCommand(String command) {
        }

        @Override
        public String resolvePlayerUniqueId(String player) {
            return "cadiducho".equalsIgnoreCase(player) || "nuevonombre".equalsIgnoreCase(player) ? UUID : "";
        }

        @Override
        public void broadcastMessage(String message) {
        }
    }
}

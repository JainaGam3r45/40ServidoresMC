package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.model.ServerStats;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPluginMessages {

    @Test
    void usesCustomConfiguredMessage() {
        TestConfiguration config = new TestConfiguration();
        config.strings.put("messages.commands.noPermission", "&cCustom denied");

        PluginMessages messages = new PluginMessages(config);

        assertEquals("&cCustom denied", messages.noPermission());
    }

    @Test
    void fallsBackWhenKeyMissing() {
        PluginMessages messages = new PluginMessages(new TestConfiguration());

        assertEquals("&cNo tienes permiso para usar este comando", messages.noPermission());
        assertEquals("&6No has votado hoy! Puedes hacerlo en &a ", messages.notVotedTodayPrefix());
    }

    @Test
    void invalidApiKeyIsAlwaysLocked() {
        TestConfiguration config = new TestConfiguration();
        config.strings.put("messages.invalidApiKey", "&cCustom phishing message");

        PluginMessages messages = new PluginMessages(config);

        assertEquals(PluginMessages.DEFAULT_INVALID_API_KEY, messages.invalidApiKey());
    }

    @Test
    void replacesPlaceholdersInMetricsLines() {
        TestConfiguration config = new TestConfiguration();
        config.lists.put("messages.metrics.lines", Arrays.asList(
                "Requests: %apiRequests%",
                "Failures: %apiFailures%"
        ));

        PluginMessages messages = new PluginMessages(config);
        PluginMetrics metrics = new PluginMetrics();
        metrics.incrementApiRequests();
        metrics.incrementApiRequests();
        metrics.incrementApiFailures();

        List<String> lines = messages.metricsLines(metrics);

        assertEquals(2, lines.size());
        assertEquals("Requests: 2", lines.get(0));
        assertEquals("Failures: 1", lines.get(1));
    }

    @Test
    void blankLinesArePreserved() {
        TestConfiguration config = new TestConfiguration();
        config.lists.put("messages.metrics.lines", Arrays.asList("Header", "", "Tail: %apiRequests%"));

        PluginMessages messages = new PluginMessages(config);
        List<String> lines = messages.metricsLines(new PluginMetrics());

        assertEquals(3, lines.size());
        assertEquals("", lines.get(1));
    }

    @Test
    void skipsConditionalLineWhenPlaceholderIsEmpty() {
        TestConfiguration config = new TestConfiguration();
        config.lists.put("messages.stats.lines", Arrays.asList(
                "Server %server%",
                "Last votes: %lastVotes%"
        ));

        PluginMessages messages = new PluginMessages(config);
        ServerStats stats = new ServerStats();
        stats.setServerName("Servidor");
        stats.setPosition(3);

        List<String> lines = messages.statsLines(stats, "");

        assertEquals(1, lines.size());
        assertEquals("Server Servidor", lines.get(0));
    }

    @Test
    void synthesizesMetricsLinesFromLegacyV9Keys() {
        TestConfiguration config = new TestConfiguration();
        config.strings.put("messages.metrics.header", "Custom header");
        config.strings.put("messages.metrics.apiRequests", "Custom requests: %value%");

        PluginMessages messages = new PluginMessages(config);
        PluginMetrics metrics = new PluginMetrics();
        metrics.incrementApiRequests();

        List<String> lines = messages.metricsLines(metrics);

        assertEquals("Custom header", lines.get(0));
        assertTrue(lines.get(1).contains("Custom requests: 1"));
    }

    @Test
    void reloadLinesUseVersionPlaceholder() {
        TestConfiguration config = new TestConfiguration();
        config.lists.put("messages.reload.lines", Arrays.asList("Reloaded", "Version %version%"));

        PluginMessages messages = new PluginMessages(config);

        List<String> lines = messages.reloadLines("3.2.0");

        assertEquals("Version 3.2.0", lines.get(1));
    }

    @Test
    void testLinesResolveVoteClaimPlaceholder() {
        TestConfiguration config = new TestConfiguration();
        config.strings.put("messages.voteClaim", "&aCustom reward");
        config.lists.put("messages.test.lines", Arrays.asList("Header", "%voteClaim%"));

        PluginMessages messages = new PluginMessages(config);

        List<String> lines = messages.testLines();

        assertEquals("&aCustom reward", lines.get(1));
    }

    private static class TestConfiguration implements CSConfiguration {

        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, List<String>> lists = new HashMap<>();

        @Override
        public void reload() {
        }

        @Override
        public String getString(String key, String defValue) {
            return strings.containsKey(key) ? strings.get(key) : defValue;
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
            return lists.containsKey(path) ? lists.get(path) : def;
        }

        @Override
        public Map<String, String> getStringMap(String path, Map<String, String> def) {
            return def;
        }

        @Override
        public CSPlugin getPlugin() {
            return null;
        }
    }
}

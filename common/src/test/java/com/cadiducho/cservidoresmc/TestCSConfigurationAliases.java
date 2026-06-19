package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCSConfigurationAliases {

    @Test
    void readsNewKeysBeforeLegacyKeys() {
        TestConfiguration configuration = new TestConfiguration();
        configuration.strings.put("tag", "legacy");
        configuration.strings.put("messages.prefix", "current");

        assertEquals("current", configuration.getTag());
    }

    @Test
    void fallsBackToLegacyKeys() {
        TestConfiguration configuration = new TestConfiguration();
        configuration.strings.put("tag", "legacy");
        configuration.lists.put("comandosCustom", Arrays.asList("legacy command"));

        assertEquals("legacy", configuration.getTag());
        assertEquals(Arrays.asList("legacy command"), configuration.customCommandsList());
    }

    @Test
    void readsNewRewardCommands() {
        TestConfiguration configuration = new TestConfiguration();
        configuration.lists.put("comandosCustom", Arrays.asList("legacy command"));
        configuration.lists.put("rewards.commands", Arrays.asList("current command"));

        assertEquals(Arrays.asList("current command"), configuration.customCommandsList());
    }

    private static class TestConfiguration implements CSConfiguration {

        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Integer> ints = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();
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
            return ints.containsKey(key) ? ints.get(key) : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return booleans.containsKey(key) ? booleans.get(key) : defValue;
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

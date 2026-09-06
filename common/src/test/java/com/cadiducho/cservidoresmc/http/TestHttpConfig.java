package com.cadiducho.cservidoresmc.http;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestHttpConfig {

    @Test
    void readsCurrentApiKeysBeforeLegacyKeys() {
        TestConfiguration configuration = new TestConfiguration();
        configuration.ints.put("readTimeOut", 1);
        configuration.ints.put("connectTimeOut", 2);
        configuration.ints.put("httpRetries", 3);
        configuration.ints.put("httpRetryBackoff", 4);
        configuration.ints.put("api.readTimeout", 5000);
        configuration.ints.put("api.connectTimeout", 6000);
        configuration.ints.put("api.retries", 7);
        configuration.ints.put("api.retryBackoffMillis", 800);

        HttpConfig httpConfig = HttpConfig.from(configuration);

        assertEquals(5000, httpConfig.getReadTimeout());
        assertEquals(6000, httpConfig.getConnectTimeout());
        assertEquals(HttpConfig.MAX_RETRIES, httpConfig.getRetries());
        assertEquals(800, httpConfig.getRetryBackoff());
        assertNull(httpConfig.getUserAgent());
    }

    @Test
    void fallsBackToLegacyApiKeys() {
        TestConfiguration configuration = new TestConfiguration();
        configuration.ints.put("readTimeOut", 5000);
        configuration.ints.put("connectTimeOut", 6000);
        configuration.ints.put("httpRetries", 7);
        configuration.ints.put("httpRetryBackoff", 800);

        HttpConfig httpConfig = HttpConfig.from(configuration);

        assertEquals(5000, httpConfig.getReadTimeout());
        assertEquals(6000, httpConfig.getConnectTimeout());
        assertEquals(HttpConfig.MAX_RETRIES, httpConfig.getRetries());
        assertEquals(800, httpConfig.getRetryBackoff());
    }

    @Test
    void capsRetriesFromConstructor() {
        HttpConfig httpConfig = new HttpConfig(5000, 5000, 100, 250);

        assertEquals(HttpConfig.MAX_RETRIES, httpConfig.getRetries());
    }

    @Test
    void keepsProvidedUserAgent() {
        HttpConfig httpConfig = HttpConfig.from(new TestConfiguration(), "40ServidoresMC/3.3.0/Bukkit-1.16.5/Java8-Test");

        assertEquals("40ServidoresMC/3.3.0/Bukkit-1.16.5/Java8-Test", httpConfig.getUserAgent());
    }

    private static class TestConfiguration implements CSConfiguration {

        private final Map<String, Integer> ints = new HashMap<>();

        @Override
        public void reload() {
        }

        @Override
        public String getString(String key, String defValue) {
            return defValue;
        }

        @Override
        public int getInt(String key, int defValue) {
            return ints.containsKey(key) ? ints.get(key) : defValue;
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
            return null;
        }
    }
}

package com.cadiducho.cservidoresmc.bukkit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ConfigValueResolver {

    private static final List<LegacyPath> LEGACY_PATHS = legacyPaths();

    private final YamlConfiguration userConfig;
    private final YamlConfiguration defaultConfig;
    private final Map<String, String> legacyToCurrent = new LinkedHashMap<>();

    ConfigValueResolver(YamlConfiguration userConfig, YamlConfiguration defaultConfig) {
        this.userConfig = userConfig;
        this.defaultConfig = defaultConfig;
        for (LegacyPath legacyPath : LEGACY_PATHS) {
            legacyToCurrent.put(legacyPath.legacyPath, legacyPath.currentPath);
        }
    }

    Object value(String path) {
        if ("configVersion".equals(path)) {
            return defaultConfig.get(path);
        }

        if (userConfig.isSet(path)) {
            return userConfig.get(path);
        }

        String legacyPath = legacyPath(path);
        if (legacyPath != null && userConfig.isSet(legacyPath)) {
            return userConfig.get(legacyPath);
        }

        return defaultConfig.get(path);
    }

    List<String> stringList(String path) {
        if (userConfig.isSet(path)) {
            return userConfig.getStringList(path);
        }

        String legacyPath = legacyPath(path);
        if (legacyPath != null && userConfig.isSet(legacyPath)) {
            return userConfig.getStringList(legacyPath);
        }

        return defaultConfig.getStringList(path);
    }

    List<String> extraPaths(Set<String> templatePaths) {
        Set<String> legacyPaths = new HashSet<>(legacyToCurrent.keySet());
        List<String> extras = new ArrayList<>();
        for (String path : userConfig.getKeys(true)) {
            if (templatePaths.contains(path) || legacyPaths.contains(path) || isChildOfTemplatePath(path, templatePaths)) {
                continue;
            }
            extras.add(path);
        }
        return extras;
    }

    List<String> migratedPaths(Set<String> templatePaths) {
        List<String> migrated = new ArrayList<>();
        for (LegacyPath legacyPath : LEGACY_PATHS) {
            if (!templatePaths.contains(legacyPath.currentPath)) {
                continue;
            }
            if (!userConfig.isSet(legacyPath.currentPath) && userConfig.isSet(legacyPath.legacyPath)) {
                migrated.add(legacyPath.legacyPath + " -> " + legacyPath.currentPath);
            }
        }
        return migrated;
    }

    private String legacyPath(String currentPath) {
        for (LegacyPath legacyPath : LEGACY_PATHS) {
            if (legacyPath.currentPath.equals(currentPath)) {
                return legacyPath.legacyPath;
            }
        }
        return null;
    }

    private boolean isChildOfTemplatePath(String path, Set<String> templatePaths) {
        int dot = path.lastIndexOf('.');
        while (dot > 0) {
            String parent = path.substring(0, dot);
            if (templatePaths.contains(parent) && userConfig.get(path) instanceof ConfigurationSection) {
                return true;
            }
            dot = parent.lastIndexOf('.');
        }
        return false;
    }

    private static List<LegacyPath> legacyPaths() {
        return Arrays.asList(
                new LegacyPath("configVer", "configVersion"),
                new LegacyPath("clave", "api.key"),
                new LegacyPath("readTimeOut", "api.readTimeout"),
                new LegacyPath("connectTimeOut", "api.connectTimeout"),
                new LegacyPath("httpRetries", "api.retries"),
                new LegacyPath("httpRetryBackoff", "api.retryBackoffMillis"),
                new LegacyPath("tag", "messages.prefix"),
                new LegacyPath("mensaje", "messages.voteClaim"),
                new LegacyPath("alreadyRewardedMessage", "messages.alreadyRewarded"),
                new LegacyPath("broadcast.activado", "broadcast.enabled"),
                new LegacyPath("broadcast.mensajeBroadcast", "broadcast.message"),
                new LegacyPath("comandosCustom", "rewards.commands")
        );
    }

    private static class LegacyPath {

        private final String legacyPath;
        private final String currentPath;

        private LegacyPath(String legacyPath, String currentPath) {
            this.legacyPath = legacyPath;
            this.currentPath = currentPath;
        }
    }
}

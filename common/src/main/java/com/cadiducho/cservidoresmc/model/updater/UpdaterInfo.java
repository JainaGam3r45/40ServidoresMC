package com.cadiducho.cservidoresmc.model.updater;

import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Getter
@ToString
public class UpdaterInfo {

    private HashMap<String, String> minecraftVersions;
    private HashMap<String, String> pluginVersions;

    public Optional<Map.Entry<String, String>> getPluginForMinecraft(String mcVersion) {
        if (minecraftVersions == null || pluginVersions == null) {
            return Optional.empty();
        }
        String recommendedVersion = minecraftVersions.get(mcVersion);
        if (recommendedVersion == null) {
            return Optional.empty();
        }
        return pluginVersions.entrySet().stream().filter((entry) -> entry.getKey().equals(recommendedVersion)).findAny();
    }
}


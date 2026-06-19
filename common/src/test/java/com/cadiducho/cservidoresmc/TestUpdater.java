package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.model.updater.GitHubReleaseInfo;
import com.cadiducho.cservidoresmc.model.updater.UpdaterInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestUpdater {

    @Test
    void parseUpdateRequest() {
        String file = "{\n" +
                "    \"pluginVersions\": {\n" +
                "        \"3.0\": \"Reescritura del sistema para hacerlo compatible con Spigot, Sponge y BungeeCord\"\n" +
                "    },\n" +
                "    \"minecraftVersions\": {\n" +
                "        \"1.8.8\": \"3.0\",\n" +
                "        \"1.12.2\": \"3.0\",\n" +
                "        \"1.13.2\": \"3.0\",\n" +
                "        \"1.14.4\": \"3.0\",\n" +
                "        \"1.15.2\": \"3.0\",\n" +
                "        \"1.16.2\": \"3.0\",\n" +
                "        \"1.16.4\": \"3.0\",\n" +
                "        \"1.16.5\": \"3.0\"\n" +
                "    }\n" +
                "}";
        Gson gson = new Gson();
        UpdaterInfo updaterInfo = gson.fromJson(file, UpdaterInfo.class);
        assertNotNull(updaterInfo);
        assertEquals("3.0", updaterInfo.getMinecraftVersions().get("1.16.5"));
        Optional<Map.Entry<String, String>> versionEntry = updaterInfo.getPluginForMinecraft("1.16.5");
        assertTrue(versionEntry.isPresent());

        String updaterVersion = versionEntry.get().getKey();
        String updateDescription = versionEntry.get().getValue();
        assertEquals("3.0", updaterVersion);
        assertEquals("Reescritura del sistema para hacerlo compatible con Spigot, Sponge y BungeeCord", updateDescription);
    }

    @Test
    void parseGitHubReleaseRequest() {
        String file = "{\n" +
                "    \"tag_name\": \"v3.1.0\",\n" +
                "    \"html_url\": \"https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v3.1.0\",\n" +
                "    \"name\": \"v3.1.0\",\n" +
                "    \"body\": \"## Cambios incluidos\\n\\n* Cliente HTTP robusto\"\n" +
                "}";
        Gson gson = new Gson();
        GitHubReleaseInfo releaseInfo = gson.fromJson(file, GitHubReleaseInfo.class);
        assertNotNull(releaseInfo);
        assertEquals("3.1.0", releaseInfo.getVersion());
        assertEquals("https://github.com/JainaGam3r45/40ServidoresMC/releases/tag/v3.1.0", releaseInfo.getHtmlUrl());
        assertEquals("Cambios incluidos", releaseInfo.getDescription());
    }

    @Test
    void compareSemanticVersions() {
        assertTrue(Updater.isNewerVersion("v3.1.1", "3.1.0"));
        assertTrue(Updater.isNewerVersion("3.2.0", "3.1.9"));
        assertTrue(Updater.isNewerVersion("4.0.0", "3.9.9"));
        assertFalse(Updater.isNewerVersion("v3.1.0", "3.1.0"));
        assertFalse(Updater.isNewerVersion("3.0.9", "3.1.0"));
        assertFalse(Updater.isNewerVersion("release-3.2.0", "3.1.0"));
    }
}

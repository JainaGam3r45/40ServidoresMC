package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ConfigMigrator {

    private static final String TEMPLATE_RESOURCE = "config.yml";

    private final CSPlugin plugin;
    private final File configFile;

    public ConfigMigrator(CSPlugin plugin, File configFile) {
        this.plugin = plugin;
        this.configFile = configFile;
    }

    public void migrate() {
        String template;
        try {
            template = readTemplate();
        } catch (IOException ex) {
            plugin.logError("No se pudo leer el config.yml embebido: " + ex.getMessage());
            return;
        }

        if (!configFile.exists()) {
            createInitialConfig(template);
            return;
        }

        try {
            migrateExistingConfig(template);
        } catch (IOException ex) {
            plugin.logError("No se pudo actualizar config.yml de forma segura: " + ex.getMessage());
        } catch (InvalidConfigurationException ex) {
            plugin.logError("config.yml no se pudo interpretar como YAML. No se modificó el archivo: " + ex.getMessage());
        }
    }

    private void createInitialConfig(String template) {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.logError("No se pudo crear la carpeta de configuración: " + parent.getAbsolutePath());
            return;
        }

        try {
            Files.write(configFile.toPath(), template.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.logError("No se pudo crear config.yml: " + ex.getMessage());
        }
    }

    private void migrateExistingConfig(String template) throws IOException, InvalidConfigurationException {
        String userConfigText = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        YamlConfiguration userConfig = loadYaml(userConfigText);
        YamlConfiguration defaultConfig = loadYaml(template);

        ConfigTemplate configTemplate = ConfigTemplate.parse(template);
        ConfigValueResolver valueResolver = new ConfigValueResolver(userConfig, defaultConfig);
        ConfigTemplateRenderer renderer = new ConfigTemplateRenderer(configTemplate, valueResolver);
        String renderedConfig = renderer.render();

        if (renderedConfig.equals(userConfigText)) {
            return;
        }

        createBackup();
        Files.write(configFile.toPath(), renderedConfig.getBytes(StandardCharsets.UTF_8));

        List<String> extraPaths = valueResolver.extraPaths(configTemplate.getPaths());
        if (!extraPaths.isEmpty()) {
            plugin.logError("config.yml contenía opciones personalizadas que no existen en la plantilla actual. "
                    + "Se conservaron en el backup y no se escribieron en el nuevo config.yml: "
                    + String.join(", ", extraPaths));
        }

        List<String> migratedPaths = valueResolver.migratedPaths(configTemplate.getPaths());
        if (migratedPaths.isEmpty()) {
            plugin.log("config.yml actualizado usando la plantilla embebida.");
            return;
        }

        plugin.log("config.yml actualizado usando la plantilla embebida. Claves legacy migradas: "
                + String.join(", ", migratedPaths));
    }

    private void createBackup() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        File backup = new File(configFile.getParentFile(), configFile.getName() + ".backup-" + timestamp);
        Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
    }

    private YamlConfiguration loadYaml(String text) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(text);
        return configuration;
    }

    private String readTemplate() throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(TEMPLATE_RESOURCE);
        if (stream == null) {
            throw new IOException("recurso no encontrado: " + TEMPLATE_RESOURCE);
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }
}

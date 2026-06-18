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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

public class ConfigMigrator {

    private static final String TEMPLATE_RESOURCE = "config.yml";
    private static final String FALLBACK_HEADER = "# Opciones añadidas automáticamente desde la configuración por defecto";

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
        String userConfig = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        validateYaml(template);
        validateYaml(userConfig);

        YamlDocument templateDocument = YamlDocument.parse(template);
        YamlDocument userDocument = YamlDocument.parse(userConfig);
        List<KeyBlock> missingBlocks = collectMissingBlocks(templateDocument, userDocument);
        String migrated = missingBlocks.isEmpty() ? userConfig : patchConfig(userConfig, templateDocument, userDocument, missingBlocks);
        migrated = normalizeBlankLines(migrated);
        if (migrated.equals(userConfig)) {
            return;
        }

        createBackup();
        Files.write(configFile.toPath(), migrated.getBytes(StandardCharsets.UTF_8));
        if (missingBlocks.isEmpty()) {
            plugin.log("config.yml actualizado. Se normalizaron saltos de línea excesivos.");
        } else {
            plugin.log("config.yml actualizado. Claves añadidas: " + joinPaths(missingBlocks));
        }
    }

    private List<KeyBlock> collectMissingBlocks(YamlDocument templateDocument, YamlDocument userDocument) {
        List<KeyBlock> missing = new ArrayList<>();
        for (KeyBlock block : templateDocument.blocks.values()) {
            if (userDocument.blocks.containsKey(block.path)) {
                continue;
            }

            if (hasMissingParent(block.path, userDocument)) {
                continue;
            }

            missing.add(block);
        }
        return missing;
    }

    private boolean hasMissingParent(String path, YamlDocument userDocument) {
        int dot = path.lastIndexOf('.');
        while (dot > 0) {
            String parentPath = path.substring(0, dot);
            if (!userDocument.blocks.containsKey(parentPath)) {
                return true;
            }
            dot = parentPath.lastIndexOf('.');
        }
        return false;
    }

    private String patchConfig(String userConfig, YamlDocument templateDocument, YamlDocument userDocument, List<KeyBlock> missingBlocks) {
        List<Insertion> insertions = new ArrayList<>();
        for (KeyBlock block : missingBlocks) {
            insertions.add(resolveInsertion(userDocument, templateDocument, block));
        }

        Collections.sort(insertions);
        StringBuilder builder = new StringBuilder(userConfig);
        boolean addedFallbackHeader = false;
        for (int i = insertions.size() - 1; i >= 0; i--) {
            Insertion insertion = insertions.get(i);
            String text = insertion.text;
            if (insertion.fallback && !addedFallbackHeader) {
                text = FALLBACK_HEADER + System.lineSeparator() + text;
                addedFallbackHeader = true;
            }
            builder.insert(insertion.offset, text);
        }
        return builder.toString();
    }

    private Insertion resolveInsertion(YamlDocument userDocument, YamlDocument templateDocument, KeyBlock block) {
        KeyBlock parent = userDocument.blocks.get(block.parentPath);
        if (parent != null) {
            return new Insertion(userDocument.offsetForLine(parent.blockEnd), ensureWrapped(block.text), false);
        }

        KeyBlock previous = findPreviousTemplateSibling(userDocument, templateDocument, block);
        if (previous != null) {
            return new Insertion(userDocument.offsetForLine(previous.blockEnd), ensureWrapped(block.text), false);
        }

        return new Insertion(userDocument.endOffset(), ensureWrapped(block.text), true);
    }

    private KeyBlock findPreviousTemplateSibling(YamlDocument userDocument, YamlDocument templateDocument, KeyBlock block) {
        KeyBlock previous = null;
        for (KeyBlock candidate : templateDocument.blocks.values()) {
            if (candidate == block) {
                return previous == null ? null : userDocument.blocks.get(previous.path);
            }
            if (candidate.indent == block.indent && safeEquals(candidate.parentPath, block.parentPath) && userDocument.blocks.containsKey(candidate.path)) {
                previous = candidate;
            }
        }
        return null;
    }

    private void createBackup() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        File backup = new File(configFile.getParentFile(), configFile.getName() + ".backup-" + timestamp);
        Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
    }

    private void validateYaml(String text) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(text);
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

    private String ensureWrapped(String text) {
        String lineSeparator = System.lineSeparator();
        String wrapped = text;
        if (!wrapped.startsWith(lineSeparator)) {
            wrapped = lineSeparator + wrapped;
        }
        if (!wrapped.endsWith(lineSeparator)) {
            wrapped = wrapped + lineSeparator;
        }
        return wrapped;
    }

    private String normalizeBlankLines(String text) {
        String lineSeparator = System.lineSeparator();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        boolean previousBlank = false;

        for (String line : lines) {
            boolean blank = line.trim().isEmpty();
            if (blank && previousBlank) {
                continue;
            }
            builder.append(line).append(lineSeparator);
            previousBlank = blank;
        }

        while (builder.length() >= lineSeparator.length() * 2
                && builder.substring(builder.length() - lineSeparator.length() * 2).equals(lineSeparator + lineSeparator)) {
            builder.setLength(builder.length() - lineSeparator.length());
        }
        return builder.toString();
    }

    private String joinPaths(List<KeyBlock> blocks) {
        List<String> paths = new ArrayList<>();
        for (KeyBlock block : blocks) {
            paths.add(block.path);
        }
        return String.join(", ", paths);
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static class YamlDocument {

        private final String text;
        private final List<String> lines;
        private final List<Integer> lineOffsets;
        private final LinkedHashMap<String, KeyBlock> blocks;

        private YamlDocument(String text, List<String> lines, List<Integer> lineOffsets, LinkedHashMap<String, KeyBlock> blocks) {
            this.text = text;
            this.lines = lines;
            this.lineOffsets = lineOffsets;
            this.blocks = blocks;
        }

        private static YamlDocument parse(String text) {
            List<String> lines = splitLines(text);
            List<Integer> lineOffsets = computeLineOffsets(text);
            LinkedHashMap<String, KeyBlock> blocks = new LinkedHashMap<>();
            ArrayDeque<KeyBlock> stack = new ArrayDeque<>();

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ")) {
                    continue;
                }

                int keyEnd = findKeyEnd(line);
                if (keyEnd < 0) {
                    continue;
                }

                int indent = countIndent(line);
                while (!stack.isEmpty() && stack.peek().indent >= indent) {
                    stack.pop();
                }

                String key = line.substring(indent, keyEnd).trim();
                if (key.isEmpty()) {
                    continue;
                }

                String parentPath = stack.isEmpty() ? null : stack.peek().path;
                String path = parentPath == null ? key : parentPath + "." + key;
                int blockStart = findBlockStart(lines, i);
                KeyBlock block = new KeyBlock(path, parentPath, indent, blockStart, lines.size(), "");
                blocks.put(path, block);
                stack.push(block);
            }

            applyBlockEnds(lines, blocks);
            applyBlockText(text, lineOffsets, blocks);
            return new YamlDocument(text, lines, lineOffsets, blocks);
        }

        private int offsetForLine(int line) {
            if (line >= lineOffsets.size()) {
                return text.length();
            }
            return lineOffsets.get(line);
        }

        private int endOffset() {
            return text.length();
        }

        private static void applyBlockEnds(List<String> lines, LinkedHashMap<String, KeyBlock> blocks) {
            List<KeyBlock> values = new ArrayList<>(blocks.values());
            for (int i = 0; i < values.size(); i++) {
                KeyBlock block = values.get(i);
                block.blockEnd = lines.size();
                for (int j = i + 1; j < values.size(); j++) {
                    KeyBlock next = values.get(j);
                    if (next.indent <= block.indent) {
                        block.blockEnd = next.blockStart;
                        break;
                    }
                }
            }
        }

        private static void applyBlockText(String text, List<Integer> lineOffsets, LinkedHashMap<String, KeyBlock> blocks) {
            for (KeyBlock block : blocks.values()) {
                int start = block.blockStart < lineOffsets.size() ? lineOffsets.get(block.blockStart) : text.length();
                int end = block.blockEnd < lineOffsets.size() ? lineOffsets.get(block.blockEnd) : text.length();
                block.text = text.substring(start, end);
            }
        }

        private static int findBlockStart(List<String> lines, int keyLine) {
            int start = keyLine;
            for (int i = keyLine - 1; i >= 0; i--) {
                String trimmed = lines.get(i).trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    start = i;
                    continue;
                }
                break;
            }
            return start;
        }

        private static int findKeyEnd(String line) {
            boolean quoted = false;
            char quote = 0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if ((c == '\'' || c == '"') && (i == 0 || line.charAt(i - 1) != '\\')) {
                    if (!quoted) {
                        quoted = true;
                        quote = c;
                    } else if (quote == c) {
                        quoted = false;
                    }
                }
                if (c == ':' && !quoted) {
                    return i;
                }
            }
            return -1;
        }

        private static int countIndent(String line) {
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            return indent;
        }

        private static List<String> splitLines(String text) {
            List<String> lines = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    int end = i > 0 && text.charAt(i - 1) == '\r' ? i - 1 : i;
                    lines.add(text.substring(start, end));
                    start = i + 1;
                }
            }
            if (start < text.length()) {
                lines.add(text.substring(start));
            }
            return lines;
        }

        private static List<Integer> computeLineOffsets(String text) {
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    offsets.add(i + 1);
                }
            }
            return offsets;
        }
    }

    private static class KeyBlock {

        private final String path;
        private final String parentPath;
        private final int indent;
        private final int blockStart;
        private int blockEnd;
        private String text;

        private KeyBlock(String path, String parentPath, int indent, int blockStart, int blockEnd, String text) {
            this.path = path;
            this.parentPath = parentPath;
            this.indent = indent;
            this.blockStart = blockStart;
            this.blockEnd = blockEnd;
            this.text = text;
        }
    }

    private static class Insertion implements Comparable<Insertion> {

        private final int offset;
        private final String text;
        private final boolean fallback;

        private Insertion(int offset, String text, boolean fallback) {
            this.offset = offset;
            this.text = text;
            this.fallback = fallback;
        }

        @Override
        public int compareTo(Insertion other) {
            return Integer.compare(this.offset, other.offset);
        }
    }
}

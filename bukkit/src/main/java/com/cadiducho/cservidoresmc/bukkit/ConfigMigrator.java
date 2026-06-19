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
import java.util.Map;
import java.util.TreeMap;

public class ConfigMigrator {

    private static final String TEMPLATE_RESOURCE = "config.yml";
    private static final String FALLBACK_HEADER = "# Opciones añadidas automáticamente desde la configuración por defecto";
    private static final List<LegacyPath> LEGACY_PATHS = legacyPaths();

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

        YamlConfiguration userYaml = loadYaml(userConfig);
        String effectiveTemplate = applyLegacyValues(template, userConfig, userYaml);
        YamlDocument templateDocument = YamlDocument.parse(effectiveTemplate);
        YamlDocument userDocument = YamlDocument.parse(userConfig);
        List<KeyBlock> missingBlocks = collectMissingBlocks(templateDocument, userDocument);
        String migrated = missingBlocks.isEmpty() ? userConfig : patchConfig(userConfig, templateDocument, userDocument, missingBlocks);
        migrated = removeMigratedLegacyPaths(migrated, userYaml);
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
        Map<Integer, List<Insertion>> insertions = new TreeMap<>(Collections.reverseOrder());
        for (KeyBlock block : missingBlocks) {
            Insertion insertion = resolveInsertion(userDocument, templateDocument, block);
            insertions.computeIfAbsent(insertion.offset, ignored -> new ArrayList<>()).add(insertion);
        }

        StringBuilder builder = new StringBuilder(userConfig);
        boolean addedFallbackHeader = false;
        for (Map.Entry<Integer, List<Insertion>> entry : insertions.entrySet()) {
            sortInsertions(entry.getValue());
            boolean includeFallbackHeader = false;
            for (Insertion insertion : entry.getValue()) {
                if (insertion.fallback && !addedFallbackHeader) {
                    includeFallbackHeader = true;
                    addedFallbackHeader = true;
                    break;
                }
            }
            builder.insert(entry.getKey(), renderInsertionGroup(entry.getValue(), includeFallbackHeader));
        }
        return builder.toString();
    }

    private void sortInsertions(List<Insertion> insertions) {
        Collections.sort(insertions, (left, right) -> {
            if (left.fallback != right.fallback) {
                return left.fallback ? 1 : -1;
            }
            return Integer.compare(right.indent, left.indent);
        });
    }

    private String renderInsertionGroup(List<Insertion> insertions, boolean includeFallbackHeader) {
        String lineSeparator = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        boolean addedFallbackHeader = false;

        for (Insertion insertion : insertions) {
            if (insertion.fallback && includeFallbackHeader && !addedFallbackHeader) {
                if (builder.length() == 0) {
                    builder.append(lineSeparator);
                } else if (builder.charAt(builder.length() - 1) != '\n') {
                    builder.append(lineSeparator);
                }
                builder.append(FALLBACK_HEADER).append(lineSeparator);
                addedFallbackHeader = true;
            }

            String text = cleanInsertedBlock(insertion.text);
            if (text.isEmpty()) {
                continue;
            }
            if (builder.length() == 0 && insertion.leadingBlank) {
                builder.append(lineSeparator);
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append(lineSeparator);
            }
            builder.append(text);
            if (!text.endsWith("\n")) {
                builder.append(lineSeparator);
            }
        }

        return builder.toString();
    }

    private Insertion resolveInsertion(YamlDocument userDocument, YamlDocument templateDocument, KeyBlock block) {
        KeyBlock parent = userDocument.blocks.get(block.parentPath);
        if (parent != null) {
            return new Insertion(userDocument.offsetForLine(parent.blockEnd), block.text, startsWithBlankLine(block.text), false);
        }

        KeyBlock previous = findPreviousTemplateSibling(userDocument, templateDocument, block);
        if (previous != null) {
            return new Insertion(userDocument.offsetForLine(previous.blockEnd), block.text, startsWithBlankLine(block.text), false);
        }

        return new Insertion(userDocument.endOffset(), block.text, true, true);
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
        loadYaml(text);
    }

    private YamlConfiguration loadYaml(String text) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(text);
        return configuration;
    }

    private String applyLegacyValues(String template, String userConfig, YamlConfiguration userYaml) {
        String rendered = template;
        for (LegacyPath legacyPath : LEGACY_PATHS) {
            String sourcePath = sourcePath(userYaml, legacyPath);
            if (sourcePath == null) {
                continue;
            }

            YamlDocument renderedDocument = YamlDocument.parse(rendered);
            YamlDocument userDocument = YamlDocument.parse(userConfig);
            KeyBlock targetBlock = renderedDocument.blocks.get(legacyPath.currentPath);
            KeyBlock sourceBlock = userDocument.blocks.get(sourcePath);
            if (targetBlock == null || sourceBlock == null) {
                continue;
            }

            String replacement = renderMappedBlock(targetBlock, sourceBlock, legacyPath);
            rendered = replaceBlock(rendered, renderedDocument, targetBlock, replacement);
        }
        return rendered;
    }

    private String sourcePath(YamlConfiguration userYaml, LegacyPath legacyPath) {
        if (userYaml.isSet(legacyPath.currentPath)) {
            return legacyPath.currentPath;
        }
        return userYaml.isSet(legacyPath.legacyPath) ? legacyPath.legacyPath : null;
    }

    private String renderMappedBlock(KeyBlock targetBlock, KeyBlock sourceBlock, LegacyPath legacyPath) {
        String lineSeparator = System.lineSeparator();
        String indent = repeat(' ', targetBlock.indent);
        String key = lastPathPart(legacyPath.currentPath);
        StringBuilder replacement = new StringBuilder();
        appendLeadingComments(targetBlock.text, replacement, lineSeparator);
        if (legacyPath.placeholderFormats) {
            Map<String, String> formats = placeholderFormats(sourceBlock.text);
            replacement.append(indent).append(key).append(':').append(lineSeparator);
            replacement.append(indent).append("  unavailable: ").append(formats.get("unavailable")).append(lineSeparator);
            replacement.append(indent).append("  numberZero: ").append(formats.get("numberZero")).append(lineSeparator);
            replacement.append(indent).append("  booleanTrue: \"true\"").append(lineSeparator);
            replacement.append(indent).append("  booleanFalse: ").append(formats.get("booleanFalse")).append(lineSeparator);
            replacement.append(indent).append("  dateTime: \"dd/MM/yyyy HH:mm\"").append(lineSeparator);
            return replacement.toString();
        }
        if (legacyPath.list) {
            List<String> items = listItems(sourceBlock.text);
            replacement.append(indent).append(key).append(':').append(lineSeparator);
            for (String item : items) {
                replacement.append(indent).append("  - ").append(quote(item)).append(lineSeparator);
            }
            return replacement.toString();
        }
        replacement.append(indent).append(key).append(':').append(rawScalarValue(sourceBlock.text)).append(lineSeparator);
        return replacement.toString();
    }

    private void appendLeadingComments(String blockText, StringBuilder builder, String lineSeparator) {
        String[] lines = blockText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                builder.append(line).append(lineSeparator);
                continue;
            }
            return;
        }
    }

    private String replaceBlock(String text, YamlDocument document, KeyBlock block, String replacement) {
        int start = document.offsetForLine(block.blockStart);
        int end = block.blockEnd < document.lineOffsets.size() ? document.offsetForLine(block.blockEnd) : text.length();
        return text.substring(0, start) + replacement + text.substring(end);
    }

    private String removeMigratedLegacyPaths(String text, YamlConfiguration originalYaml) {
        String migrated = text;
        for (LegacyPath legacyPath : LEGACY_PATHS) {
            YamlDocument document = YamlDocument.parse(migrated);
            if (!document.blocks.containsKey(legacyPath.currentPath)) {
                continue;
            }

            KeyBlock legacyBlock = document.blocks.get(legacyPath.legacyPath);
            if (legacyBlock != null && originalYaml.isSet(legacyPath.legacyPath)) {
                migrated = removeBlock(migrated, document, legacyBlock);
            }
        }
        return removeEmptySection(migrated, "placeholderapi.fallbacks");
    }

    private Map<String, String> placeholderFormats(String blockText) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("unavailable", "\"N/A\"");
        values.put("numberZero", "\"0\"");
        values.put("booleanFalse", "\"false\"");

        String[] lines = blockText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("notAvailable:")) {
                values.put("unavailable", trimmed.substring("notAvailable:".length()).trim());
            } else if (trimmed.startsWith("zero:")) {
                values.put("numberZero", trimmed.substring("zero:".length()).trim());
            } else if (trimmed.startsWith("false:")) {
                values.put("booleanFalse", trimmed.substring("false:".length()).trim());
            }
        }
        return values;
    }

    private String removeBlock(String text, YamlDocument document, KeyBlock block) {
        int start = document.offsetForLine(block.blockStart);
        int end = block.blockEnd < document.lineOffsets.size() ? document.offsetForLine(block.blockEnd) : text.length();
        return text.substring(0, start) + text.substring(end);
    }

    private String removeEmptySection(String text, String path) {
        YamlDocument document = YamlDocument.parse(text);
        KeyBlock block = document.blocks.get(path);
        if (block == null || hasChildBlock(document, block)) {
            return text;
        }
        return removeBlock(text, document, block);
    }

    private boolean hasChildBlock(YamlDocument document, KeyBlock block) {
        for (KeyBlock candidate : document.blocks.values()) {
            if (block.path.equals(candidate.parentPath)) {
                return true;
            }
        }
        return false;
    }

    private String rawScalarValue(String blockText) {
        String[] lines = blockText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon >= 0) {
                String value = line.substring(colon + 1);
                return value.isEmpty() ? " \"\"" : value;
            }
        }
        return " \"\"";
    }

    private List<String> listItems(String blockText) {
        List<String> items = new ArrayList<>();
        String[] lines = blockText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                items.add(unquote(trimmed.substring(2).trim()));
            }
        }
        return items;
    }

    private String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String repeat(char character, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(character);
        }
        return builder.toString();
    }

    private String lastPathPart(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    private static List<LegacyPath> legacyPaths() {
        List<LegacyPath> paths = new ArrayList<>();
        paths.add(new LegacyPath("configVer", "configVersion", false));
        paths.add(new LegacyPath("clave", "api.key", false));
        paths.add(new LegacyPath("readTimeOut", "api.readTimeout", false));
        paths.add(new LegacyPath("connectTimeOut", "api.connectTimeout", false));
        paths.add(new LegacyPath("httpRetries", "api.retries", false));
        paths.add(new LegacyPath("httpRetryBackoff", "api.retryBackoffMillis", false));
        paths.add(new LegacyPath("tag", "messages.prefix", false));
        paths.add(new LegacyPath("mensaje", "messages.voteClaim", false));
        paths.add(new LegacyPath("alreadyRewardedMessage", "messages.alreadyRewarded", false));
        paths.add(new LegacyPath("broadcast.activado", "broadcast.enabled", false));
        paths.add(new LegacyPath("broadcast.mensajeBroadcast", "broadcast.message", false));
        paths.add(new LegacyPath("comandosCustom", "rewards.commands", true));
        paths.add(new LegacyPath("placeholderapi.fallbacks", "placeholderapi.formats", false, true));
        return paths;
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

    private String cleanInsertedBlock(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        while (normalized.startsWith("\n")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("\n\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean startsWithBlankLine(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.startsWith("\n");
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

    private static class Insertion {

        private final int offset;
        private final String text;
        private final boolean leadingBlank;
        private final boolean fallback;
        private final int indent;

        private Insertion(int offset, String text, boolean leadingBlank, boolean fallback) {
            this.offset = offset;
            this.text = text;
            this.leadingBlank = leadingBlank;
            this.fallback = fallback;
            this.indent = detectIndent(text);
        }

        private int detectIndent(String text) {
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            String[] lines = normalized.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    return YamlDocument.countIndent(line);
                }
            }
            return 0;
        }
    }

    private static class LegacyPath {

        private final String legacyPath;
        private final String currentPath;
        private final boolean list;
        private final boolean placeholderFormats;

        private LegacyPath(String legacyPath, String currentPath, boolean list) {
            this(legacyPath, currentPath, list, false);
        }

        private LegacyPath(String legacyPath, String currentPath, boolean list, boolean placeholderFormats) {
            this.legacyPath = legacyPath;
            this.currentPath = currentPath;
            this.list = list;
            this.placeholderFormats = placeholderFormats;
        }
    }
}

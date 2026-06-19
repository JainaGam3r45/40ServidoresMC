package com.cadiducho.cservidoresmc.bukkit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class ConfigTemplate {

    private final List<TemplateLine> lines;
    private final Set<String> paths;

    private ConfigTemplate(List<TemplateLine> lines, Set<String> paths) {
        this.lines = lines;
        this.paths = paths;
    }

    static ConfigTemplate parse(String text) {
        List<String> rawLines = splitLines(text);
        List<TemplateLine> templateLines = new ArrayList<>();
        Set<String> paths = new LinkedHashSet<>();
        YamlPathTracker pathTracker = new YamlPathTracker();

        for (int index = 0; index < rawLines.size(); index++) {
            String line = rawLines.get(index);
            TemplateLine templateLine = TemplateLine.from(line, nextContentLine(rawLines, index + 1), pathTracker);
            templateLines.add(templateLine);

            if (templateLine.hasPath()) {
                paths.add(templateLine.getPath());
            }
        }

        return new ConfigTemplate(templateLines, paths);
    }

    List<TemplateLine> getLines() {
        return lines;
    }

    Set<String> getPaths() {
        return paths;
    }

    private static String nextContentLine(List<String> lines, int start) {
        for (int index = start; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return line;
            }
        }
        return "";
    }

    private static List<String> splitLines(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] split = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>();
        int last = split.length;
        if (last > 0 && split[last - 1].isEmpty()) {
            last--;
        }
        for (int index = 0; index < last; index++) {
            lines.add(split[index]);
        }
        return lines;
    }
}

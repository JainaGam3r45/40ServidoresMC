package com.cadiducho.cservidoresmc.bukkit;

class TemplateLine {

    private final String text;
    private final TemplateLineType type;
    private final String path;
    private final String key;
    private final int indent;

    private TemplateLine(String text, TemplateLineType type, String path, String key, int indent) {
        this.text = text;
        this.type = type;
        this.path = path;
        this.key = key;
        this.indent = indent;
    }

    static TemplateLine from(String line, String nextContentLine, YamlPathTracker pathTracker) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return new TemplateLine(line, TemplateLineType.BLANK, null, null, 0);
        }
        if (trimmed.startsWith("#") || trimmed.startsWith("- ")) {
            return new TemplateLine(line, TemplateLineType.COMMENT, null, null, 0);
        }

        int colon = findColon(line);
        if (colon < 0) {
            return new TemplateLine(line, TemplateLineType.RAW, null, null, 0);
        }

        int indent = countIndent(line);
        String key = line.substring(indent, colon).trim();
        String value = line.substring(colon + 1).trim();
        boolean list = value.isEmpty() && nextContentLine.trim().startsWith("-");
        String path = pathTracker.pathFor(indent, key);

        if (list) {
            return new TemplateLine(line, TemplateLineType.LIST, path, key, indent);
        }
        if (value.isEmpty()) {
            pathTracker.put(indent, key);
            return new TemplateLine(line, TemplateLineType.SECTION, path, key, indent);
        }

        return new TemplateLine(line, TemplateLineType.VALUE, path, key, indent);
    }

    String getText() {
        return text;
    }

    TemplateLineType getType() {
        return type;
    }

    String getPath() {
        return path;
    }

    String getKey() {
        return key;
    }

    int getIndent() {
        return indent;
    }

    boolean hasPath() {
        return path != null;
    }

    private static int findColon(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if ((character == '\'' || character == '"') && (index == 0 || line.charAt(index - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = character;
                } else if (quote == character) {
                    quoted = false;
                }
            }
            if (character == ':' && !quoted) {
                return index;
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
}

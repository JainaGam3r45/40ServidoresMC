package com.cadiducho.cservidoresmc.bukkit;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

class ConfigTemplateRenderer {

    private static final String INDENT = "  ";

    private final ConfigTemplate template;
    private final ConfigValueResolver values;

    ConfigTemplateRenderer(ConfigTemplate template, ConfigValueResolver values) {
        this.template = template;
        this.values = values;
    }

    String render() {
        StringBuilder builder = new StringBuilder();
        List<TemplateLine> lines = template.getLines();
        boolean skippingListItems = false;

        for (TemplateLine line : lines) {
            if (skippingListItems && line.getText().trim().startsWith("-")) {
                continue;
            }
            skippingListItems = false;

            if (line.getType() == TemplateLineType.VALUE) {
                appendValue(builder, line);
                continue;
            }
            if (line.getType() == TemplateLineType.LIST) {
                appendList(builder, line);
                skippingListItems = true;
                continue;
            }

            appendLine(builder, line.getText());
        }

        return normalizeBlankLines(builder.toString());
    }

    private void appendValue(StringBuilder builder, TemplateLine line) {
        Object value = values.value(line.getPath());
        if (value instanceof ConfigurationSection) {
            appendLine(builder, indentation(line.getIndent()) + line.getKey() + ":");
            return;
        }
        appendLine(builder, indentation(line.getIndent()) + line.getKey() + ": " + renderScalar(value));
    }

    private void appendList(StringBuilder builder, TemplateLine line) {
        List<String> list = values.stringList(line.getPath());
        String indent = indentation(line.getIndent());
        if (list == null || list.isEmpty()) {
            appendLine(builder, indent + line.getKey() + ": []");
            return;
        }

        appendLine(builder, indent + line.getKey() + ":");
        String itemIndent = indent + INDENT;
        for (String item : list) {
            appendLine(builder, itemIndent + "- " + quote(item));
        }
    }

    private String renderScalar(Object value) {
        if (value == null) {
            return "\"\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }

    private String quote(String value) {
        String safeValue = value == null ? "" : value;
        return "\"" + safeValue
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private String indentation(int spaces) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < spaces; index++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private void appendLine(StringBuilder builder, String line) {
        builder.append(line).append(System.lineSeparator());
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
}

package com.cadiducho.cservidoresmc.model.updater;

import java.util.ArrayList;
import java.util.List;

public class UpdateNoticeFormatter {

    private static final int SUMMARY_LIMIT = 150;
    private static final String SEPARATOR = "&6----------------------------------------";
    private static final String FALLBACK_SUMMARY = "Consulta las notas completas en GitHub";

    public List<String> updateAvailable(UpdateCheckResult result) {
        List<String> lines = new ArrayList<>();
        lines.add(SEPARATOR);
        lines.add("&8[&b40ServidoresMC&8] &eNueva actualización disponible");
        lines.add("&7Instalada: &c" + cleanVersion(result.getInstalledVersion()));
        lines.add("&7Disponible: &a" + cleanVersion(result.getAvailableVersion()));
        lines.add("&7Cambios: &f" + sanitizeSummary(result.getDescription()));
        lines.add("&7Descarga: &b" + cleanUrl(result.getDownloadUrl()));
        lines.add(SEPARATOR);
        return lines;
    }

    public List<String> upToDate(UpdateCheckResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("&8[&b40ServidoresMC&8] &a40ServidoresMC está actualizado.");
        lines.add("&7Versión instalada: &a" + cleanVersion(result.getInstalledVersion()));
        return lines;
    }

    public List<String> error(UpdateCheckResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("&8[&b40ServidoresMC&8] &cNo se pudo comprobar si hay actualizaciones.");
        if (result.getMessage() != null && !result.getMessage().trim().isEmpty()) {
            lines.add("&7Detalle: &f" + sanitizeSummary(result.getMessage()));
        }
        return lines;
    }

    public static String stripColor(String text) {
        return text == null ? "" : text.replaceAll("(?i)[&§][0-9A-FK-OR]", "");
    }

    public static String sanitizeSummary(String text) {
        if (text == null || text.trim().isEmpty()) {
            return FALLBACK_SUMMARY;
        }

        String clean = text
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("(?i)[&§][0-9A-FK-OR]", "")
                .replace("§", "")
                .replace("&", "")
                .replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "$1")
                .replaceAll("https?://\\S+", "enlace")
                .replace("`", "")
                .replace("*", "")
                .replace("_", "")
                .replace("#", "")
                .replaceAll("^\\s*[-+>]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (clean.isEmpty()) {
            return FALLBACK_SUMMARY;
        }

        if (clean.length() > SUMMARY_LIMIT) {
            return clean.substring(0, SUMMARY_LIMIT - 3).trim() + "...";
        }

        return clean;
    }

    private String cleanVersion(String version) {
        return version == null || version.trim().isEmpty() ? "desconocida" : stripColor(version.trim());
    }

    private String cleanUrl(String url) {
        return url == null || url.trim().isEmpty() ? "https://github.com/JainaGam3r45/40ServidoresMC/releases" : stripColor(url.trim());
    }
}

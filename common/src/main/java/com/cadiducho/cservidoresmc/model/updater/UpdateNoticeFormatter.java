package com.cadiducho.cservidoresmc.model.updater;

import java.util.ArrayList;
import java.util.List;

public class UpdateNoticeFormatter {

    private static final String FALLBACK_SUMMARY = "Consulta las notas completas en GitHub";
    private static final String UNKNOWN_VERSION = "desconocida";
    private static final String DEFAULT_DOWNLOAD_URL = "https://github.com/JainaGam3r45/40ServidoresMC/releases";
    private static final String SEPARATOR = "&6----------------------------------------";
    private static final String UPDATE_AVAILABLE = "&8[&b40ServidoresMC&8] &eNueva actualización disponible";
    private static final String INSTALLED = "&7Instalada: &c%installed%";
    private static final String AVAILABLE = "&7Disponible: &a%available%";
    private static final String CHANGES = "&7Cambios: &f%summary%";
    private static final String DOWNLOAD = "&7Descarga: &b%url%";
    private static final String UP_TO_DATE = "&8[&b40ServidoresMC&8] &a40ServidoresMC está actualizado.";
    private static final String INSTALLED_VERSION = "&7Versión instalada: &a%installed%";
    private static final String CHECK_FAILED = "&8[&b40ServidoresMC&8] &cNo se pudo comprobar si hay actualizaciones.";
    private static final String DETAIL = "&7Detalle: &f%detail%";

    public List<String> updateAvailable(UpdateCheckResult result) {
        List<String> lines = new ArrayList<>();
        lines.add(SEPARATOR);
        lines.add(UPDATE_AVAILABLE);
        lines.add(replace(INSTALLED, "%installed%", cleanVersion(result == null ? null : result.getInstalledVersion())));
        lines.add(replace(AVAILABLE, "%available%", cleanVersion(result == null ? null : result.getAvailableVersion())));
        lines.add(replace(CHANGES, "%summary%", sanitizeSummary(result == null ? null : result.getDescription())));
        lines.add(replace(DOWNLOAD, "%url%", cleanUrl(result == null ? null : result.getDownloadUrl())));
        lines.add(SEPARATOR);
        return lines;
    }

    public List<String> upToDate(UpdateCheckResult result) {
        List<String> lines = new ArrayList<>();
        lines.add(UP_TO_DATE);
        lines.add(replace(INSTALLED_VERSION, "%installed%", cleanVersion(result == null ? null : result.getInstalledVersion())));
        return lines;
    }

    public List<String> error(UpdateCheckResult result) {
        List<String> lines = new ArrayList<>();
        lines.add(CHECK_FAILED);
        if (result != null && result.getMessage() != null && !result.getMessage().trim().isEmpty()) {
            lines.add(replace(DETAIL, "%detail%", sanitizeSummary(result.getMessage())));
        }
        return lines;
    }

    public String sanitizeSummary(String text) {
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

        if (clean.length() > 150) {
            return clean.substring(0, 147).trim() + "...";
        }

        return clean;
    }

    public static String stripColor(String text) {
        return text == null ? "" : text.replaceAll("(?i)[&§][0-9A-FK-OR]", "");
    }

    private String cleanVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return UNKNOWN_VERSION;
        }
        return version.trim().replaceAll("(?i)[&§][0-9A-FK-OR]", "");
    }

    private String cleanUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_DOWNLOAD_URL;
        }
        return url.trim().replaceAll("(?i)[&§][0-9A-FK-OR]", "");
    }

    private static String replace(String template, String key, String value) {
        return template.replace(key, value == null ? "" : value);
    }
}

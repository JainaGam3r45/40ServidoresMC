package com.cadiducho.cservidoresmc.http;

/**
 * User-Agent for vote API and updater requests.
 * Shape: {@code 40ServidoresMC/<version>/<platform>-<mc>/Java<major>-<vendor>}
 */
public final class UserAgent {

    private UserAgent() {
    }

    public static String build(String pluginVersion, String platform, String serverVersion) {
        String resolvedPlatform = platform == null ? "Unknown" : platform;
        String resolvedServer = serverVersion == null ? "unknown" : serverVersion;
        return "40ServidoresMC/" + safeVersion(pluginVersion) + "/"
                + resolvedPlatform + "-" + resolvedServer + "/" + javaInfo();
    }

    private static String javaInfo() {
        String version = System.getProperty("java.version");
        String vendor = System.getProperty("java.vendor");
        String sanitizedVendor = vendor == null ? "Unknown" : vendor.replaceAll("\\s+", "_");
        return "Java" + majorJavaVersion(version) + "-" + sanitizedVendor;
    }

    static String majorJavaVersion(String version) {
        if (version == null || version.isEmpty()) {
            return "Unknown";
        }

        String[] parts = version.split("[._-]");
        if (parts.length == 0) {
            return "Unknown";
        }

        String first = digitsOnly(parts[0]);
        if ("1".equals(first) && parts.length > 1) {
            String second = digitsOnly(parts[1]);
            return second.isEmpty() ? "Unknown" : second;
        }

        return first.isEmpty() ? "Unknown" : first;
    }

    private static String digitsOnly(String value) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isDigit(c)) {
                break;
            }
            digits.append(c);
        }
        return digits.toString();
    }

    private static String safeVersion(String version) {
        return version == null ? "0" : version;
    }
}

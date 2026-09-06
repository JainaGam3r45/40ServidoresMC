package com.cadiducho.cservidoresmc.bukkit;

/**
 * One-shot Folia check: looks for Paper's {@code RegionizedServer} marker class.
 */
final class FoliaDetector {

    private static final String FOLIA_MARKER_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    private static final boolean FOLIA = detectFolia();

    private FoliaDetector() {
    }

    static boolean isFoliaServer() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName(FOLIA_MARKER_CLASS);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (LinkageError ignored) {
            return false;
        }
    }
}

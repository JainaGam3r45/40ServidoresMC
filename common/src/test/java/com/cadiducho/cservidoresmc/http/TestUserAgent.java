package com.cadiducho.cservidoresmc.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUserAgent {

    @Test
    void containsAllExpectedFields() {
        String userAgent = UserAgent.build("3.0", "Bukkit", "1.20.4");

        assertTrue(userAgent.startsWith("40ServidoresMC/3.0/Bukkit-1.20.4/"));
        assertTrue(userAgent.contains("Java"));
    }

    @Test
    void replacesNullPlatformAndVersionWithDefaults() {
        String userAgent = UserAgent.build("3.0", null, null);

        assertTrue(userAgent.contains("Unknown-unknown"));
    }

    @Test
    void sanitizesJavaVendorWhitespace() {
        String userAgent = UserAgent.build("3.0", "Bukkit", "1.20.4");
        int javaIndex = userAgent.indexOf("/Java");

        assertTrue(javaIndex > 0);
        assertFalse(userAgent.substring(javaIndex + 1).contains(" "));
    }

    @Test
    void keepsOnlyMajorJavaVersion() {
        assertEquals("17", UserAgent.majorJavaVersion("17.0.5"));
        assertEquals("8", UserAgent.majorJavaVersion("1.8.0_402"));
        assertEquals("21", UserAgent.majorJavaVersion("21"));
    }

    @Test
    void unknownPluginVersionDoesNotCrash() {
        String userAgent = UserAgent.build(null, "Bukkit", "1.20.4");

        assertFalse(userAgent.contains("null"));
    }

    @Test
    void differentPlatformsProduceDifferentOutput() {
        String bukkit = UserAgent.build("3.0", "Bukkit", "1.20.4");
        String sponge = UserAgent.build("3.0", "Sponge", "1.20.4");

        assertNotEquals(bukkit, sponge);
        assertTrue(bukkit.contains("Bukkit-"));
        assertTrue(sponge.contains("Sponge-"));
    }
}

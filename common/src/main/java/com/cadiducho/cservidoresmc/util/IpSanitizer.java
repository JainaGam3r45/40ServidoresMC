package com.cadiducho.cservidoresmc.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Cleans a player IP before sending it as user_ip in the v3 ack.
 * The website expects a clear IP for cross-check, not a hash.
 */
public final class IpSanitizer {

    private IpSanitizer() {
    }

    public static String sanitize(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "";
        }
        if (ip.contains("%")) {
            return "";
        }
        try {
            return InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException e) {
            return "";
        }
    }

    /**
     * Loopback/private/link-local usually means Bungee/Velocity without IP forwarding.
     * In that case the caller should send an empty user_ip.
     */
    public static boolean isLikelyBehindProxy(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static String forAck(String rawIp) {
        String cleaned = sanitize(rawIp);
        if (cleaned.isEmpty() || isLikelyBehindProxy(cleaned)) {
            return "";
        }
        return cleaned;
    }
}

package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.util.IpSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestIpSanitizer {

    @Test
    void keepsPublicIpv4() {
        assertEquals("203.0.113.10", IpSanitizer.forAck("203.0.113.10"));
    }

    @Test
    void blanksPrivateAndLoopback() {
        assertEquals("", IpSanitizer.forAck("127.0.0.1"));
        assertEquals("", IpSanitizer.forAck("10.0.0.5"));
        assertEquals("", IpSanitizer.forAck("192.168.1.20"));
        assertEquals("", IpSanitizer.forAck("::1"));
    }

    @Test
    void blanksScopedIpv6() {
        assertEquals("", IpSanitizer.sanitize("fe80::1%eth0"));
        assertTrue(IpSanitizer.isLikelyBehindProxy("169.254.1.1"));
        assertFalse(IpSanitizer.isLikelyBehindProxy("203.0.113.10"));
    }
}

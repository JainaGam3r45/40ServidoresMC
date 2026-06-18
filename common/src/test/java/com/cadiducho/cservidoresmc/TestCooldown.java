package com.cadiducho.cservidoresmc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestCooldown {

    @Test
    void activeCooldown() {
        Cooldown cooldown = new Cooldown(1);

        cooldown.setOnCooldown("player");

        assertTrue(cooldown.isCoolingDown("player"));
        assertTrue(cooldown.getTimeLeft("player") > 0);
    }

    @Test
    void expiredCooldown() {
        Cooldown cooldown = new Cooldown(0);

        cooldown.setOnCooldown("player");

        assertFalse(cooldown.isCoolingDown("player"));
        assertEquals(0, cooldown.getTimeLeft("player"));
    }

    @Test
    void cleanupRemovesExpiredEntries() throws ReflectiveOperationException {
        Cooldown cooldown = new Cooldown(0);

        cooldown.setOnCooldown("player");
        cooldown.cleanup();

        assertFalse(cooldown.isCoolingDown("player"));
        assertTrue(getCooldowns(cooldown).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> getCooldowns(Cooldown cooldown) throws ReflectiveOperationException {
        Field cooldowns = Cooldown.class.getDeclaredField("cooldowns");
        cooldowns.setAccessible(true);
        return (Map<String, Long>) cooldowns.get(cooldown);
    }
}

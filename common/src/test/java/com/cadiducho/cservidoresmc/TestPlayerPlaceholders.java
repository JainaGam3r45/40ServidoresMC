package com.cadiducho.cservidoresmc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPlayerPlaceholders {

    @Test
    void applyPlayerReplacesPlayerPlaceholder() {
        assertEquals("give Steve diamond 1", PlayerPlaceholders.applyPlayer("give %player% diamond 1", "Steve"));
    }

    @Test
    void applyPlayerStillSupportsLegacyBracePlaceholder() {
        assertEquals("money add Steve 10", PlayerPlaceholders.applyPlayer("money add {0} 10", "Steve"));
    }

    @Test
    void applyStreakCommandReplacesAllPlaceholders() {
        assertEquals(
                "give Steve 550e8400-e29b-41d4-a716-446655440000 3",
                PlayerPlaceholders.applyStreakCommand(
                        "give %player% %uuid% %streak%",
                        "Steve",
                        "550e8400-e29b-41d4-a716-446655440000",
                        3));
    }
}

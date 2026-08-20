package com.cadiducho.cservidoresmc;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestVoteCooldownRules {

    @Test
    void returnsMinusOneWithoutLastVote() {
        assertEquals(-1L, VoteCooldownRules.nextVoteInMillis(0L, 1_000L));
        assertFalse(VoteCooldownRules.canVoteAgain(0L, 1_000L));
    }

    @Test
    void lateUtcVoteWaitsTwelveHoursIntoNextDay() {
        long lastVoteAt = utcMillis(2026, 8, 20, 20, 0);
        long twelveHoursLater = lastVoteAt + TimeUnit.HOURS.toMillis(12);
        long nextDayStart = utcMillis(2026, 8, 21, 0, 0);

        assertEquals(twelveHoursLater, VoteCooldownRules.nextEligibleAtMillis(lastVoteAt));
        assertTrue(twelveHoursLater > nextDayStart);
        assertEquals(twelveHoursLater - nextDayStart,
                VoteCooldownRules.nextVoteInMillis(lastVoteAt, nextDayStart));
        assertEquals(0L, VoteCooldownRules.nextVoteInMillis(lastVoteAt, twelveHoursLater));
        assertTrue(VoteCooldownRules.canVoteAgain(lastVoteAt, twelveHoursLater));
    }

    @Test
    void earlyUtcVoteWaitsUntilNextUtcMidnight() {
        long lastVoteAt = utcMillis(2026, 8, 20, 1, 0);
        long twelveHoursLater = lastVoteAt + TimeUnit.HOURS.toMillis(12);
        long nextDayStart = utcMillis(2026, 8, 21, 0, 0);

        assertEquals(nextDayStart, VoteCooldownRules.nextEligibleAtMillis(lastVoteAt));
        assertTrue(nextDayStart > twelveHoursLater);
        assertEquals(nextDayStart - twelveHoursLater,
                VoteCooldownRules.nextVoteInMillis(lastVoteAt, twelveHoursLater));
        assertEquals(0L, VoteCooldownRules.nextVoteInMillis(lastVoteAt, nextDayStart));
    }

    @Test
    void formatsUtcDate() {
        assertEquals("2026-08-20", VoteCooldownRules.utcDateString(utcMillis(2026, 8, 20, 23, 59)));
        assertEquals("2026-08-21", VoteCooldownRules.utcDateString(utcMillis(2026, 8, 21, 0, 0)));
    }

    private static long utcMillis(int year, int month, int day, int hour, int minute) {
        return LocalDate.of(year, month, day)
                .atTime(hour, minute)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
    }
}

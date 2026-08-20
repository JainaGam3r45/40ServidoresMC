package com.cadiducho.cservidoresmc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * Official 40ServidoresMC vote window: same UTC calendar day is blocked, and at least
 * 12 hours must pass since the previous vote.
 */
public final class VoteCooldownRules {

    public static final long MIN_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(12);

    private VoteCooldownRules() {
    }

    /**
     * @return millis until the player can vote again, {@code 0} if eligible now,
     *         or {@code -1} when there is no known last vote
     */
    public static long nextVoteInMillis(long lastVoteAtMillis, long nowMillis) {
        if (lastVoteAtMillis <= 0L) {
            return -1L;
        }
        long nextEligibleAt = nextEligibleAtMillis(lastVoteAtMillis);
        return Math.max(0L, nextEligibleAt - nowMillis);
    }

    public static long nextEligibleAtMillis(long lastVoteAtMillis) {
        long twelveHoursLater = lastVoteAtMillis + MIN_INTERVAL_MILLIS;
        long nextUtcDayStart = startOfNextUtcDayAfter(lastVoteAtMillis);
        return Math.max(twelveHoursLater, nextUtcDayStart);
    }

    public static boolean canVoteAgain(long lastVoteAtMillis, long nowMillis) {
        return lastVoteAtMillis > 0L && nextVoteInMillis(lastVoteAtMillis, nowMillis) == 0L;
    }

    public static String utcDateString(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    private static long startOfNextUtcDayAfter(long lastVoteAtMillis) {
        LocalDate voteDay = Instant.ofEpochMilli(lastVoteAtMillis).atZone(ZoneOffset.UTC).toLocalDate();
        return voteDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}

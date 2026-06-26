package com.cadiducho.cservidoresmc;

import java.util.concurrent.TimeUnit;

public class VoteTimeFormatter {

    private VoteTimeFormatter() {
    }

    public static String formatDuration(long millis) {
        long safeMillis = Math.max(0L, millis);
        long days = TimeUnit.MILLISECONDS.toDays(safeMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(safeMillis) % 24L;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(safeMillis) % 60L;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(safeMillis) % 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}

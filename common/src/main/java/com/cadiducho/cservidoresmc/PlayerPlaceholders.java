package com.cadiducho.cservidoresmc;

public final class PlayerPlaceholders {

    private PlayerPlaceholders() {
    }

    public static String applyPlayer(String template, String player) {
        if (template == null) {
            return "";
        }
        String safePlayer = player == null ? "" : player;
        return template
                .replace("%player%", safePlayer)
                .replace("{0}", safePlayer);
    }

    public static String applyStreakCommand(String template, String player, String uuid, int streak) {
        return applyPlayer(template, player)
                .replace("%uuid%", uuid == null ? "" : uuid)
                .replace("%streak%", String.valueOf(streak));
    }
}

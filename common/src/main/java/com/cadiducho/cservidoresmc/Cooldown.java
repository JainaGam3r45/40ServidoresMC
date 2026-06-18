package com.cadiducho.cservidoresmc;

import java.util.concurrent.ConcurrentHashMap;

public class Cooldown {

    private final int time;
    private final ConcurrentHashMap<String, Long> cooldowns;

    public Cooldown(int time) {
        this.time = time;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    public int getTime() {
        return time;
    }

    private ConcurrentHashMap<String, Long> getCooldowns() {
        return cooldowns;
    }

    public int getTimeLeft(String player) {
        Long expiresAt = getCooldowns().get(player);
        if (expiresAt == null) {
            return 0;
        }

        long millisLeft = expiresAt - System.currentTimeMillis();
        if (millisLeft <= 0) {
            getCooldowns().remove(player, expiresAt);
            return 0;
        }

        return (int) ((millisLeft / 1000) + 1);
    }

    public void setOnCooldown(String player) {
        getCooldowns().put(player, System.currentTimeMillis() + (getTime() * 1000L));
    }

    public boolean isCoolingDown(String player) {
        Long expiresAt = getCooldowns().get(player);
        if (expiresAt == null) {
            return false;
        }

        if (expiresAt > System.currentTimeMillis()) {
            return true;
        }

        getCooldowns().remove(player, expiresAt);
        return false;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        getCooldowns().forEach((player, expiresAt) -> {
            if (expiresAt <= now) {
                getCooldowns().remove(player, expiresAt);
            }
        });
    }
}

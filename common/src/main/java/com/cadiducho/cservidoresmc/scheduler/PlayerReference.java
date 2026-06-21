package com.cadiducho.cservidoresmc.scheduler;

import com.cadiducho.cservidoresmc.api.CSCommandSender;

public final class PlayerReference {

    private final String name;
    private final String uniqueId;

    private PlayerReference(String name, String uniqueId) {
        this.name = clean(name);
        this.uniqueId = clean(uniqueId);
    }

    public static PlayerReference of(String name, String uniqueId) {
        return new PlayerReference(name, uniqueId);
    }

    public static PlayerReference from(CSCommandSender sender) {
        if (sender == null) {
            return new PlayerReference("", "");
        }
        return new PlayerReference(sender.getName(), sender.getUniqueId());
    }

    public String getName() {
        return name;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public boolean hasUniqueId() {
        return !uniqueId.isEmpty();
    }

    public boolean isEmpty() {
        return name.isEmpty() && uniqueId.isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

package com.cadiducho.cservidoresmc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateNotificationSession {

    private final Set<String> notified = ConcurrentHashMap.newKeySet();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public boolean begin(String uniqueId) {
        if (uniqueId == null || uniqueId.trim().isEmpty()) {
            return false;
        }
        if (notified.contains(uniqueId)) {
            return false;
        }
        return pending.add(uniqueId);
    }

    public void markNotified(String uniqueId) {
        if (uniqueId == null || uniqueId.trim().isEmpty()) {
            return;
        }
        pending.remove(uniqueId);
        notified.add(uniqueId);
    }

    public void clearPending(String uniqueId) {
        if (uniqueId != null) {
            pending.remove(uniqueId);
        }
    }
}

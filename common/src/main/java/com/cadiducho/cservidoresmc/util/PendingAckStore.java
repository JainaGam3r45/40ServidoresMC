package com.cadiducho.cservidoresmc.util;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory store for vote ids that were delivered but whose ack failed.
 * Retried on the next /voto40 for the same nick.
 */
public class PendingAckStore {

    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(6);

    private final ConcurrentMap<String, Entry> pending = new ConcurrentHashMap<String, Entry>();
    private final Duration maxAge;

    public PendingAckStore() {
        this(DEFAULT_MAX_AGE);
    }

    public PendingAckStore(Duration maxAge) {
        this.maxAge = maxAge == null ? DEFAULT_MAX_AGE : maxAge;
    }

    public void add(String nick, List<Long> voteIds) {
        if (nick == null || voteIds == null || voteIds.isEmpty()) {
            return;
        }
        evictOlderThan(maxAge);
        pending.compute(nick, (key, existing) -> {
            Entry entry = existing != null ? existing : new Entry();
            entry.ids.addAll(voteIds);
            return entry;
        });
    }

    public List<Long> take(String nick) {
        if (nick == null) {
            return Collections.emptyList();
        }
        evictOlderThan(maxAge);
        Entry taken = pending.remove(nick);
        if (taken == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Long>(taken.ids));
    }

    public List<Long> peek(String nick) {
        if (nick == null) {
            return Collections.emptyList();
        }
        Entry current = pending.get(nick);
        if (current == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Long>(current.ids));
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    private void evictOlderThan(Duration age) {
        Instant cutoff = Instant.now().minus(age);
        pending.entrySet().removeIf(entry -> entry.getValue().createdAt.isBefore(cutoff));
    }

    private static final class Entry {
        final List<Long> ids = new CopyOnWriteArrayList<Long>();
        final Instant createdAt = Instant.now();
    }
}

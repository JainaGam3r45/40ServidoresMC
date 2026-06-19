package com.cadiducho.cservidoresmc.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TtlCache<K, V> {

    private static final int DEFAULT_MAX_ENTRIES = 1024;

    private final ConcurrentMap<K, CacheEntry<V>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxEntries;

    public TtlCache(Clock clock) {
        this(clock, DEFAULT_MAX_ENTRIES);
    }

    public TtlCache(Clock clock, int maxEntries) {
        this.clock = clock;
        this.maxEntries = Math.max(1, maxEntries);
    }

    public V get(K key) {
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired(clock.currentTimeMillis())) {
            entries.remove(key, entry);
            return null;
        }

        return entry.value;
    }

    public void put(K key, V value, long ttlMillis) {
        if (ttlMillis <= 0 || value == null) {
            return;
        }

        long now = clock.currentTimeMillis();
        cleanupExpired(now);
        if (entries.size() >= maxEntries && !entries.containsKey(key)) {
            evictOne();
        }
        entries.put(key, new CacheEntry<>(value, now, now + ttlMillis));
    }

    public V peek(K key) {
        CacheEntry<V> entry = entries.get(key);
        return entry == null ? null : entry.value;
    }

    public long ageMillis(K key) {
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            return -1L;
        }

        return Math.max(0L, clock.currentTimeMillis() - entry.createdAtMillis);
    }

    public void invalidate(K key) {
        entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    public void cleanupExpired() {
        cleanupExpired(clock.currentTimeMillis());
    }

    public int size() {
        return entries.size();
    }

    private void cleanupExpired(long now) {
        for (K key : entries.keySet()) {
            CacheEntry<V> entry = entries.get(key);
            if (entry != null && entry.isExpired(now)) {
                entries.remove(key, entry);
            }
        }
    }

    private void evictOne() {
        for (K key : entries.keySet()) {
            entries.remove(key);
            return;
        }
    }

    private static class CacheEntry<V> {

        private final V value;
        private final long createdAtMillis;
        private final long expiresAtMillis;

        private CacheEntry(V value, long createdAtMillis, long expiresAtMillis) {
            this.value = value;
            this.createdAtMillis = createdAtMillis;
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}

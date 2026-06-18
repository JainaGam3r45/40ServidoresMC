package com.cadiducho.cservidoresmc.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TtlCache<K, V> {

    private final ConcurrentMap<K, CacheEntry<V>> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public TtlCache(Clock clock) {
        this.clock = clock;
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

        entries.put(key, new CacheEntry<>(value, clock.currentTimeMillis() + ttlMillis));
    }

    public void invalidate(K key) {
        entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    private static class CacheEntry<V> {

        private final V value;
        private final long expiresAtMillis;

        private CacheEntry(V value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}

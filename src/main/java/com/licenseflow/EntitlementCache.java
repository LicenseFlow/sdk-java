package com.licenseflow;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, TTL-based local entitlement cache for zero-network-request validation.
 *
 * <p>Strategies:</p>
 * <ul>
 *   <li>{@code cache-first} — Return cache if valid, else network.</li>
 *   <li>{@code stale-while-revalidate} — Return cache immediately, revalidate in background.</li>
 *   <li>{@code network-first} — Always hit network, cache as fallback.</li>
 * </ul>
 */
public class EntitlementCache {

    /** Represents a single cached entitlement decision. */
    public static class CachedEntry {
        private final Map<String, Object> data;
        private final Instant cachedAt;
        private final Instant expiresAt;
        private final String source; // "network", "cache", "offline"

        public CachedEntry(Map<String, Object> data, Instant cachedAt, Instant expiresAt, String source) {
            this.data = data;
            this.cachedAt = cachedAt;
            this.expiresAt = expiresAt;
            this.source = source;
        }

        public Map<String, Object> getData()   { return data; }
        public Instant getCachedAt()            { return cachedAt; }
        public Instant getExpiresAt()           { return expiresAt; }
        public String getSource()               { return source; }
    }

    private final ConcurrentHashMap<String, CachedEntry> entries = new ConcurrentHashMap<>();
    private final long ttlSeconds;
    private final long graceSeconds;
    private final String strategy;

    public EntitlementCache() {
        this(300, 72 * 3600, "stale-while-revalidate");
    }

    public EntitlementCache(long ttlSeconds, long offlineGraceSeconds, String strategy) {
        this.ttlSeconds = ttlSeconds;
        this.graceSeconds = offlineGraceSeconds;
        this.strategy = strategy;
    }

    /** Get cached entitlements. Returns null on miss or full expiry. */
    public CachedEntry get(String key) {
        CachedEntry entry = entries.get(key);
        if (entry == null) return null;

        Instant now = Instant.now();

        // Within normal TTL
        if (now.isBefore(entry.expiresAt)) {
            return new CachedEntry(entry.data, entry.cachedAt, entry.expiresAt, "cache");
        }

        // Within offline grace period
        if (now.isBefore(entry.cachedAt.plusSeconds(graceSeconds))) {
            return new CachedEntry(entry.data, entry.cachedAt, entry.expiresAt, "offline");
        }

        // Fully expired
        entries.remove(key);
        return null;
    }

    /** Store entitlement decision in cache. */
    public void set(String key, Map<String, Object> data) {
        Instant now = Instant.now();
        entries.put(key, new CachedEntry(data, now, now.plusSeconds(ttlSeconds), "network"));
    }

    /** Remove a specific cached entry. */
    public void invalidate(String key) {
        entries.remove(key);
    }

    /** Clear all cached entries. */
    public void flush() {
        entries.clear();
    }

    /**
     * Determine cache action: "use_cache", "use_cache_revalidate", or "use_network".
     */
    public String getStrategy(String key) {
        CachedEntry entry = get(key);
        if (entry == null) return "use_network";

        switch (strategy) {
            case "cache-first":
                return "offline".equals(entry.source) ? "use_cache_revalidate" : "use_cache";
            case "stale-while-revalidate":
                return "cache".equals(entry.source) ? "use_cache" : "use_cache_revalidate";
            default:
                return "use_network";
        }
    }

    /** Number of cached entries. */
    public int size() {
        return entries.size();
    }
}

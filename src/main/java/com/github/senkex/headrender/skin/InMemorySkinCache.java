package com.github.senkex.headrender.skin;

import com.github.senkex.headrender.api.SkinCache;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link SkinCache} implementation backed by a synchronized
 * {@link LinkedHashMap} with LRU eviction and a per-entry TTL.
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class InMemorySkinCache implements SkinCache {

    /**
     * Default maximum number of entries.
     */
    public static final int DEFAULT_MAX_SIZE = 256;

    /**
     * Default time-to-live in milliseconds (10 minutes).
     */
    public static final long DEFAULT_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final Map<String, CacheEntry> entries;
    private volatile long ttlMillis;
    private volatile int maxSize;

    /**
     * Creates a cache with default capacity and TTL.
     */
    public InMemorySkinCache() {
        this(DEFAULT_MAX_SIZE, DEFAULT_TTL_MILLIS);
    }

    /**
     * Creates a cache with the given capacity and TTL.
     *
     * @param maxSize the maximum number of entries, must be positive
     * @param ttlMillis the entry time-to-live in milliseconds, must be positive
     */
    public InMemorySkinCache(final int maxSize, final long ttlMillis) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("Max size must be greater than zero");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("TTL must be greater than zero");
        }
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        this.entries = Collections.synchronizedMap(new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, CacheEntry> eldest) {
                return size() > InMemorySkinCache.this.maxSize;
            }
        });
    }

    /**
     * Retrieves a cached image if present and still valid.
     *
     * @param key the cache key
     * @return the cached image, or {@code null} when missing or expired
     */
    @Override
    public BufferedImage get(final String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        final String normalized = key.toLowerCase(Locale.ROOT);

        final CacheEntry entry = entries.get(normalized);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAt) {
            entries.remove(normalized);
            return null;
        }
        return entry.image;
    }

    /**
     * Stores an image in the cache using the current TTL.
     *
     * @param key the cache key
     * @param image the image to store
     */
    @Override
    public void put(final String key, final BufferedImage image) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(image, "Image cannot be null");
        entries.put(key.toLowerCase(Locale.ROOT),
                new CacheEntry(image, System.currentTimeMillis() + ttlMillis));
    }

    /**
     * Removes a specific entry from the cache.
     *
     * @param key the cache key to remove
     */
    @Override
    public void invalidate(final String key) {
        if (key != null) {
            entries.remove(key.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Clears every entry from the cache.
     */
    @Override
    public void clear() {
        entries.clear();
    }

    /**
     * Returns the current number of entries in the cache.
     *
     * @return the cache size
     */
    @Override
    public int size() {
        return entries.size();
    }

    /**
     * Updates the entry time-to-live applied to subsequent {@code put} calls.
     *
     * @param duration the duration value
     * @param unit the time unit
     */
    public void setTtl(final long duration, final TimeUnit unit) {
        Objects.requireNonNull(unit, "TimeUnit cannot be null");
        if (duration <= 0) {
            throw new IllegalArgumentException("Duration must be greater than zero");
        }
        this.ttlMillis = unit.toMillis(duration);
    }

    /**
     * Updates the maximum number of entries allowed in the cache.
     *
     * @param size the new maximum size, must be positive
     */
    public void setMaxSize(final int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Max size must be greater than zero");
        }
        this.maxSize = size;
    }

    private static final class CacheEntry {

        private final BufferedImage image;
        private final long expireAt;

        CacheEntry(final BufferedImage image, final long expireAt) {
            this.image = image;
            this.expireAt = expireAt;
        }
    }
}

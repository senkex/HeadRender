package com.github.senkex.headrender.api;

import java.awt.image.BufferedImage;

/**
 * Contract for caching player skin images.
 *
 * <p>Implementations must be safe for concurrent access.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public interface SkinCache {

    /**
     * Retrieves a cached image if present and still valid.
     *
     * @param key the cache key
     * @return the cached image, or {@code null} when missing or expired
     */
    BufferedImage get(String key);

    /**
     * Stores an image in the cache.
     *
     * @param key the cache key
     * @param image the image to store
     */
    void put(String key, BufferedImage image);

    /**
     * Removes a specific entry from the cache.
     *
     * @param key the cache key to remove
     */
    void invalidate(String key);

    /**
     * Clears every entry from the cache.
     */
    void clear();

    /**
     * Returns the current number of entries in the cache.
     *
     * @return the cache size
     */
    int size();
}

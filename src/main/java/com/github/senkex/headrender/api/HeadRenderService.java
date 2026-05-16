package com.github.senkex.headrender.api;

import com.github.senkex.headrender.model.RenderOptions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Contract for the head rendering pipeline.
 *
 * <p>An implementation is responsible for fetching the skin, scaling
 * it down and converting every pixel into a colored chat line. All
 * operations are asynchronous and return a {@link CompletableFuture}
 * resolved with one chat line per pixel row.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public interface HeadRenderService {

    /**
     * Renders the given player using default options.
     *
     * @param target the player name or trimmed UUID
     * @return a future completed with one chat line per pixel row
     */
    default CompletableFuture<List<String>> render(final String target) {
        return render(target, RenderOptions.defaults());
    }

    /**
     * Renders the given player using custom options.
     *
     * @param target the player name or trimmed UUID
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    CompletableFuture<List<String>> render(String target, RenderOptions options);

    /**
     * Renders the given player UUID using default options.
     *
     * @param uuid the player UUID
     * @return a future completed with one chat line per pixel row
     */
    default CompletableFuture<List<String>> render(final UUID uuid) {
        return render(uuid, RenderOptions.defaults());
    }

    /**
     * Renders the given player UUID using custom options.
     *
     * @param uuid the player UUID
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    CompletableFuture<List<String>> render(UUID uuid, RenderOptions options);

    /**
     * Returns the cache used by this service.
     *
     * @return the underlying skin cache
     */
    SkinCache getCache();

    /**
     * Returns the provider used to fetch skin images.
     *
     * @return the underlying skin provider
     */
    SkinProvider getProvider();

    /**
     * Releases any resource owned by this service (thread pools, etc.).
     */
    void shutdown();
}

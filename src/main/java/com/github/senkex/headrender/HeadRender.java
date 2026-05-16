package com.github.senkex.headrender;

import com.github.senkex.headrender.api.HeadRenderService;
import com.github.senkex.headrender.api.SkinCache;
import com.github.senkex.headrender.api.SkinProvider;
import com.github.senkex.headrender.model.RenderOptions;
import com.github.senkex.headrender.service.DefaultHeadRenderService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Static facade and main entry point for the HeadRender library.
 *
 * <p>Exposes shortcut methods over a shared, lazily initialized
 * {@link HeadRenderService} instance. No setup or plugin instance
 * is required: import the library, call the static methods and the
 * service is created on first use.</p>
 *
 * <p><b>Minimum Minecraft version:</b> 1.16 (HEX colors are required).
 * Supports every version released since.</p>
 *
 * <p><b>Quick start:</b></p>
 * <pre>{@code
 * HeadRender.render("Senkex").thenAccept(lines -> {
 *     for (String line : lines) {
 *         player.sendMessage(line);
 *     }
 * });
 * }</pre>
 *
 * <p><b>Custom configuration:</b></p>
 * <pre>{@code
 * HeadRenderService service = DefaultHeadRenderService.builder()
 *     .provider(new MinotarSkinProvider(3000))
 *     .cache(new InMemorySkinCache(512, 30 * 60 * 1000L))
 *     .build();
 *
 * HeadRender.use(service);
 * }</pre>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HeadRender {

    private static volatile HeadRenderService service;

    private HeadRender() {
        throw new UnsupportedOperationException("Facade class");
    }

    /**
     * Returns the active {@link HeadRenderService} instance, lazily
     * creating a default one the first time it is requested.
     *
     * @return the active service
     */
    public static HeadRenderService service() {
        HeadRenderService current = service;
        if (current == null) {
            synchronized (HeadRender.class) {
                current = service;
                if (current == null) {
                    current = DefaultHeadRenderService.builder().build();
                    service = current;
                }
            }
        }
        return current;
    }

    /**
     * Replaces the active {@link HeadRenderService} instance.
     *
     * <p>The previously active service, if any, is shut down.</p>
     *
     * @param replacement the new service to use, must not be {@code null}
     */
    public static void use(final HeadRenderService replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("Service cannot be null");
        }
        synchronized (HeadRender.class) {
            if (service != null && service != replacement) {
                service.shutdown();
            }
            service = replacement;
        }
    }

    /**
     * Renders the given player using default options.
     *
     * @param target the player name or trimmed UUID
     * @return a future completed with one chat line per pixel row
     */
    public static CompletableFuture<List<String>> render(final String target) {
        return service().render(target);
    }

    /**
     * Renders the given player using custom options.
     *
     * @param target the player name or trimmed UUID
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    public static CompletableFuture<List<String>> render(final String target, final RenderOptions options) {
        return service().render(target, options);
    }

    /**
     * Renders the given player UUID using default options.
     *
     * @param uuid the player UUID
     * @return a future completed with one chat line per pixel row
     */
    public static CompletableFuture<List<String>> render(final UUID uuid) {
        return service().render(uuid);
    }

    /**
     * Renders the given player UUID using custom options.
     *
     * @param uuid the player UUID
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    public static CompletableFuture<List<String>> render(final UUID uuid, final RenderOptions options) {
        return service().render(uuid, options);
    }

    /**
     * Returns the cache used by the active service.
     *
     * @return the underlying skin cache
     */
    public static SkinCache cache() {
        return service().getCache();
    }

    /**
     * Returns the provider used by the active service.
     *
     * @return the underlying skin provider
     */
    public static SkinProvider provider() {
        return service().getProvider();
    }

    /**
     * Clears every entry from the active cache.
     */
    public static void clearCache() {
        service().getCache().clear();
    }

    /**
     * Returns the current number of cached skins.
     *
     * @return the cache size
     */
    public static int cacheSize() {
        return service().getCache().size();
    }

    /**
     * Shuts down the active service and releases its resources.
     */
    public static void shutdown() {
        synchronized (HeadRender.class) {
            if (service != null) {
                service.shutdown();
                service = null;
            }
        }
    }
}

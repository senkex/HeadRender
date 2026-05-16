package com.github.senkex.headrender.service;

import com.github.senkex.headrender.api.HeadRenderService;
import com.github.senkex.headrender.api.SkinCache;
import com.github.senkex.headrender.api.SkinProvider;
import com.github.senkex.headrender.cache.InMemorySkinCache;
import com.github.senkex.headrender.exception.HeadRenderException;
import com.github.senkex.headrender.image.ImageScaler;
import com.github.senkex.headrender.image.PixelRenderer;
import com.github.senkex.headrender.model.RenderOptions;
import com.github.senkex.headrender.provider.MinotarSkinProvider;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default {@link HeadRenderService} implementation.
 *
 * <p>Composes a {@link SkinProvider} and a {@link SkinCache} with an
 * internal cached thread pool to perform all network and image work
 * off the calling thread. Both dependencies are pluggable through
 * {@link #builder()}.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class DefaultHeadRenderService implements HeadRenderService {

    private final SkinProvider provider;
    private final SkinCache cache;
    private final ExecutorService executor;

    private DefaultHeadRenderService(final Builder builder) {
        this.provider = builder.provider != null ? builder.provider : new MinotarSkinProvider();
        this.cache = builder.cache != null ? builder.cache : new InMemorySkinCache();
        this.executor = builder.executor != null ? builder.executor : defaultExecutor();
    }

    /**
     * Creates a new builder for {@link DefaultHeadRenderService}.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Renders the given player using custom options.
     *
     * @param target the player name or trimmed UUID
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    @Override
    public CompletableFuture<List<String>> render(final String target, final RenderOptions options) {
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");

        return CompletableFuture.supplyAsync(() -> {
            try {
                final BufferedImage image = obtainImage(target, options);
                final BufferedImage scaled = ImageScaler.scalePixel(image, options.getSize(), options.getSize());
                return PixelRenderer.renderImage(scaled, options);
            } catch (final Exception exception) {
                throw new HeadRenderException("Failed to render head for " + target, exception);
            }
        }, executor);
    }

    /**
     * Renders the given player UUID using custom options.
     *
     * @param uuid the player UUID
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    @Override
    public CompletableFuture<List<String>> render(final UUID uuid, final RenderOptions options) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        return render(uuid.toString().replace("-", ""), options);
    }

    /**
     * Returns the cache used by this service.
     *
     * @return the underlying skin cache
     */
    @Override
    public SkinCache getCache() {
        return cache;
    }

    /**
     * Returns the provider used to fetch skin images.
     *
     * @return the underlying skin provider
     */
    @Override
    public SkinProvider getProvider() {
        return provider;
    }

    /**
     * Shuts down the internal thread pool. Any pending future is cancelled.
     */
    @Override
    public void shutdown() {
        executor.shutdownNow();
    }

    private BufferedImage obtainImage(final String target, final RenderOptions options) throws Exception {
        final String key = buildCacheKey(target, options);

        if (options.useCache()) {
            final BufferedImage cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        final BufferedImage fetched = provider.fetch(target, options.getSize(), options.useHelmetLayer());

        if (options.useCache()) {
            cache.put(key, fetched);
        }
        return fetched;
    }

    private static String buildCacheKey(final String target, final RenderOptions options) {
        return target + ':' + options.getSize() + ':' + (options.useHelmetLayer() ? 'h' : 'a');
    }

    private static ExecutorService defaultExecutor() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable runnable) {
                final Thread thread = new Thread(runnable, "HeadRender-Worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    /**
     * Builder for {@link DefaultHeadRenderService}.
     *
     * <p>Developed by <b>Senkex</b></p>
     */
    public static final class Builder {

        private SkinProvider provider;
        private SkinCache cache;
        private ExecutorService executor;

        private Builder() {
        }

        /**
         * Sets a custom skin provider.
         *
         * @param provider the provider to use, must not be {@code null}
         * @return this builder
         */
        public Builder provider(final SkinProvider provider) {
            this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
            return this;
        }

        /**
         * Sets a custom skin cache.
         *
         * @param cache the cache to use, must not be {@code null}
         * @return this builder
         */
        public Builder cache(final SkinCache cache) {
            this.cache = Objects.requireNonNull(cache, "Cache cannot be null");
            return this;
        }

        /**
         * Sets a custom executor used to perform async work.
         *
         * @param executor the executor to use, must not be {@code null}
         * @return this builder
         */
        public Builder executor(final ExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
            return this;
        }

        /**
         * Builds the configured service instance.
         *
         * @return a new {@link DefaultHeadRenderService}
         */
        public DefaultHeadRenderService build() {
            return new DefaultHeadRenderService(this);
        }
    }
}

package com.github.senkex.headrender.service;

import com.github.senkex.headrender.api.HeadRenderService;
import com.github.senkex.headrender.api.SkinCache;
import com.github.senkex.headrender.api.SkinProvider;
import com.github.senkex.headrender.cache.InMemorySkinCache;
import com.github.senkex.headrender.exception.HeadRenderException;
import com.github.senkex.headrender.image.ImageScaler;
import com.github.senkex.headrender.image.PixelRenderer;
import com.github.senkex.headrender.model.RenderOptions;
import com.github.senkex.headrender.parser.HeadTagParser;
import com.github.senkex.headrender.provider.MinotarSkinProvider;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

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
     * Parses the given text and replaces every {@code <head>NAME</head>}
     * tag with the rendered head of {@code NAME}.
     *
     * @param text the text to parse
     * @param options the render configuration applied to every head tag
     * @return a future completed with the resulting chat lines
     */
    @Override
    public CompletableFuture<List<String>> parse(final String text, final RenderOptions options, final Pattern pattern) {
        Objects.requireNonNull(text, "Text cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");
        Objects.requireNonNull(pattern, "Pattern cannot be null");

        if (!HeadTagParser.containsTag(text, pattern)) {
            final List<String> raw = new ArrayList<>();
            for (final String line : text.split("\n", -1)) {
                raw.add(line);
            }
            return CompletableFuture.completedFuture(raw);
        }

        final String[] blocks = text.split("\n", -1);
        final Set<String> targets = new LinkedHashSet<>();
        for (final String block : blocks) {
            for (final HeadTagParser.Segment segment : HeadTagParser.parse(block, pattern)) {
                if (segment.isHead()) {
                    targets.add(segment.value());
                }
            }
        }

        final Map<String, CompletableFuture<List<String>>> futures = new HashMap<>();
        for (final String target : targets) {
            futures.put(target, render(target, options));
        }

        return CompletableFuture
                .allOf(futures.values().toArray(new CompletableFuture<?>[0]))
                .thenApply(ignored -> {
                    final Map<String, List<String>> resolved = new HashMap<>(futures.size());
                    for (final Map.Entry<String, CompletableFuture<List<String>>> entry : futures.entrySet()) {
                        resolved.put(entry.getKey(), entry.getValue().join());
                    }
                    return assemble(blocks, resolved, options, pattern);
                });
    }

    private static List<String> assemble(final String[] blocks,
                                         final Map<String, List<String>> heads,
                                         final RenderOptions options,
                                         final Pattern pattern) {
        final int size = options.getSize();
        final int middle = size / 2;
        final List<String> output = new ArrayList<>();

        for (final String block : blocks) {
            final List<HeadTagParser.Segment> segments = HeadTagParser.parse(block, pattern);
            if (segments.isEmpty()) {
                output.add(block);
                continue;
            }

            boolean hasHead = false;
            for (final HeadTagParser.Segment segment : segments) {
                if (segment.isHead()) {
                    hasHead = true;
                    break;
                }
            }
            if (!hasHead) {
                output.add(block);
                continue;
            }

            final StringBuilder[] rows = new StringBuilder[size];
            for (int i = 0; i < size; i++) {
                rows[i] = new StringBuilder();
            }

            for (final HeadTagParser.Segment segment : segments) {
                if (segment.isHead()) {
                    final List<String> rendered = heads.get(segment.value());
                    final int rowCount = Math.min(size, rendered.size());
                    for (int i = 0; i < rowCount; i++) {
                        rows[i].append(rendered.get(i));
                    }
                } else {
                    final String value = segment.value();
                    final String pad = repeat(' ', value.length());
                    for (int i = 0; i < size; i++) {
                        rows[i].append(i == middle ? value : pad);
                    }
                }
            }

            for (final StringBuilder row : rows) {
                output.add(row.toString());
            }
        }
        return output;
    }

    private static String repeat(final char character, final int count) {
        if (count <= 0) {
            return "";
        }
        final char[] buffer = new char[count];
        for (int i = 0; i < count; i++) {
            buffer[i] = character;
        }
        return new String(buffer);
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

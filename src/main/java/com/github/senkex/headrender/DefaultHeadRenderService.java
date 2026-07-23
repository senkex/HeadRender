package com.github.senkex.headrender;

import com.github.senkex.headrender.api.HeadRenderService;
import com.github.senkex.headrender.api.HeadRenderer;
import com.github.senkex.headrender.api.SkinCache;
import com.github.senkex.headrender.api.SkinProvider;
import com.github.senkex.headrender.render.HexPixelRenderer;
import com.github.senkex.headrender.render.ImageScaler;
import com.github.senkex.headrender.skin.provider.FallbackSkinProvider;
import com.github.senkex.headrender.skin.HeadSource;
import com.github.senkex.headrender.skin.InMemorySkinCache;
import com.github.senkex.headrender.skin.provider.MinotarSkinProvider;
import com.github.senkex.headrender.skin.provider.MojangSkinProvider;
import com.github.senkex.headrender.skin.provider.SourceSkinProvider;
import com.github.senkex.headrender.text.HeadTagParser;

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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private final HeadRenderer renderer;
    private final ExecutorService executor;

    private DefaultHeadRenderService(final Builder builder) {
        // Every target reaching the provider is a canonical HeadSource string,
        // so the chain is wrapped once here. Already-wrapped providers are left
        // alone to keep a user-supplied configuration intact.
        final SkinProvider configured = builder.provider != null ? builder.provider : defaultProvider();
        this.provider = configured instanceof SourceSkinProvider
                ? configured
                : SourceSkinProvider.wrapping(configured);
        this.cache = builder.cache != null ? builder.cache : new InMemorySkinCache();
        this.renderer = builder.renderer != null ? builder.renderer : HexPixelRenderer.INSTANCE;
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
        return render(HeadSource.parse(target), options);
    }

    /**
     * Renders the given source using custom options.
     *
     * @param source the resolved skin origin
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    @Override
    public CompletableFuture<List<String>> render(final HeadSource source, final RenderOptions options) {
        Objects.requireNonNull(source, "Source cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");

        final String target = source.canonical();
        final RenderOptions effective = source.applyTo(options);

        return CompletableFuture.supplyAsync(() -> {
            try {
                final BufferedImage image = obtainImage(target, effective);
                final int size = effective.getSize();
                final BufferedImage scaled = ImageScaler.scalePixel(image, size, size);
                return offset(renderer.render(scaled, effective), effective.leadingSpaces(size));
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
        final Set<HeadSource> targets = new LinkedHashSet<>();
        for (final String block : blocks) {
            for (final HeadTagParser.Segment segment : HeadTagParser.parse(block, pattern)) {
                if (segment.isHead()) {
                    targets.add(segment.source());
                }
            }
        }

        final Map<HeadSource, CompletableFuture<List<String>>> futures = new HashMap<>();
        for (final HeadSource target : targets) {
            futures.put(target, render(target, options));
        }

        return CompletableFuture
                .allOf(futures.values().toArray(new CompletableFuture<?>[0]))
                .thenApply(ignored -> {
                    final Map<HeadSource, List<String>> resolved = new HashMap<>(futures.size());
                    for (final Map.Entry<HeadSource, CompletableFuture<List<String>>> entry : futures.entrySet()) {
                        resolved.put(entry.getKey(), entry.getValue().join());
                    }
                    return assemble(blocks, resolved, options, pattern);
                });
    }

    private static List<String> assemble(final String[] blocks,
                                         final Map<HeadSource, List<String>> heads,
                                         final RenderOptions options,
                                         final Pattern pattern) {
        final List<String> output = new ArrayList<>();

        for (final String block : blocks) {
            final List<HeadTagParser.Segment> segments = HeadTagParser.parse(block, pattern);
            if (segments.isEmpty()) {
                output.add(block);
                continue;
            }

            // Block height is driven by the tallest rendered head so the
            // alignment stays correct regardless of the active renderer.
            int height = 0;
            for (final HeadTagParser.Segment segment : segments) {
                if (segment.isHead()) {
                    final List<String> rendered = heads.get(segment.source());
                    if (rendered != null) {
                        height = Math.max(height, rendered.size());
                    }
                }
            }
            if (height == 0) {
                output.add(block);
                continue;
            }

            final int middle = height / 2;
            final StringBuilder[] rows = new StringBuilder[height];
            for (int i = 0; i < height; i++) {
                rows[i] = new StringBuilder();
            }

            for (final HeadTagParser.Segment segment : segments) {
                if (segment.isHead()) {
                    final List<String> rendered = heads.get(segment.source());
                    final int rowCount = Math.min(height, rendered.size());
                    for (int i = 0; i < rowCount; i++) {
                        rows[i].append(rendered.get(i));
                    }
                } else {
                    final String value = segment.value();
                    final String pad = repeat(' ', value.length());
                    for (int i = 0; i < height; i++) {
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

    /**
     * Prepends {@code spaces} blank characters to every rendered line, shifting
     * the whole head sideways while keeping its columns aligned.
     *
     * @param lines the rendered head lines
     * @param spaces the number of leading spaces to prepend
     * @return the shifted lines, or the same list when {@code spaces <= 0}
     */
    private static List<String> offset(final List<String> lines, final int spaces) {
        if (spaces <= 0 || lines.isEmpty()) {
            return lines;
        }
        final String pad = repeat(' ', spaces);
        final List<String> shifted = new ArrayList<>(lines.size());
        for (final String line : lines) {
            shifted.add(pad + line);
        }
        return shifted;
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
     * Returns the renderer strategy used by this service.
     *
     * @return the underlying head renderer
     */
    @Override
    public HeadRenderer getRenderer() {
        return renderer;
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

    private static SkinProvider defaultProvider() {
        // Mojang first (official, no proxy, online skins in offline mode),
        // Minotar as a proxy fallback if Mojang is down or rate-limits.
        return new FallbackSkinProvider(new MojangSkinProvider(), new MinotarSkinProvider());
    }

    /**
     * Upper bound on worker threads owned by the default executor.
     *
     * <p>The work is a short HTTP fetch followed by a small image resize, so
     * throughput is bound by the network, not by cores. Two threads keep a
     * render queue moving without the library ever becoming a visible line in a
     * timings report.</p>
     */
    public static final int DEFAULT_MAX_THREADS = 2;

    /**
     * Longest a worker thread stays alive with nothing to do, in seconds.
     */
    public static final long DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 30L;

    private static ExecutorService defaultExecutor() {
        final ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable runnable) {
                final Thread thread = new Thread(runnable, "HeadRender-Worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                // Never compete with the server's main loop for CPU.
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        };

        // A cached pool would spawn one thread per queued render, so a burst of
        // tags could create hundreds. This is capped, and it winds all the way
        // down to zero threads while idle: an unused HeadRender costs nothing.
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0, DEFAULT_MAX_THREADS,
                DEFAULT_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                factory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Builder for {@link DefaultHeadRenderService}.
     *
     * <p>Developed by <b>Senkex</b></p>
     */
    public static final class Builder {

        private SkinProvider provider;
        private SkinCache cache;
        private HeadRenderer renderer;
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
         * Sets the renderer strategy used to turn head images into lines.
         *
         * <p>Defaults to {@link HexPixelRenderer}.</p>
         *
         * @param renderer the renderer to use, must not be {@code null}
         * @return this builder
         */
        public Builder renderer(final HeadRenderer renderer) {
            this.renderer = Objects.requireNonNull(renderer, "Renderer cannot be null");
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

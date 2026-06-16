package com.github.senkex.headrender;

import com.github.senkex.headrender.api.HeadRenderService;
import com.github.senkex.headrender.api.HeadRenderer;
import com.github.senkex.headrender.api.SkinCache;
import com.github.senkex.headrender.api.SkinProvider;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

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
     * Parses text containing {@code <head>NAME</head>} tags and replaces
     * every tag with the rendered head of {@code NAME}.
     *
     * <p>The output is one chat line per row, ready to send to a player,
     * a hologram, a text display or any other multi-line consumer. Text
     * around the tag is placed on the vertical center row.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * HeadRender.parse("Welcome <head>Senkex</head> to the server!")
     *     .thenAccept(lines -> lines.forEach(player::sendMessage));
     * }</pre>
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parse(final String text) {
        return service().parse(text);
    }

    /**
     * Parses text containing {@code <head>NAME</head>} tags with custom options.
     *
     * @param text the text to parse
     * @param options the render configuration applied to every head tag
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parse(final String text, final RenderOptions options) {
        return service().parse(text, options);
    }

    /**
     * Parses text using a custom tag name (e.g. {@code "face"} matches
     * {@code <face>NAME</face>}).
     *
     * @param text the text to parse
     * @param options the render configuration
     * @param tagName the tag name to match (case-insensitive)
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parse(final String text, final RenderOptions options, final String tagName) {
        return service().parse(text, options, tagName);
    }

    /**
     * Parses text using a fully custom pattern. The pattern must expose
     * the head name as capture group {@code 1}.
     *
     * <p>Use this overload for placeholder syntaxes other than XML-style
     * tags, e.g. {@code {head:NAME}} or {@code %head_NAME%}.</p>
     *
     * @param text the text to parse
     * @param options the render configuration
     * @param pattern the regex pattern with the head name as group 1
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parse(final String text, final RenderOptions options, final Pattern pattern) {
        return service().parse(text, options, pattern);
    }

    /**
     * Parses text replacing PlaceholderAPI-style {@code %head-NAME%} and
     * {@code %head_NAME%} placeholders with the rendered head of {@code NAME}.
     *
     * <p>Example:</p>
     * <pre>{@code
     * HeadRender.parsePlaceholders("Bienvenido %head-Senkex% al server!")
     *     .thenAccept(lines -> lines.forEach(player::sendMessage));
     * }</pre>
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parsePlaceholders(final String text) {
        return service().parsePlaceholders(text);
    }

    /**
     * Parses text replacing {@code %head-NAME%} / {@code %head_NAME%}
     * placeholders with custom options.
     *
     * @param text the text to parse
     * @param options the render configuration applied to every placeholder
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parsePlaceholders(final String text, final RenderOptions options) {
        return service().parsePlaceholders(text, options);
    }

    /**
     * Parses text replacing namespaced {@code %headrender:NAME%} placeholders
     * with the rendered head of {@code NAME}.
     *
     * <p>Example:</p>
     * <pre>{@code
     * HeadRender.parseNamespaced("Top 1: %headrender:Senkex%")
     *     .thenAccept(lines -> lines.forEach(player::sendMessage));
     * }</pre>
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parseNamespaced(final String text) {
        return service().parseNamespaced(text);
    }

    /**
     * Parses text replacing {@code %headrender:NAME%} placeholders with
     * custom options.
     *
     * @param text the text to parse
     * @param options the render configuration applied to every placeholder
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parseNamespaced(final String text, final RenderOptions options) {
        return service().parseNamespaced(text, options);
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
     * Returns the renderer used by the active service.
     *
     * @return the underlying head renderer
     */
    public static HeadRenderer renderer() {
        return service().getRenderer();
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

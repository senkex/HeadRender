package com.github.senkex.headrender;

import com.github.senkex.headrender.api.HeadRenderService;
import com.github.senkex.headrender.api.HeadRenderer;
import com.github.senkex.headrender.api.SkinCache;
import com.github.senkex.headrender.api.SkinProvider;
import com.github.senkex.headrender.text.HeadRowMerger;

import java.util.ArrayList;
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
     * Parses text replacing Adventure-style {@code <head:VALUE>} tags with the
     * rendered head.
     *
     * <p>{@code VALUE} accepts an explicit {@code type:value} pair, a bare value
     * whose type is detected, and an optional trailing {@code :true} /
     * {@code :false} helmet override.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * HeadRender.parseTags("Steve <head:entity/player/wide/steve> vs <head:Senkex:false>")
     *     .thenAccept(lines -> lines.forEach(player::sendMessage));
     * }</pre>
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     * @see com.github.senkex.headrender.skin.HeadSource
     */
    public static CompletableFuture<List<String>> parseTags(final String text) {
        return service().parseTags(text);
    }

    /**
     * Parses text replacing {@code <head:VALUE>} tags with custom options.
     *
     * @param text the text to parse
     * @param options the render configuration applied to every tag
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parseTags(final String text, final RenderOptions options) {
        return service().parseTags(text, options);
    }

    /**
     * Parses text replacing typed {@code %head:VALUE%} placeholders with the
     * rendered head.
     *
     * <p>Example:</p>
     * <pre>{@code
     * HeadRender.parseTyped("Top 1: %head:player:Senkex%")
     *     .thenAccept(lines -> lines.forEach(player::sendMessage));
     * }</pre>
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parseTyped(final String text) {
        return service().parseTyped(text);
    }

    /**
     * Parses text replacing {@code %head:VALUE%} placeholders with custom options.
     *
     * @param text the text to parse
     * @param options the render configuration applied to every placeholder
     * @return a future completed with the resulting chat lines
     */
    public static CompletableFuture<List<String>> parseTyped(final String text, final RenderOptions options) {
        return service().parseTyped(text, options);
    }

    /**
     * Renders the given player into a template of chat lines, injecting one head
     * row per {@code <hd>} marker line.
     *
     * <p>The head is only injected when the number of {@code <hd>} marker lines
     * equals the head height (8 for a default head); otherwise the markers are
     * stripped and no head is shown. Non-marker lines pass through untouched.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * List<String> template = List.of(
     *     "<hd>",
     *     "<hd> &fName: Senkex",
     *     "<hd> &fRank: VIP",
     *     "<hd>",
     *     "<hd> &fCoins: 1200",
     *     "<hd>",
     *     "<hd> &fWins: 42",
     *     "<hd>");
     * HeadRender.renderRows("Senkex", template)
     *     .thenAccept(lines -> lines.forEach(player::sendMessage));
     * }</pre>
     *
     * @param target the player name or trimmed UUID
     * @param template the template lines with {@code <hd>} markers
     * @return a future completed with the merged chat lines
     */
    public static CompletableFuture<List<String>> renderRows(final String target, final List<String> template) {
        return renderRows(target, template, RenderOptions.defaults(), HeadRowMerger.DEFAULT_MARKER);
    }

    /**
     * Renders the given player into a template of chat lines with custom options.
     *
     * @param target the player name or trimmed UUID
     * @param template the template lines with {@code <hd>} markers
     * @param options the render configuration
     * @return a future completed with the merged chat lines
     */
    public static CompletableFuture<List<String>> renderRows(final String target, final List<String> template, final RenderOptions options) {
        return renderRows(target, template, options, HeadRowMerger.DEFAULT_MARKER);
    }

    /**
     * Renders the given player into a template of chat lines using a custom
     * marker token.
     *
     * <p>If the template contains no marker line the future completes
     * immediately with a copy of the template and no skin is fetched.</p>
     *
     * @param target the player name or trimmed UUID
     * @param template the template lines
     * @param options the render configuration
     * @param marker the marker token that receives head rows (e.g. {@code <hd>})
     * @return a future completed with the merged chat lines
     */
    public static CompletableFuture<List<String>> renderRows(final String target, final List<String> template, final RenderOptions options, final String marker) {
        if (HeadRowMerger.count(template, marker) == 0) {
            return CompletableFuture.completedFuture(new ArrayList<>(template));
        }
        return service().render(target, options).thenApply(rows -> HeadRowMerger.merge(template, rows, marker));
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
